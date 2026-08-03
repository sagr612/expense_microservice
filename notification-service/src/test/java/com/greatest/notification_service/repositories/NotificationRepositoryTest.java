package com.greatest.notification_service.repositories;

import com.greatest.notification_service.entities.Notification;
import com.greatest.notification_service.entities.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NotificationRepository notificationRepository;

    private Notification build(String userId, boolean isRead) {
        return Notification.builder()
                .userId(userId)
                .title("Title")
                .body("Body")
                .type(NotificationType.BUDGET_ALERT)
                .isRead(isRead)
                .build();
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsOnlyMatchingUser_newestFirst() {
        Notification older = build("user-1", false);
        Notification other = build("user-2", false);

        entityManager.persist(other);
        entityManager.persist(older);
        entityManager.flush();

        Notification newer = build("user-1", false);
        entityManager.persist(newer);
        entityManager.flush();

        List<Notification> result = notificationRepository.findByUserIdOrderByCreatedAtDesc("user-1");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> n.getUserId().equals("user-1"));
        assertThat(result.get(0).getId()).isEqualTo(newer.getId());
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsEmpty_whenUserHasNone() {
        List<Notification> result = notificationRepository.findByUserIdOrderByCreatedAtDesc("nobody");
        assertThat(result).isEmpty();
    }

    @Test
    void countByUserIdAndIsReadFalse_countsOnlyUnread() {
        entityManager.persist(build("user-1", false));
        entityManager.persist(build("user-1", false));
        entityManager.persist(build("user-1", true));
        entityManager.flush();

        Long count = notificationRepository.countByUserIdAndIsReadFalse("user-1");

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void countByUserIdAndIsReadFalse_returnsZero_whenAllRead() {
        entityManager.persist(build("user-1", true));
        entityManager.flush();

        Long count = notificationRepository.countByUserIdAndIsReadFalse("user-1");

        assertThat(count).isZero();
    }

    @Test
    void deleteByUserId_removesOnlyThatUsersNotifications() {
        entityManager.persist(build("user-1", false));
        entityManager.persist(build("user-1", true));
        entityManager.persist(build("user-2", false));
        entityManager.flush();

        notificationRepository.deleteByUserId("user-1");
        entityManager.flush();

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc("user-1")).isEmpty();
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc("user-2")).hasSize(1);
    }

    @Test
    void deleteByUserId_doesNothing_whenUserHasNoNotifications() {
        entityManager.persist(build("user-1", false));
        entityManager.flush();

        notificationRepository.deleteByUserId("unknown-user");
        entityManager.flush();

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc("user-1")).hasSize(1);
    }
}