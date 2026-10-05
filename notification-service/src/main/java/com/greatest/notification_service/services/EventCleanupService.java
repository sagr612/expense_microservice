package com.greatest.notification_service.services;

import com.greatest.notification_service.repositories.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventCleanupService {
    private final ProcessedEventRepository processedEventRepository;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldProcessedEvents() {
        Instant cutoff = Instant.now().minus(14, ChronoUnit.DAYS);
        int deletedEvents = processedEventRepository.deleteByProcessedAtBefore(cutoff);
        log.info("Deleted {} processed events older than {}", deletedEvents, cutoff);
    }
}
