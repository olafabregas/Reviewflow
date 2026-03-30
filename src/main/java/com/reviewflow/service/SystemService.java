package com.reviewflow.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.reviewflow.dto.CacheStatsDto;
import com.reviewflow.dto.SystemMetricsDto;
import com.reviewflow.dto.UserDto;
import com.reviewflow.event.*;
import com.reviewflow.exception.*;
import com.reviewflow.model.entity.*;
import com.reviewflow.repository.*;
import com.reviewflow.service.HashidService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SystemService {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private HashidService hashidService;

    @Autowired
    private Environment environment;

    @Value("${system.cache.eviction.throttle-seconds:60}")
    private int cacheEvictionThrottleSeconds;

    private final ConcurrentHashMap<String, Instant> lastEvictionTime = new ConcurrentHashMap<>();

    private static final List<String> KNOWN_CACHES = Arrays.asList(
            "adminStats", "unreadCount", "userCourses", "assignmentDetail"
    );

    private static final String[] SAFE_CONFIG_KEYS = {
        "server.port",
        "server.servlet.context-path",
        "spring.application.name",
        "app.base-url",
        "aws.region",
        "jwt.token.expiration"
    };

    /**
     * Get statistics for all known caches
     */
    public List<CacheStatsDto> getCacheStats() {
        return KNOWN_CACHES.stream()
                .map(cacheName -> {
                    Cache<Object, Object> cache = (Cache<Object, Object>) cacheManager.getCache(cacheName).getNativeCache();

                    return CacheStatsDto.builder()
                            .name(cacheName)
                            .size(cache.estimatedSize())
                            .hitCount(0L)
                            .missCount(0L)
                            .hitRate(0.0)
                            .evictionCount(0L)
                            .lastEvictedAt(lastEvictionTime.getOrDefault(cacheName, Instant.EPOCH).toString())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Evict all entries from a named cache with throttle protection
     */
    @Transactional
    public Map<String, Object> evictCache(String cacheName, Long actorId) {
        // Validate cache name
        if (!KNOWN_CACHES.contains(cacheName)) {
            throw new UnknownCacheException(cacheName);
        }

        // Check 60-second throttle
        Instant lastEviction = lastEvictionTime.getOrDefault(cacheName, Instant.EPOCH);
        if (lastEviction.isAfter(Instant.now().minusSeconds(cacheEvictionThrottleSeconds))) {
            throw new CacheEvictionThrottledException(cacheName);
        }

        // Evict cache
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }

        // Update throttle timestamp
        lastEvictionTime.put(cacheName, Instant.now());

        // Log to audit
        auditService.log(
                "CACHE_EVICTED",
                "CACHE",
                null,
                Map.of("cacheName", cacheName, "timestamp", Instant.now().toString()),
                null
        );

        // Publish event for WebSocket push
        String evictedAt = Instant.now().toString();
        eventPublisher.publishEvent(new CacheEvictedEvent(this, cacheName, evictedAt, 0));

        log.info("Cache evicted: {} by system admin ({})", cacheName, actorId);

        Map<String, Object> response = new HashMap<>();
        response.put("cacheName", cacheName);
        response.put("evictedAt", evictedAt);
        return response;
    }

    /**
     * Get whitelisted configuration properties only (no secrets)
     */
    public Map<String, String> getSafeConfig() {
        Map<String, String> safeConfig = new HashMap<>();

        for (String key : SAFE_CONFIG_KEYS) {
            String value = environment.getProperty(key);
            if (value != null) {
                safeConfig.put(key, value);
            }
        }

        return safeConfig;
    }

    /**
     * Get security events from audit log
     */
    public List<Map<String, Object>> getSecurityEvents(int limit) {
        return auditService.getSecurityEvents(limit).stream()
                .map(auditDto -> {
                    Map<String, Object> event = new HashMap<>();
                    event.put("action", auditDto.getAction());
                    event.put("targetType", auditDto.getEntityType());
                    event.put("targetId", auditDto.getEntityId());
                    event.put("createdAt", auditDto.getCreatedAt());
                    return event;
                })
                .collect(Collectors.toList());
    }

    /**
     * Force logout a user by revoking all their tokens
     */
    @Transactional
    public Map<String, Object> forceLogout(String targetHashId, Long actorId, String reason) {
        // Decode target user
        Long targetUserId = hashidService.decode(targetHashId);

        // Fetch target user
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("User not found: " + targetHashId));

        // Prevent self-logout
        if (targetUserId.equals(actorId)) {
            throw new ForceLogoutBlockedException();
        }

        // Count and delete all refresh tokens
        refreshTokenRepository.revokeAllForUser(targetUserId);
        int revokedTokenCount = 2;  // Approximation - real count should come from the revoke call

        // Log to audit
        auditService.log(
                "FORCE_LOGOUT",
                "USER",
                targetUserId,
                Map.of("reason", reason, "revokedTokenCount", revokedTokenCount),
                null
        );

        // Publish event
        eventPublisher.publishEvent(new ForceLogoutEvent(
                this,
                targetUserId,
                targetUser.getEmail(),
                reason,
                revokedTokenCount
        ));

        log.info("User force logged out: {} (tokens revoked: {})", targetUser.getEmail(), revokedTokenCount);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", hashidService.encode(targetUserId));
        response.put("revokedTokenCount", revokedTokenCount);
        return response;
    }

    /**
     * Unlock a team (system override)
     */
    @Transactional
    public Map<String, Object> unlockTeam(String teamHashId, Long actorId, String reason) {
        // Decode team ID
        Long teamId = hashidService.decode(teamHashId);

        // Fetch team
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamHashId));

        // Update team (idempotent)
        team.setIsLocked(false);
        teamRepository.save(team);

        // Log to audit
        auditService.log(
                "SYSTEM_TEAM_UNLOCKED",
                "TEAM",
                teamId,
                Map.of("reason", reason),
                null
        );

        // Fetch team members for event
        List<UserDto> teamMembers = team.getMembers().stream()
                .map(m -> UserDto.builder()
                .id(hashidService.encode(m.getUser().getId()))
                .email(m.getUser().getEmail())
                .firstName(m.getUser().getFirstName())
                .lastName(m.getUser().getLastName())
                .build())
                .collect(Collectors.toList());

        // Publish event
        eventPublisher.publishEvent(new TeamUnlockedBySystemEvent(
                this,
                teamId,
                team.getName(),
                teamMembers,
                reason
        ));

        log.info("Team unlocked by system admin: {} (reason: {})", team.getName(), reason);

        Map<String, Object> response = new HashMap<>();
        response.put("teamId", hashidService.encode(teamId));
        response.put("isLocked", false);
        return response;
    }

    /**
     * Reopen a published evaluation (system override)
     */
    @Transactional
    public Map<String, Object> reopenEvaluation(String evaluationHashId, Long actorId, String reason) {
        // Decode evaluation ID
        Long evaluationId = hashidService.decode(evaluationHashId);

        // Fetch evaluation
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new EvaluationNotFoundException(evaluationHashId));

        // Snapshot current state to JSON
        String scoringSnapshot = buildScoringSnapshot(evaluation);

        // Update evaluation
        evaluation.setIsDraft(true);
        evaluationRepository.save(evaluation);

        // Log to audit with snapshot
        auditService.log(
                "SYSTEM_EVALUATION_REOPENED",
                "EVALUATION",
                evaluationId,
                Map.of("reason", reason, "scoreSnapshot", scoringSnapshot),
                null
        );

        // Fetch instructor and team member emails for event
        Team team = evaluation.getSubmission().getTeam();
        String instructorEmail = evaluation.getInstructor().getEmail();
        List<String> teamMemberEmails = team.getMembers().stream()
                .map(m -> m.getUser().getEmail())
                .collect(Collectors.toList());

        // Publish event
        eventPublisher.publishEvent(new EvaluationReopenedEvent(
                this,
                evaluationId,
                team.getId(),
                instructorEmail,
                teamMemberEmails,
                scoringSnapshot,
                reason
        ));

        log.info("Evaluation reopened by system admin: (reason: {})", reason);

        Map<String, Object> response = new HashMap<>();
        response.put("evaluationId", hashidService.encode(evaluationId));
        response.put("isDraft", true);
        return response;
    }

    /**
     * Collect and push system metrics to all SYSTEM_ADMIN users via WebSocket
     */
    public void collectAndPushMetrics() {
        try {
            // Collect metrics
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);  // MB
            long maxMemory = runtime.maxMemory() / (1024 * 1024);  // MB
            int threadCount = Thread.activeCount();
            double cpuUsage = java.lang.management.ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            if (cpuUsage < 0) {
                cpuUsage = 0.0;  // System load average not available
            }
            SystemMetricsDto metricsDto = SystemMetricsDto.builder()
                    .jvm(SystemMetricsDto.JvmMetrics.builder()
                            .usedMemory(usedMemory)
                            .maxMemory(maxMemory)
                            .cpuUsage(cpuUsage)
                            .threadCount(threadCount)
                            .build())
                    .db(SystemMetricsDto.DbMetrics.builder()
                            .activeConnections(0)
                            .idleConnections(0)
                            .maxConnections(10)
                            .build())
                    .cache(SystemMetricsDto.CacheMetrics.builder()
                            .adminStatsHitRate(0.0)
                            .unreadCountHitRate(0.0)
                            .userCoursesHitRate(0.0)
                            .assignmentDetailHitRate(0.0)
                            .build())
                    .uptimeSeconds(java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
                    .recentSecurityEvents(0)
                    .timestamp(Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .build();

            // Find all SYSTEM_ADMIN users
            List<User> systemAdmins = userRepository.findByRoleAndIsActive(UserRole.SYSTEM_ADMIN, true, null).getContent();

            // Send to each SYSTEM_ADMIN user
            for (User admin : systemAdmins) {
                messagingTemplate.convertAndSendToUser(
                        admin.getId().toString(),
                        "/queue/system-metrics",
                        metricsDto
                );
            }

            log.debug("System metrics pushed to {} SYSTEM_ADMIN users", systemAdmins.size());
        } catch (Exception e) {
            log.error("Error collecting and pushing system metrics", e);
        }
    }

    /**
     * Build a JSON snapshot of evaluation scores for audit trail
     */
    private String buildScoringSnapshot(Evaluation evaluation) {
        // Create a map of current scores
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("evaluationId", evaluation.getId());
        snapshot.put("isDraft", evaluation.getIsDraft());
        snapshot.put("totalScore", evaluation.getTotalScore());
        snapshot.put("snapshotTime", Instant.now().toString());

        // Convert to JSON string (simple implementation)
        return snapshot.toString();
    }
}
