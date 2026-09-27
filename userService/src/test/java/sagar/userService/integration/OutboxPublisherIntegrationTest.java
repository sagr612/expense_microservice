package sagar.userService.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.producer.OutboxClaimService;
import sagar.userService.producer.OutboxEventPublisher;
import sagar.userService.repository.OutboxRepository;
import sagar.userService.services.CloudinaryService;
import sagar.userService.services.OutboxAdminService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"user.deleted"})
@ActiveProfiles("integration")
public class OutboxPublisherIntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private OutboxClaimService outboxClaimService;
    @Autowired
    private OutboxEventPublisher outboxEventPublisher;
    @Autowired
    private OutboxAdminService outboxAdminService;
    @MockitoSpyBean
    private KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoBean
    private RedissonClient redissonClient;
    @MockitoBean
    private CloudinaryService cloudinaryService;
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        reset(kafkaTemplate);
    }

    private OutboxEvent createPendingEvent(String aggregateId) {
        return outboxRepository.save(OutboxEvent.builder().aggregateId(aggregateId).eventType("USER_DELETED").payload("{\"userId\":\"" + aggregateId + "\"}").status(OutboxEvent.OutboxStatus.PENDING).nextRetryAt(Instant.now().minusSeconds(5)).published(false).attemptCount(0).build());
    }

    @Test
    void publishSuccess_transitionsToPublishedState() {
        OutboxEvent event = createPendingEvent("user-success-1");

        List<OutboxEvent> claimed = outboxClaimService.claimEvents(Instant.now(), Duration.ofMinutes(1), 10);
        assertThat(claimed).hasSize(1);

        assertThat(claimed.get(0).getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PROCESSING);

        outboxEventPublisher.processOne(claimed.get(0));

        OutboxEvent updated = outboxRepository.findById(event.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PUBLISHED);
        assertThat(updated.isPublished()).isTrue();
        assertThat(updated.getLastError()).isNull();
        assertThat(updated.getLockedUntil()).isNull();
    }

    @Test
    void publishFailure_appliesExponentialBackoffAndRecordsError() {
        OutboxEvent event = createPendingEvent("user-fail-1");

        doThrow(new RuntimeException("Simulated Kafka broker failure")).when(kafkaTemplate).send(anyString(), anyString(), any());

        List<OutboxEvent> claimed = outboxClaimService.claimEvents(Instant.now(), Duration.ofMinutes(1), 10);

        Instant beforeProcess = Instant.now();
        outboxEventPublisher.processOne(claimed.get(0));

        OutboxEvent updated = outboxRepository.findById(event.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
        assertThat(updated.getAttemptCount()).isEqualTo(1);
        assertThat(updated.getLastError()).contains("Simulated Kafka broker failure");
        assertThat(updated.getNextRetryAt()).isAfter(beforeProcess);

    }


    @Test
    void publishFailure_transitionsToDeadState_afterMaxAttempts() {
        OutboxEvent event = createPendingEvent("user-dead-1");
        event.setAttemptCount(9);
        outboxRepository.save(event);

        doThrow(new RuntimeException("Simulated Unrecoverable Error")).when(kafkaTemplate).send(anyString(), anyString(), any());

        List<OutboxEvent> claimed = outboxClaimService.claimEvents(Instant.now(), Duration.ofMinutes(1), 10);

        outboxEventPublisher.processOne(claimed.get(0));
        OutboxEvent updated = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.DEAD);
        assertThat(updated.getAttemptCount()).isEqualTo(10);
        assertThat(updated.getLastError()).contains("Simulated Unrecoverable Error");
        // verify dead events are no longer claimable by the poller
        List<OutboxEvent> nextClaimed = outboxClaimService.claimEvents(Instant.now().plusSeconds(3600), Duration.ofMinutes(1), 10);
        assertThat(nextClaimed).isEmpty();
    }

    @Test
    void adminRevive_resetsDeadEventToPending_andAllowsRepublishing() {
        OutboxEvent event = createPendingEvent("user-revive-1");
        event.setStatus(OutboxEvent.OutboxStatus.DEAD);
        event.setAttemptCount(10);
        event.setLastError("Old Fatal Error");
        outboxRepository.save(event);

        outboxAdminService.reviveEvent(event.getId());

        OutboxEvent revived = outboxRepository.findById(event.getId()).orElseThrow();

        assertThat(revived.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
        assertThat(revived.getAttemptCount()).isEqualTo(0);
        assertThat(revived.getLastError()).isNull();

        List<OutboxEvent> claimed=outboxClaimService.claimEvents(Instant.now(), Duration.ofMinutes(1), 10);
        assertThat(claimed).hasSize(1);
        outboxEventPublisher.processOne(claimed.get(0));

        OutboxEvent published = outboxRepository.findById(event.getId()).orElseThrow();

        assertThat(published.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PUBLISHED);


    }
}
