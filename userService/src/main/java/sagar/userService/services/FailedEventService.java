package sagar.userService.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.entities.FailedEventStatus;
import sagar.userService.entities.FailedEvents;
import sagar.userService.entities.UserInfo;
import sagar.userService.repository.FailedEventRepository;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
@Slf4j
public class FailedEventService {

    private final FailedEventRepository repository;

    @Transactional
    public void saveRaw(String rawPayload, String topic, int partition, long offset, String errorMessage, String stackTrace) {
        FailedEvents failedEvents = new FailedEvents();
        failedEvents.setTopic(topic);
        failedEvents.setPartition(partition);
        failedEvents.setOffset(offset);
        failedEvents.setEventType("USER_EVENT");
        failedEvents.setPayload(rawPayload != null ? rawPayload : "null");
        failedEvents.setErrorMessage(truncate(errorMessage, 1000));
        failedEvents.setStackTrace(stackTrace);
        failedEvents.setRetryCount(0);
        failedEvents.setStatus(FailedEventStatus.FAILED);
        failedEvents.setCreatedAt(LocalDateTime.now());
        failedEvents.setUpdatedAt(LocalDateTime.now());

        repository.save(failedEvents);
        log.info("Saved failed event from topic {} offset {} to failed_events table", topic, offset);
    }

    private String truncate(String message, int maxLength) {
        if (message == null) return null;
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }

}