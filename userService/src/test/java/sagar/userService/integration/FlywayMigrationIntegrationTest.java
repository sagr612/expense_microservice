package sagar.userService.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.entities.ProcessedEvent;
import sagar.userService.entities.UserInfo;
import sagar.userService.entities.FailedEvents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
class FlywayMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestEntityManager em;

    @Test
    void allMigrations_runWithoutError() {
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    void usersTable_canPersistAndRetrieve() {
        UserInfo user = UserInfo.builder()
                .userId("test-user-1")
                .firstName("Sagar")
                .lastName("Sharma")
                .email("sagar@test.com")
                .phoneNumber("9999999999")
                .build();

        UserInfo saved = em.persistFlushFind(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo("test-user-1");
    }

    @Test
    void usersTable_userId_isUnique() {
        em.persist(UserInfo.builder()
                .userId("dup-user")
                .firstName("A").lastName("B")
                .email("a@test.com").phoneNumber("1111111111")
                .build());
        em.flush();

        assertThatCode(() -> {
            em.persist(UserInfo.builder()
                    .userId("dup-user")      // same userId
                    .firstName("C").lastName("D")
                    .email("c@test.com").phoneNumber("2222222222")
                    .build());
            em.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    void processedEventsTable_canPersistAndRetrieve() {
        ProcessedEvent event = ProcessedEvent.builder()
                .eventId("evt-123")
                .consumerGroup("userinfo-consumer-group")
                .build();

        ProcessedEvent saved = em.persistFlushFind(event);

        assertThat(saved.getEventId()).isEqualTo("evt-123");
        assertThat(saved.getProcessedAt()).isNotNull();
    }

    @Test
    void outboxTable_hasLockedUntilColumn() {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId("user-1")
                .eventType("USER_DELETED")
                .payload("{\"userId\":\"user-1\"}")
                .build();

        OutboxEvent saved = em.persistFlushFind(event);

        assertThat(saved.getLockedUntil()).isNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
    }

        @Test
    void failedEventsTable_canPersistAndRetrieve() {
        FailedEvents failed = new FailedEvents();
        failed.setTopic("user.events.DLT");
        failed.setPartition(0);
        failed.setOffset(42L);
        failed.setEventType("USER_EVENT");
        failed.setPayload("{\"userId\":\"bad-user\"}");
        failed.setErrorMessage("Something went wrong");
        failed.setStatus(sagar.userService.entities.FailedEventStatus.FAILED);
        failed.setRetryCount(0);
        failed.setCreatedAt(java.time.LocalDateTime.now());
        failed.setUpdatedAt(java.time.LocalDateTime.now());

        FailedEvents saved = em.persistFlushFind(failed);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTopic()).isEqualTo("user.events.DLT");
    }
}
