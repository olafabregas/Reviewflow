package com.reviewflow.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.shared.domain.ClamAvScanResult;
import com.reviewflow.submission.exception.MalwareDetectedException;

import fi.solita.clamav.ClamAVClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ClamAvScanService {

  private final ClamAVClient clamAVClient;
  private final ReviewFlowMetrics metrics;
  private final boolean clamavEnabled;

  public ClamAvScanService(
      @Value("${clamav.host:localhost}") String host,
      @Value("${clamav.port:3310}") int port,
      @Value("${clamav.timeout-ms:5000}") int timeoutMs,
      @Value("${clamav.enabled:false}") boolean enabled,
      ReviewFlowMetrics metrics) {
    this.clamavEnabled = enabled;
    this.metrics = metrics;

    if (enabled) {
      this.clamAVClient = new ClamAVClient(host, port, timeoutMs);
      log.info("ClamAV client initialized: {}:{} (timeout={}ms)", host, port, timeoutMs);
    } else {
      this.clamAVClient = null;
      log.info("ClamAV scanning is DISABLED");
    }
  }

  /**
   * Asynchronously scan a file for malware. Returns immediately with a CompletableFuture that
   * completes when scan finishes. Does not block the calling thread.
   */
  @Async("scanExecutor")
  public CompletableFuture<ClamAvScanResult> scanAsync(Path filePath) {
    if (!clamavEnabled) {
      return CompletableFuture.completedFuture(ClamAvScanResult.DISABLED);
    }

    try (InputStream is = Files.newInputStream(filePath)) {
      byte[] response = clamAVClient.scan(is);
      boolean isClean = ClamAVClient.isCleanReply(response);

      if (isClean) {
        log.info("ClamAV: File clean — {}", filePath.getFileName());
        metrics.recordScanClean();
        return CompletableFuture.completedFuture(ClamAvScanResult.CLEAN);
      } else {
        String virusName = new String(response).trim();
        log.warn("ClamAV: INFECTED — {} — virus={}", filePath.getFileName(), virusName);
        metrics.recordMalwareDetected();
        return CompletableFuture.completedFuture(ClamAvScanResult.INFECTED);
      }
    } catch (IOException e) {
      log.error("ClamAV scan error for file={}: {}", filePath.getFileName(), e.getMessage());
      metrics.recordScanError();
      return CompletableFuture.completedFuture(ClamAvScanResult.ERROR);
    }
  }

  /**
   * Synchronous scan with timeout — blocks until scan completes or times out. Throws
   * MalwareDetectedException if infected; MalwareScanUnavailableException when scanner is
   * unavailable in production.
   */
  public void scanAndThrow(Path filePath, long timeoutMs) {
    ClamAvScanResult result;

    try {
      result = scanAsync(filePath).get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      handleScanError("ClamAV scan timed out", e, filePath);
      return;
    } catch (RejectedExecutionException e) {
      metrics.recordScanRejected();
      handleScanError("ClamAV scan rejected: queue full", e, filePath);
      return;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      handleScanError("ClamAV scan interrupted", e, filePath);
      return;
    } catch (Exception e) {
      handleScanError("ClamAV scan threw unexpected exception", e, filePath);
      return;
    }

    switch (result) {
      case INFECTED -> {
        metrics.recordMalwareDetected();
        log.warn("Malware detected in file: {}", filePath.getFileName());
        throw new MalwareDetectedException("File rejected: malware detected");
      }
      case ERROR ->
          handleScanError(
              "ClamAV returned ERROR for file: " + filePath.getFileName(), null, filePath);
      case DISABLED -> {
        if (clamavEnabled) {
          handleScanError(
              "ClamAV is enabled but scanner reports DISABLED", null, filePath);
        } else {
          log.debug("ClamAV disabled — skipping scan for {}", filePath.getFileName());
        }
      }
      case CLEAN -> {
        metrics.recordScanClean();
        log.debug("ClamAV scan clean: {}", filePath.getFileName());
      }
    }
  }

  private void handleScanError(String message, Exception cause, Path filePath) {
    metrics.recordScanError();
    if (clamavEnabled) {
      if (cause != null) {
        log.error("{} — rejecting upload (fail-closed in prod)", message, cause);
      } else {
        log.error("{} — rejecting upload (fail-closed in prod)", message);
      }
      throw new MalwareScanUnavailableException(
          "File upload rejected: virus scanner is unavailable. Please try again.");
    } else {
      log.warn("{} — allowing upload (fail-open in non-prod)", message);
      if (cause != null) {
        log.warn("Scan error cause: {}", cause.getMessage());
      }
    }
  }
}
