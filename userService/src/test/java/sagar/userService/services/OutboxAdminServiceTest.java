package sagar.userService.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sagar.userService.entities.OutboxEvent;
import sagar.userService.repository.OutboxRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@ExtendWith(MockitoExtension.class)
class OutboxAdminServiceTest {

    @Mock
    private OutboxRepository repo;
    @InjectMocks
    private OutboxAdminService adminService;

    @Test
    void reviveEvent_resetsStateToPending_whenEventIsDead() {
        OutboxEvent dead = OutboxEvent.builder()
                .id("evt-1").status(OutboxEvent.OutboxStatus.DEAD)
                .attemptCount(10).lastError("boom").build();
        when(repo.findById("evt-1")).thenReturn(Optional.of(dead));

        adminService.reviveEvent("evt-1");

        assertThat(dead.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
        assertThat(dead.getAttemptCount()).isEqualTo(0);
        assertThat(dead.getLastError()).isNull();
        assertThat(dead.getNextRetryAt()).isBeforeOrEqualTo(Instant.now());
        verify(repo).save(dead);
    }

    @Test
    void reviveEvent_throws_whenEventNotFound() {
        when(repo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adminService.reviveEvent("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviveEvent_throws_whenEventIsNotDead() {
        OutboxEvent published = OutboxEvent.builder()
                .id("evt-1").status(OutboxEvent.OutboxStatus.PUBLISHED).build();
        when(repo.findById("evt-1")).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> adminService.reviveEvent("evt-1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
