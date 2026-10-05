package com.greatest.notification_service.services;

import com.greatest.notification_service.dtos.ExpoMessage;
import com.greatest.notification_service.dtos.ExpoTicket;
import com.greatest.notification_service.dtos.ExpoTicketResponse;
import com.greatest.notification_service.repositories.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpoPushService {

    private final PushTokenRepository pushTokenRepository;
    private final WebClient expoWebClient;

    public void sendPushNotification(String expoToken, String title, String body, Map<String, Object> data) {
        if (expoToken == null || !expoToken.startsWith("ExponentPushToken[") && !expoToken.startsWith("ExpoPushToken[")) {
            log.warn("Invalid expo token: {}", mask(expoToken));
            return;
        }

        ExpoMessage message = ExpoMessage.builder()
                .to(expoToken)
                .title(title)
                .body(body)
                .sound("default")
                .data(data)
                .build();

        expoWebClient.post()
                .uri("/--/api/v2/push/send")
                .bodyValue(List.of(message))
                .retrieve()
                .bodyToMono(ExpoTicketResponse.class)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .jitter(0.5)
                        .filter(this::isRetryable))
                .doOnNext(resp -> handleTickets(resp, expoToken))
//                .doOnError(err -> log.error("Push failed for token={}: {}", mask(expoToken), err.getMessage()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        resp->log.debug("Push request completed for token={}", mask(expoToken)),

                        err -> log.error("Push failed for token={}: {}", mask(expoToken), err.getMessage())

                );

    }

    private boolean isRetryable(Throwable ex) {
        if (ex instanceof WebClientRequestException) {
            return true; // network / connect/ dns/ read timeout
        }

        if (ex instanceof WebClientResponseException w) {
            int status = w.getStatusCode().value();
            return status == 429 || status >= 500; // rate limit or expo server 5xx
        }
        return false;
    }

    private void handleTickets(ExpoTicketResponse resp, String expoToken) {
        if (resp == null || resp.getData() == null) {
            return;
        }
        for (ExpoTicket ticket : resp.getData()) {
            if (!"error".equals(ticket.getStatus())) {
                log.info("Push ticket accepted for token={}, id={}", mask(expoToken), ticket.getId());
                continue;
            }

            String code = ticket.getDetails() == null ? null : ticket.getDetails().getError();

            if ("DeviceNotRegistered".equals(code)) {
                log.warn("Deleting stale expo token (DeviceNotRegistered): {}", mask(expoToken));
                pushTokenRepository.deleteByExpoToken(expoToken);
            } else {
                log.warn("Expo ticket error for token={}, code={}, message={}", mask(expoToken), code, ticket.getMessage());
            }

        }
    }

    private String mask(String token) {
        if (token == null) {
            return "null";
        }
        int bracket = token.indexOf('[');
        return bracket < 0 ? token : token.substring(0, Math.min(bracket + 5, token.length())) + "...]";
    }
}