package com.reviewflow.infrastructure.health;

import com.reviewflow.infrastructure.storage.ClamAvScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "clamav.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ClamAvHealthIndicator implements HealthIndicator {

  private final ClamAvScanService clamAvScanService;

  @Override
  public Health health() {
    try {
      boolean reachable = clamAvScanService.ping();
      return reachable
          ? Health.up().build()
          : Health.down().withDetail("error", "Scanner unreachable").build();
    } catch (Exception e) {
      return Health.down().withException(e).build();
    }
  }
}
