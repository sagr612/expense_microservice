package sagar.userService.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import sagar.userService.entities.OutboxEvent;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the default embedded/test DB config for @DataJpaTest (H2 by default unless you've
// excluded it) — no Testcontainers needed here since we're only exercising the query method,
// not anything Postgres-specific.
@DataJpaTest
class OutboxRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void findTop50ByPublishedFalse_returnsOnlyUnpublishedEvents_oldestFirst() {
        OutboxEvent published = OutboxEvent.builder()
                .aggregateId("user-1")
                .eventType("USER_DELETED")
                .payload("{}")
                .published(true)
                .build();

        OutboxEvent older = OutboxEvent.builder()
                .aggregateId("user-2")
                .eventType("USER_DELETED")
                .payload("{}")
                .published(false)
                .build();

        OutboxEvent newer = OutboxEvent.builder()
                .aggregateId("user-3")
                .eventType("USER_DELETED")
                .payload("{}")
                .published(false)
                .build();

        entityManager.persist(published);
        entityManager.persist(older);
        entityManager.flush(); // ensure distinct createdAt ordering between older/newer
        entityManager.persist(newer);
        entityManager.flush();

        List<OutboxEvent> result = outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OutboxEvent::getAggregateId)
                .containsExactly("user-2", "user-3"); // oldest unpublished first
        assertThat(result).noneMatch(OutboxEvent::isPublished);
    }

    @Test
    void findTop50ByPublishedFalse_returnsEmptyList_whenAllEventsPublished() {
        OutboxEvent published = OutboxEvent.builder()
                .aggregateId("user-1")
                .eventType("USER_DELETED")
                .payload("{}")
                .published(true)
                .build();

        entityManager.persist(published);
        entityManager.flush();

        List<OutboxEvent> result = outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        assertThat(result).isEmpty();
    }
}
