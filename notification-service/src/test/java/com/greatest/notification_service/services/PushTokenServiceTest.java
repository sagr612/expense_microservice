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

import java.util.HashMap;
import java.util.Map;
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
            Map<String, Object> data = new HashMap<>();
            pushTokenService.sendToUser("user-1", "Title", "Body", data);

            verify(expoPushService).sendPushNotification("ExponentPushToken[abc]", "Title", "Body", data);
        }

        @Test
        void doesNothing_whenNoTokenRegisteredForUser() {
            when(pushTokenRepository.findByUserId("user-1")).thenReturn(Optional.empty());

            pushTokenService.sendToUser("user-1", "Title", "Body", any());

            verifyNoInteractions(expoPushService);
        }
    }


    @Nested
    class DeleteToken {
        @Test
        void deletesTokenForUser() {
            pushTokenService.deleteToken("user-1");
            verify(pushTokenRepository).deleteByUserId("user-1");
        }

        @Test
        void doesNothing_whenUserIdIsNull() {
            pushTokenService.deleteToken(null);
            verify(pushTokenRepository, never()).deleteByUserId(any());
        }
    }

    @Test
    void sendsToExpo_evenIfTokenLooksInvalid_leavesValidationToExpoService() {
        PushToken token = PushToken.builder().userId("user-1").expoToken("garbage-not-a-real-token").build();
        when(pushTokenRepository.findByUserId("user-1")).thenReturn(Optional.of(token));

        Map<String, Object> data = new HashMap<>();

        pushTokenService.sendToUser("user-1", "Title", "Body", data);

        // PushTokenService's job is just to look up + delegate; validation is ExpoPushService's problem.
        verify(expoPushService).sendPushNotification("garbage-not-a-real-token", "Title", "Body", data);
    }

}