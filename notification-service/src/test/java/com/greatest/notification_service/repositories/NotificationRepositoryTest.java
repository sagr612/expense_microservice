package com.greatest.notification_service.repositories;

import com.greatest.notification_service.entities.Notification;
import com.greatest.notification_service.entities.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
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
    void findByUserIdOrderByCreatedAtDesc_returnsOnlyMatchingUser_newestFirst() throws InterruptedException {
        Notification older = build("user-1", false);
        Notification other = build("user-2", false);

        entityManager.persist(other);
        entityManager.persist(older);
        entityManager.flush();

        Thread.sleep(25);

        Notification newer = build("user-1", false);
        entityManager.persist(newer);
        entityManager.flush();

        Page<Notification> result = notificationRepository.findByUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> n.getUserId().equals("user-1"));
        assertThat(result.getContent().get(0).getId()).isEqualTo(newer.getId());
    }

    @Test
    void findByUserIdOrderByCreatedAtDesc_returnsEmpty_whenUserHasNone() {
        Page<Notification> result = notificationRepository.findByUserIdOrderByCreatedAtDesc("nobody", PageRequest.of(0, 10));
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

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(0, 10))).isEmpty();
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc("user-2", PageRequest.of(0, 10))).hasSize(1);
    }

    @Test
    void deleteByUserId_doesNothing_whenUserHasNoNotifications() {
        entityManager.persist(build("user-1", false));
        entityManager.flush();

        notificationRepository.deleteByUserId("unknown-user");
        entityManager.flush();

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(0, 10))).hasSize(1);
    }
}