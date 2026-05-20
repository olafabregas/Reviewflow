package com.reviewflow.infrastructure.storage;

import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.shared.domain.ClamAvScanResult;
import com.reviewflow.submission.exception.MalwareDetectedException;
import fi.solita.clamav.ClamAVClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ClamAvScanService {

  private final ClamAVClient clamAVClient;
  private final ReviewFlowMetrics metrics;
  private final boolean enabled;

  public ClamAvScanService(
      @Value("${clamav.host:localhost}") String host,
      @Value("${clamav.port:3310}") int port,
      @Value("${clamav.timeout-ms:5000}") int timeoutMs,
      @Value("${clamav.enabled:false}") boolean enabled,
      ReviewFlowMetrics metrics) {
    this.enabled = enabled;
    this.metrics = metrics;

    if (enabled) {
      this.clamAVClient = new ClamAVClient(host, port, timeoutMs);
      log.info("ClamAV client initialized: {}:{} (timeout={}ms)", host, port, timeoutMs);
    } else {
      this.clamAVClient = null;
      log.info("ClamAV scanning is DISABLED");
    }
  }

  public boolean ping() {
    if (!enabled || clamAVClient == null) {
      return false;
    }
    try {
      clamAVClient.ping();
      return true;
    } catch (Exception e) {
      log.debug("ClamAV ping failed: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Asynchronously scan a file for malware. Returns immediately with a CompletableFuture that
   * completes when scan finishes. Does not block the calling thread.
   */
  @Async("scanExecutor")
  public CompletableFuture<ClamAvScanResult> scanAsync(Path filePath) {
    if (!enabled) {
      return CompletableFuture.completedFuture(ClamAvScanResult.DISABLED);
    }

    try (InputStream is = Files.newInputStream(filePath)) {
      byte[] response = clamAVClient.scan(is);
      boolean isClean = ClamAVClient.isCleanReply(response);

      if (isClean) {
        log.info("ClamAV: File clean — {}", filePath.getFileName());
        metrics.recordClamAvScanResult("clean");
        return CompletableFuture.completedFuture(ClamAvScanResult.CLEAN);
      } else {
        String virusName = new String(response).trim();
        log.warn("ClamAV: INFECTED — {} — virus={}", filePath.getFileName(), virusName);
        metrics.recordClamAvScanResult("infected");
        return CompletableFuture.completedFuture(ClamAvScanResult.INFECTED);
      }
    } catch (IOException e) {
      log.error("ClamAV scan error for file={}", filePath.getFileName(), e);
      metrics.recordClamAvScanResult("error");
      return CompletableFuture.completedFuture(ClamAvScanResult.ERROR);
    }
  }

  /**
   * Synchronous scan with timeout — blocks until scan completes or times out. Throws
   * MalwareDetectedException if infected.
   */
  public void scanAndThrow(Path filePath, long timeoutMs) {
    ClamAvScanResult result;
    try {
      result = scanAsync(filePath).get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      log.warn("ClamAV scan timeout for file={}", filePath.getFileName());
      metrics.recordClamAvScanResult("error");
      throw new RuntimeException("ClamAV scan timeout");
    } catch (RejectedExecutionException e) {
      metrics.recordScanRejected();
      log.warn("ClamAV scan rejected for file={}: queue full", filePath.getFileName());
      throw new RuntimeException("ClamAV scan queue full", e);
    } catch (Exception e) {
      log.error("ClamAV scan failed for file={}", filePath.getFileName(), e);
      metrics.recordClamAvScanResult("error");
      throw new RuntimeException("ClamAV scan failed", e);
    }

    if (result == ClamAvScanResult.INFECTED) {
      throw new MalwareDetectedException("Malware detected in file: " + filePath.getFileName());
    }
  }
}
