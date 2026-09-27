package com.greatest.expenseService.services;

import com.greatest.expenseService.entities.OutboxEvent;
import com.greatest.expenseService.entities.OutboxEvent.OutboxStatus;
import com.greatest.expenseService.repository.OutboxRepository;
import com.greatest.expenseService.services.OutboxClaimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxClaimServiceTest {
    @Mock
    private OutboxRepository outboxRepository;
    @InjectMocks
    private OutboxClaimService outboxClaimService;
    private OutboxEvent pendingEvent;

    @BeforeEach
    void setUp() {
        pendingEvent = OutboxEvent.builder()
                .id("evt-1")
                .aggregateId("user-123")
                .eventType("EXPENSE_CREATED")
                .status(OutboxStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Should claim pending events, update status to PROCESSING and set lockedUntil lease")
    void claimEvents_success() {
        Instant now = Instant.now();
        Duration lease = Duration.ofMinutes(1);
        int batchSize = 10;
        when(outboxRepository.findClaimableEvents(eq(now), eq(batchSize))).thenReturn(List.of(pendingEvent));
        when(outboxRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<OutboxEvent> claimed = outboxClaimService.claimEvents(now, lease, batchSize);
        assertThat(claimed).hasSize(1);
        OutboxEvent claimedEvent = claimed.get(0);
        assertThat(claimedEvent.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(claimedEvent.getLockedUntil()).isEqualTo(now.plus(lease));
        verify(outboxRepository).saveAll(any());
    }

    @Test
    @DisplayName("Should return empty list when no claimable events found")
    void claimEvents_empty() {
        Instant now = Instant.now();
        when(outboxRepository.findClaimableEvents(eq(now), eq(10))).thenReturn(List.of());


        List<OutboxEvent> claimed=outboxClaimService.claimEvents(now, Duration.ofMinutes(1), 10);
        assertThat(claimed).isEmpty();
        verify(outboxRepository, never()).saveAll(any());
    }
}
