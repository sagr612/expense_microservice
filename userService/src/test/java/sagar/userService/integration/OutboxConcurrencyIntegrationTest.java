package sagar.userService.integration;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.producer.OutboxClaimService;
import sagar.userService.repository.OutboxRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
@Import(OutboxClaimService.class)
public class OutboxConcurrencyIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OutboxClaimService outboxClaimService;
    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void cleanup() {
        outboxRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentWorkers_claimDistinctEventsWithoutOverlap() throws Exception {
        int totalEvents = 10, batchSize = 5;
        for (int i = 0; i < totalEvents; i++) {
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateId("user-" + i)
                    .eventType("USER_DELETED")
                    .payload("{\"userId\":\"user-" + i + "\"}")
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .nextRetryAt(Instant.now().minusSeconds(10))
                    .published(false)
                    .build());
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Instant now = Instant.now();
        Duration lease = Duration.ofMinutes(2);

        List<Future<List<OutboxEvent>>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                readyLatch.await();
                startLatch.await();

                return outboxClaimService.claimEvents(now, lease, batchSize);
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        List<OutboxEvent> worker1Events = futures.get(0).get();
        List<OutboxEvent> worker2Events = futures.get(1).get();
        executor.shutdown();

        assertThat(worker1Events).hasSize(batchSize);
        assertThat(worker2Events).hasSize(batchSize);

        Set<String> worker1Ids = new HashSet<>();
        worker1Events.forEach(e -> worker1Ids.add(e.getId()));

        Set<String> worker2Ids = new HashSet<>();
        worker2Events.forEach(e -> worker2Ids.add(e.getId()));

        Set<String> intersection = new HashSet<>(worker1Ids);
        intersection.retainAll(worker2Ids);
        assertThat(intersection).as("Workers must not claim any overlapping events").isEmpty();

        Set<String> allClaimedIds = new HashSet<>(worker1Ids);
        allClaimedIds.addAll(worker2Ids);
        assertThat(allClaimedIds).hasSize(totalEvents);

    }

    @Test
    void expiredLease_isReclaimedWhenWorkerCrashes() {
        Instant now = Instant.now();
        OutboxEvent expiredEvent = outboxRepository.save(OutboxEvent.builder()
                .aggregateId("user-stuck")
                .eventType("USER_DELETED")
                .payload("{\"userId\":\"user-stuck\"}")
                .status(OutboxEvent.OutboxStatus.PROCESSING)
                .lockedUntil(now.minusSeconds(30)) // lease expired 30s ago!
                .nextRetryAt(now.minusSeconds(60))
                .published(false)
                .build());

        List<OutboxEvent> claimed = outboxClaimService.claimEvents(now, Duration.ofMinutes(5), 10);

        assertThat(claimed).hasSize(1);
        OutboxEvent claimedEvent = claimed.get(0);
        assertThat(claimedEvent.getId()).isEqualTo(expiredEvent.getId());
        assertThat(claimedEvent.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PROCESSING);

        assertThat(claimedEvent.getLockedUntil()).isAfter(now);

    }

    @Test
    void futureRetryAt_isNotClaimed(){
        Instant now = Instant.now();
        outboxRepository.save(OutboxEvent.builder()
                .aggregateId("user-backoff")
                .eventType("USER_DELETED")
                .payload("{\"userId\":\"user-backoff\"}")
                .status(OutboxEvent.OutboxStatus.PENDING)
                .nextRetryAt(now.plusSeconds(300)) // 5 minutes in future
                .published(false)
                .build());
        List<OutboxEvent> claimed=outboxClaimService.claimEvents(now, Duration.ofMinutes(5), 10);

        assertThat(claimed).isEmpty();
    }
}
