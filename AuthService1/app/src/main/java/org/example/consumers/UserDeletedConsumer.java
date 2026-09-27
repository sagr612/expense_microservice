package org.example.consumers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.model.UserDeletedEvent;
import org.example.service.UserCleanupService;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDeletedConsumer {

    private final UserCleanupService userCleanupService;

    @KafkaListener(
            topics = "${kafka.topics.user-deleted:user.deleted}",
            groupId = "authService-group",
            containerFactory = "userDeletedKafkaListenerContainerFactory"
    )
    public void consume(UserDeletedEvent event) {
        log.info("Received user deleted event for userId={}", event.getUserId());
        userCleanupService.deleteUserData(event.getUserId());
        log.info("Cleanup completed for userId={}", event.getUserId());
    }

}