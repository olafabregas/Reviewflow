package com.reviewflow.service;

import com.reviewflow.exception.MalwareDetectedException;
import com.reviewflow.model.enums.ClamAvScanResult;
import com.reviewflow.monitoring.SecurityMetrics;
import fi.solita.clamav.ClamAVClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ClamAvScanService {

    private final ClamAVClient clamAVClient;
    private final SecurityMetrics securityMetrics;
    private final boolean enabled;

    public ClamAvScanService(
            @Value("${clamav.host:localhost}") String host,
            @Value("${clamav.port:3310}") int port,
            @Value("${clamav.timeout-ms:5000}") int timeoutMs,
            @Value("${clamav.enabled:false}") boolean enabled,
            SecurityMetrics securityMetrics) {
        this.enabled = enabled;
        this.securityMetrics = securityMetrics;
        
        if (enabled) {
            this.clamAVClient = new ClamAVClient(host, port, timeoutMs);
            log.info("ClamAV client initialized: {}:{} (timeout={}ms)", host, port, timeoutMs);
        } else {
            this.clamAVClient = null;
            log.info("ClamAV scanning is DISABLED");
        }
    }

    /**
     * Asynchronously scan a file for malware.
     * Returns immediately with a CompletableFuture that completes when scan finishes.
     * Does not block the calling thread.
     */
    @Async
    public CompletableFuture<ClamAvScanResult> scanAsync(Path filePath) {
        if (!enabled) {
            return CompletableFuture.completedFuture(ClamAvScanResult.DISABLED);
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] response = clamAVClient.scan(is);
            boolean isClean = ClamAVClient.isCleanReply(response);

            if (isClean) {
                log.info("ClamAV: File clean — {}", filePath.getFileName());
                securityMetrics.recordClamavClean();
                return CompletableFuture.completedFuture(ClamAvScanResult.CLEAN);
            } else {
                String virusName = new String(response).trim();
                log.warn("ClamAV: INFECTED — {} — virus={}", filePath.getFileName(), virusName);
                securityMetrics.recordClamavInfected();
                return CompletableFuture.completedFuture(ClamAvScanResult.INFECTED);
            }
        } catch (IOException e) {
            log.error("ClamAV scan error for file={}: {}", filePath.getFileName(), e.getMessage());
            securityMetrics.recordClamavError();
            return CompletableFuture.completedFuture(ClamAvScanResult.ERROR);
        }
    }

    /**
     * Synchronous scan with timeout — blocks until scan completes or times out.
     * Throws MalwareDetectedException if infected.
     */
    public void scanAndThrow(Path filePath, long timeoutMs) {
        ClamAvScanResult result;
        try {
            result = scanAsync(filePath).get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("ClamAV scan timeout for file={}", filePath.getFileName());
            securityMetrics.recordClamavError();
            throw new RuntimeException("ClamAV scan timeout");
        } catch (Exception e) {
            log.error("ClamAV scan failed for file={}: {}", filePath.getFileName(), e.getMessage());
            securityMetrics.recordClamavError();
            throw new RuntimeException("ClamAV scan failed", e);
        }

        if (result == ClamAvScanResult.INFECTED) {
            throw new MalwareDetectedException("Malware detected in file: " + filePath.getFileName());
        }
    }
}
