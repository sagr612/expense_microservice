package com.greatest.notification_service.services;

import com.greatest.notification_service.dtos.NotificationDto;
import com.greatest.notification_service.entities.Notification;
import com.greatest.notification_service.entities.NotificationType;
import com.greatest.notification_service.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @InjectMocks
    private NotificationService notificationService;

    private Notification notification;

    @BeforeEach
    void setUp() {
        notification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .title("Budget Alert")
                .body("You've used 80% of your Food budget")
                .type(NotificationType.BUDGET_ALERT)
                .isRead(false)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    class Create {

        @Test
        void createsNotification_withIsReadFalse() {
            when(repository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

            Notification result = notificationService.create(
                    "user-1", "Budget Alert", "You've used 80%", NotificationType.BUDGET_ALERT);

            assertThat(result.getUserId()).isEqualTo("user-1");
            assertThat(result.getTitle()).isEqualTo("Budget Alert");
            assertThat(result.getIsRead()).isFalse();
            assertThat(result.getType()).isEqualTo(NotificationType.BUDGET_ALERT);
        }

        @Test
        void passesBuiltEntity_toRepository() {
            when(repository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

            notificationService.create("user-1", "Title", "Body", NotificationType.EXPENSE_CREATED);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getBody()).isEqualTo("Body");
        }
    }

    @Nested
    class GetNotifications {

        @Test
        void returnsMappedDtos_orderedAsRepositoryReturnsThem() {
            when(repository.findByUserIdOrderByCreatedAtDesc("user-1"))
                    .thenReturn(List.of(notification));

            List<NotificationDto> result = notificationService.getNotifications("user-1");

            assertThat(result).hasSize(1);
            NotificationDto dto = result.get(0);
            assertThat(dto.getId()).isEqualTo("notif-1");
            assertThat(dto.getTitle()).isEqualTo("Budget Alert");
            assertThat(dto.getType()).isEqualTo("BUDGET_ALERT");
            assertThat(dto.getIsRead()).isFalse();
        }

        @Test
        void returnsEmptyList_whenUserHasNoNotifications() {
            when(repository.findByUserIdOrderByCreatedAtDesc("user-2"))
                    .thenReturn(List.of());

            List<NotificationDto> result = notificationService.getNotifications("user-2");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class UnreadCount {

        @Test
        void delegatesToRepositoryCount() {
            when(repository.countByUserIdAndIsReadFalse("user-1")).thenReturn(3L);

            Long count = notificationService.unreadCount("user-1");

            assertThat(count).isEqualTo(3L);
        }
    }

    @Nested
    class MarkRead {

        @Test
        void setsIsReadTrue_whenCallerIsOwner() {
            when(repository.findById("notif-1")).thenReturn(Optional.of(notification));

            notificationService.markRead("notif-1", "user-1");   // owner matches

            assertThat(notification.getIsRead()).isTrue();
            verify(repository).save(notification);
        }

        @Test
        void throwsAccessDenied_whenCallerIsNotOwner() {
            when(repository.findById("notif-1")).thenReturn(Optional.of(notification));

            assertThatThrownBy(() -> notificationService.markRead("notif-1", "some-other-user"))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(notification.getIsRead()).isFalse();
            verify(repository, never()).save(any());
        }

        @Test
        void throwsIllegalArgument_whenNotificationNotFound() {
            when(repository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markRead("missing", "user-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Notification not found");
        }
    }

}