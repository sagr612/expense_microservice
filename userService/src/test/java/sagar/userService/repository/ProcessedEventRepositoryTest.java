package sagar.userService.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import sagar.userService.entities.ProcessedEvent;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest

@ActiveProfiles("test")
class ProcessedEventRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired private ProcessedEventRepository repo;

    @Test
    void existsById_returnsTrue_whenEventAlreadyProcessed() {
        entityManager.persist(ProcessedEvent.builder()
                .eventId("evt-123").consumerGroup("test-group").build());
        entityManager.flush();

        assertThat(repo.existsById("evt-123")).isTrue();
    }

    @Test
    void existsById_returnsFalse_whenEventNeverProcessed() {
        assertThat(repo.existsById("evt-nonexistent")).isFalse();
    }

    @Test
    void save_isIdempotent_secondSaveWithSameIdIsANoOp() {
        repo.saveAndFlush(ProcessedEvent.builder()
                .eventId("evt-123").consumerGroup("g").build());
        // Second save is really an update via same PK — no exception, still one row.
        repo.saveAndFlush(ProcessedEvent.builder()
                .eventId("evt-123").consumerGroup("g").build());

        assertThat(repo.count()).isEqualTo(1);
    }
}

