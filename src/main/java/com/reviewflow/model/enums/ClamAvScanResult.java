package com.reviewflow.model.enums;

public enum ClamAvScanResult {
    CLEAN,         // No threat found
    INFECTED,      // Malware detected
    ERROR,         // Scan failed (timeout, network error, etc.)
    DISABLED       // clamav.enabled=false
}
