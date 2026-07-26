package com.greatest.expenseService.consumer;

import com.greatest.expenseService.dto.UserDeletedEvent;
import com.greatest.expenseService.services.UserCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDeletedConsumer {

    private final UserCleanupService userCleanupService;

    @KafkaListener(
            topics = "user.deleted",
            groupId = "expenseService-group",
            containerFactory =
                    "userDeletedKafkaListenerContainerFactory"
    )
    public void consume(UserDeletedEvent event) {

        log.info(
                "Received user deleted event for userId={}",
                event.getUserId()
        );

        userCleanupService.deleteUserData(
                event.getUserId()
        );

        log.info(
                "Cleanup completed for userId={}",
                event.getUserId()
        );
    }
}