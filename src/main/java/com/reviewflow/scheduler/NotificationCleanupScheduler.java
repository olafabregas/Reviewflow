package com.reviewflow.scheduler;

import com.reviewflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;

    // Runs every day at 2:00 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void deleteOldNotifications() {
        Instant cutoff = Instant.now().minus(60, ChronoUnit.DAYS);
        notificationService.deleteOlderThan(cutoff);
        log.info("Deleted notifications older than 60 days");
    }
}
