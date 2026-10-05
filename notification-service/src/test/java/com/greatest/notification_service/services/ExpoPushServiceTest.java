package com.greatest.notification_service.services;

import com.greatest.notification_service.repositories.PushTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExpoPushServiceTest {
    @Mock
    private PushTokenRepository pushTokenRepository;
    private ExpoPushService expoPushService;

    private void setupWebClientWithResponse(String jsonResponse) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body(jsonResponse)
                                .build()
                ))
                .build();
        expoPushService = new ExpoPushService(pushTokenRepository, webClient);
    }

    @Nested
    class TokenValidation {
        @BeforeEach
        void setup() {
            WebClient webClient = WebClient.builder()
                    .exchangeFunction(request -> Mono.empty())
                    .build();
            expoPushService = new ExpoPushService(pushTokenRepository, webClient);
        }

        @Test
        void skip_whenTokenIsNull() {
            expoPushService.sendPushNotification(null, "title", "body", Map.of());
            verifyNoInteractions(pushTokenRepository);
        }

        @Test
        void skips_whenTokenHasInvalidPrefix() {
            expoPushService.sendPushNotification("InvalidToken[12345]", "Title", "Body", Map.of());
            verifyNoInteractions(pushTokenRepository);
        }
    }

    @Nested
    class TicketHandling {
        @Test
        void deletesToken_whenDeviceNotRegistered() {
            String expoToken = "ExponentPushToken[device-unregistered-123]";
            String responseJson = """
                    {
                        "data": [
                            {
                                "status": "error",
                                "message": "The recipient device is not registered with FCM.",
                                "details": {
                                    "error": "DeviceNotRegistered"
                                }
                            }
                        ]
                    }
                    """;

            setupWebClientWithResponse(responseJson);
            expoPushService.sendPushNotification(expoToken, "Title", "Body", Map.of("key", "value"));

            await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> verify(pushTokenRepository).deleteByExpoToken(expoToken));
        }


        @Test
        void doesNotDeleteToken_whenOtherError() {
            String expoToken = "ExpoPushToken[message-too-big-123]";
            String responseJson = """
                    {
                        "data": [
                            {
                                "status": "error",
                                "message": "Message exceeds payload limit",
                                "details": {
                                    "error": "MessageTooBig"
                                }
                            }
                        ]
                    }
                    """;
            setupWebClientWithResponse(responseJson);
            expoPushService.sendPushNotification(expoToken, "Title", "Body", Map.of());
            verify(pushTokenRepository, never()).deleteByExpoToken(anyString());
        }

        @Test
        void doesNotDeleteToken_whenTicketStatusIsOk() {
            String expoToken = "ExpoPushToken[healthy-device-123]";
            String responseJson = """
                    {
                        "data": [
                            {
                                "status": "ok",
                                "id": "ticket-id-123"
                            }
                        ]
                    }
                    """;
            setupWebClientWithResponse(responseJson);
            expoPushService.sendPushNotification(expoToken, "Title", "Body", Map.of());
            verify(pushTokenRepository, never()).deleteByExpoToken(anyString());
        }
    }

}
