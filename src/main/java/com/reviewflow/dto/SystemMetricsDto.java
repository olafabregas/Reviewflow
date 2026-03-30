package com.reviewflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMetricsDto {

    @JsonProperty("jvm")
    private JvmMetrics jvm;

    @JsonProperty("db")
    private DbMetrics db;

    @JsonProperty("cache")
    private CacheMetrics cache;

    @JsonProperty("uptimeSeconds")
    private long uptimeSeconds;

    @JsonProperty("recentSecurityEvents")
    private int recentSecurityEvents;

    @JsonProperty("timestamp")
    private String timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JvmMetrics {

        @JsonProperty("usedMemory")
        private long usedMemory;

        @JsonProperty("maxMemory")
        private long maxMemory;

        @JsonProperty("cpuUsage")
        private double cpuUsage;

        @JsonProperty("threadCount")
        private int threadCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DbMetrics {

        @JsonProperty("activeConnections")
        private int activeConnections;

        @JsonProperty("idleConnections")
        private int idleConnections;

        @JsonProperty("maxConnections")
        private int maxConnections;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheMetrics {

        @JsonProperty("adminStats")
        private Double adminStatsHitRate;

        @JsonProperty("unreadCount")
        private Double unreadCountHitRate;

        @JsonProperty("userCourses")
        private Double userCoursesHitRate;

        @JsonProperty("assignmentDetail")
        private Double assignmentDetailHitRate;
    }
}
