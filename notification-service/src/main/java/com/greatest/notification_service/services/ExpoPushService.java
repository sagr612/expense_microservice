package com.greatest.notification_service.services;

import com.greatest.notification_service.dtos.ExpoMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpoPushService {

    // WebClient is non-blocking — better than RestTemplate for outbound calls
    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://exp.host")
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();

    public void sendPushNotification(String expoToken, String title, String body) {
        if (expoToken == null || !expoToken.startsWith("ExponentPushToken")) {
            log.warn("Invalid expo token: {}", expoToken);
            return;
        }

        ExpoMessage message = ExpoMessage.builder()
                .to(expoToken)
                .title(title)
                .body(body)
                .sound("default")
                .build();


        webClient.post()
                .uri("/--/api/v2/push/send")
                .bodyValue(message)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(ex -> ex instanceof WebClientRequestException))
                .doOnSuccess(resp ->
                        log.info("Push sent to token={} response={}", expoToken, resp))
                .subscribe(
                        resp -> { /* already logged in doOnSuccess */ },
                        err -> log.error("Push failed for token={}: {}", expoToken, err.getMessage())
                );

    }
}