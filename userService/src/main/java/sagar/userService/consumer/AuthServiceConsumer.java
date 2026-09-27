package sagar.userService.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sagar.userService.dtos.UserInfoDto;
import sagar.userService.entities.ProcessedEvent;
import sagar.userService.repository.ProcessedEventRepository;
import sagar.userService.services.UserService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceConsumer {

    private final UserService userService;
    private final ProcessedEventRepository processedEventRepository;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroup;

    @KafkaListener(topics = "${spring.kafka.topic-json.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(UserInfoDto eventData) {
        String eventId = eventData.getEventId();

        if (eventId == null || eventId.isBlank()) {
            log.warn("Event without eventId — falling back to at-least-once semantics; userId={}", eventData.getUserId());
            userService.upsertFromEvent(eventData);
            return;
        }

        if (processedEventRepository.existsById(eventId)) {
            log.debug("Skipping already-processed event {}", eventId);
            return;
        }
        userService.upsertFromEvent(eventData);

        processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).consumerGroup(consumerGroup).build());
        log.info("Processed event {} for userId {}", eventId, eventData.getUserId());

    }
}
