package com.greatest.notification_service.services;

import com.greatest.notification_service.entities.PushToken;
import com.greatest.notification_service.repositories.PushTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private ExpoPushService expoPushService;

    @InjectMocks
    private PushTokenService pushTokenService;

    @Nested
    class SaveToken {

        @Test
        void updatesExistingToken_whenUserAlreadyHasOne() {
            PushToken existing = PushToken.builder()
                    .id("token-1")
                    .userId("user-1")
                    .expoToken("old-token")
                    .build();

            when(pushTokenRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));
            when(pushTokenRepository.save(any(PushToken.class))).thenAnswer(i -> i.getArgument(0));

            pushTokenService.saveToken("user-1", "new-token");

            ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
            verify(pushTokenRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo("token-1");
            assertThat(captor.getValue().getExpoToken()).isEqualTo("new-token");
        }

        @Test
        void createsNewToken_whenUserHasNoneYet() {
            when(pushTokenRepository.findByUserId("user-2")).thenReturn(Optional.empty());
            when(pushTokenRepository.save(any(PushToken.class))).thenAnswer(i -> i.getArgument(0));

            pushTokenService.saveToken("user-2", "brand-new-token");

            ArgumentCaptor<PushToken> captor = ArgumentCaptor.forClass(PushToken.class);
            verify(pushTokenRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo("user-2");
            assertThat(captor.getValue().getExpoToken()).isEqualTo("brand-new-token");
        }
    }

    @Nested
    class SendToUser {

        @Test
        void sendsPush_whenTokenExists() {
            PushToken token = PushToken.builder()
                    .userId("user-1")
                    .expoToken("ExponentPushToken[abc]")
                    .build();

            when(pushTokenRepository.findByUserId("user-1")).thenReturn(Optional.of(token));

            pushTokenService.sendToUser("user-1", "Title", "Body");

            verify(expoPushService).sendPushNotification("ExponentPushToken[abc]", "Title", "Body");
        }

        @Test
        void doesNothing_whenNoTokenRegisteredForUser() {
            when(pushTokenRepository.findByUserId("user-1")).thenReturn(Optional.empty());

            pushTokenService.sendToUser("user-1", "Title", "Body");

            verifyNoInteractions(expoPushService);
        }
    }
}