package sagar.userService.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import sagar.userService.dtos.UserDeletedEvent;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.repository.OutboxRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDeletedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topic-user-deleted.name}")
    private String topic;

    public void publish(UserDeletedEvent event) {
        kafkaTemplate.send(topic, event.getUserId(), event);
    }

    @Scheduled(fixedDelay = 2000)
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                Object payloadObject = objectMapper.readValue(event.getPayload(), Object.class);
                kafkaTemplate.send(topic, event.getAggregateId(), payloadObject).get();

//                kafkaTemplate.send("user.deleted", event.getAggregateId(), event.getPayload()).get();
                event.setPublished(true);
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}", event.getId(), e);
            }
        }
    }
}