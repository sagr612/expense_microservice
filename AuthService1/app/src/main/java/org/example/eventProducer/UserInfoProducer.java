package org.example.eventProducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entities.OutboxEvent;
import org.example.repository.OutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserInfoProducer {
    private final KafkaTemplate<String, UserInfoEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;

    @Value("${spring.kafka.topic-json.name}")
    private String topic;


    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        log.info("Publishing pending events");
        List<OutboxEvent> pending = outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                UserInfoEvent payload = objectMapper.readValue(event.getPayload(), UserInfoEvent.class);
                kafkaTemplate.send(topic, event.getAggregateId(), payload).get(); //can make scheduler slow so increased thread pool
                event.setPublished(true);
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}", event.getId(), e);
            }
        }
    }

}
