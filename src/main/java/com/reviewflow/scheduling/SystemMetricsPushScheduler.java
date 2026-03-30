package com.reviewflow.scheduling;

import com.reviewflow.service.SystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SystemMetricsPushScheduler {

    @Autowired
    private SystemService systemService;

    /**
     * Push system metrics every 30 seconds to all SYSTEM_ADMIN users
     */
    @Scheduled(fixedDelayString = "${system.metrics.push.interval-ms:30000}")
    public void pushMetricsEvery30Seconds() {
        log.debug("Pushing system metrics to SYSTEM_ADMIN users");
        systemService.collectAndPushMetrics();
    }
}
