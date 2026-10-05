package com.greatest.notification_service.consumers;

import com.greatest.notification_service.dtos.UserDeletedEvent;
import com.greatest.notification_service.services.UserCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDeletedConsumer {

    private final UserCleanupService userCleanupService;

    @KafkaListener(topics = "${spring.kafka.topic-user-deleted.name:user.deleted}", groupId = "${spring.kafka.consumer.group-id:notification-service-group}",

            containerFactory = "userDeletedKafkaListenerContainerFactory")
    public void consume(UserDeletedEvent event) {

        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            log.warn("Skipping invalid user deleted event: userId is null or blank");
            return;
        }
        log.info("Received user deleted event for userId={}", event.getUserId());

        userCleanupService.deleteUserData(event.getUserId());

        log.info("Cleanup completed for userId={}", event.getUserId());
    }
}