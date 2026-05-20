package com.reviewflow.system.service;

import static com.reviewflow.shared.constant.CacheNames.CACHE_ADMIN_STATS;
import static com.reviewflow.shared.constant.CacheNames.CACHE_ASSIGNMENT;
import static com.reviewflow.shared.constant.CacheNames.CACHE_ASSIGNMENT_GROUPS;
import static com.reviewflow.shared.constant.CacheNames.CACHE_UNREAD_COUNT;
import static com.reviewflow.shared.constant.CacheNames.CACHE_USER_COURSES;

import com.github.benmanes.caffeine.cache.Cache;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import com.reviewflow.shared.dto.AuditLogDto;
import com.reviewflow.shared.dto.CacheEvictResponse;
import com.reviewflow.shared.dto.CacheStatsDto;
import com.reviewflow.shared.dto.ForceLogoutResponse;
import com.reviewflow.shared.dto.ReopenEvaluationResponse;
import com.reviewflow.shared.dto.SecurityEventDto;
import com.reviewflow.shared.dto.SystemMetricsDto;
import com.reviewflow.shared.dto.UnlockTeamResponse;
import com.reviewflow.shared.dto.UserDto;
import com.reviewflow.shared.event.CacheEvictedEvent;
import com.reviewflow.evaluation.event.EvaluationReopenedEvent;
import com.reviewflow.shared.event.ForceLogoutEvent;
import com.reviewflow.team.event.TeamUnlockedBySystemEvent;
import com.reviewflow.admin.service.AuditService;
import com.reviewflow.auth.service.TokenVersionService;
import com.reviewflow.system.exception.CacheEvictionThrottledException;
import com.reviewflow.evaluation.exception.EvaluationNotFoundException;
import com.reviewflow.system.exception.ForceLogoutBlockedException;
import com.reviewflow.team.exception.TeamNotFoundException;
import com.reviewflow.system.exception.UnknownCacheException;
import com.reviewflow.shared.domain.Evaluation;
import com.reviewflow.shared.domain.Team;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.evaluation.repository.EvaluationRepository;
import com.reviewflow.auth.repository.RefreshTokenRepository;
import com.reviewflow.team.repository.TeamRepository;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.shared.util.HashidService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class SystemService {

  @Autowired private CacheManager cacheManager;

  @Autowired private DataSource dataSource;

  @Autowired private MeterRegistry meterRegistry;

  @Autowired private SimpMessagingTemplate messagingTemplate;

  @Autowired private AuditService auditService;

  @Autowired private UserRepository userRepository;

  @Autowired private EvaluationRepository evaluationRepository;

  @Autowired private TeamRepository teamRepository;

  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired private HashidService hashidService;

  @Autowired private SystemMessagingService systemMessagingService;

  @Autowired private Environment environment;
  @Autowired private TokenVersionService tokenVersionService;

  @Value("${system.cache.eviction.throttle-seconds:60}")
  private int cacheEvictionThrottleSeconds;

  private final ConcurrentHashMap<String, Instant> lastEvictionTime = new ConcurrentHashMap<>();

  private static final List<String> KNOWN_CACHES =
      Arrays.asList(
          CACHE_ADMIN_STATS,
          CACHE_UNREAD_COUNT,
          CACHE_USER_COURSES,
          CACHE_ASSIGNMENT,
          CACHE_ASSIGNMENT_GROUPS);

  private static final String[] SAFE_CONFIG_KEYS = {
    "server.port",
    "server.servlet.context-path",
    "spring.application.name",
    "app.base-url",
    "aws.region",
    "jwt.token.expiration",
    "security.password.min-length",
    "security.password.max-length",
    "security.password.require-uppercase",
    "security.password.require-lowercase",
    "security.password.require-number",
    "security.password.require-special",
    "security.password.allow-whitespace"
  };

  /** Get statistics for all known caches */
  public List<CacheStatsDto> getCacheStats() {
    return KNOWN_CACHES.stream()
        .map(
            cacheName -> {
              org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
              // Null-safe: skip caches that don't exist to prevent NullPointerException
              if (springCache == null) {
                return CacheStatsDto.builder()
                    .name(cacheName)
                    .size(0L)
                    .hitCount(0L)
                    .missCount(0L)
                    .hitRate(0.0)
                    .evictionCount(0L)
                    .lastEvictedAt(Instant.EPOCH.toString())
                    .build();
              }

              try {
                Cache<Object, Object> cache = (Cache<Object, Object>) springCache.getNativeCache();

                // Additional null-check for native cache
                if (cache == null) {
                  return CacheStatsDto.builder()
                      .name(cacheName)
                      .size(0L)
                      .hitCount(0L)
                      .missCount(0L)
                      .hitRate(0.0)
                      .evictionCount(0L)
                      .lastEvictedAt(Instant.EPOCH.toString())
                      .build();
                }

                com.github.benmanes.caffeine.cache.stats.CacheStats stats = cache.stats();
                return CacheStatsDto.builder()
                    .name(cacheName)
                    .size(cache.estimatedSize())
                    .hitCount(stats != null ? stats.hitCount() : 0L)
                    .missCount(stats != null ? stats.missCount() : 0L)
                    .hitRate(stats != null ? safeRate(stats.hitRate()) : 0.0)
                    .evictionCount(stats != null ? stats.evictionCount() : 0L)
                    .lastEvictedAt(
                        lastEvictionTime.getOrDefault(cacheName, Instant.EPOCH).toString())
                    .build();
              } catch (Exception e) {
                log.warn("Error retrieving cache stats for {}: {}", cacheName, e.getMessage());
                return CacheStatsDto.builder()
                    .name(cacheName)
                    .size(0L)
                    .hitCount(0L)
                    .missCount(0L)
                    .hitRate(0.0)
                    .evictionCount(0L)
                    .lastEvictedAt(Instant.EPOCH.toString())
                    .build();
              }
            })
        .collect(Collectors.toList());
  }

  /** Evict all entries from a named cache with throttle protection */
  @Transactional
  public CacheEvictResponse evictCache(String cacheName, Long actorId) {
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
        null);

    // Publish event for WebSocket push
    String evictedAt = Instant.now().toString();
    eventPublisher.publishEvent(new CacheEvictedEvent(this, cacheName, evictedAt, 0));

    log.info("Cache evicted: {} by system admin ({})", cacheName, actorId);

    return CacheEvictResponse.builder().cacheName(cacheName).evictedAt(evictedAt).build();
  }

  /** Get whitelisted configuration properties only (no secrets) */
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

  /** Get security events from audit log */
  public List<SecurityEventDto> getSecurityEvents(int limit) {
    List<AuditLogDto> auditLogs = auditService.getSecurityEvents(limit);
    if (auditLogs == null) {
      return Collections.emptyList();
    }

    return auditLogs.stream()
        .map(
            auditDto ->
                SecurityEventDto.builder()
                    .action(auditDto.getAction())
                    .targetType(auditDto.getEntityType())
                    .targetId(auditDto.getEntityId())
                    .createdAt(auditDto.getCreatedAt())
                    .build())
        .collect(Collectors.toList());
  }

  /** Force logout a user by revoking all their tokens */
  @Transactional
  public ForceLogoutResponse forceLogout(String targetHashId, Long actorId, String reason) {
    // Decode target user
    Long targetUserId = hashidService.decode(targetHashId);

    // Fetch target user
    User targetUser =
        userRepository
            .findById(targetUserId)
            .orElseThrow(
                () -> new com.reviewflow.shared.exception.ResourceNotFoundException("User", targetHashId));

    // Prevent self-logout
    if (targetUserId.equals(actorId)) {
      throw new ForceLogoutBlockedException();
    }

    // Count and delete all refresh tokens
    int revokedTokenCount = refreshTokenRepository.revokeAllForUser(targetUserId);

    // Bump token version for immediate access token invalidation
    userRepository.incrementTokenVersion(targetUserId);
    tokenVersionService.invalidate(targetUserId);

    // Log to audit
    auditService.log(
        actorId,
        "FORCE_LOGOUT",
        "USER",
        targetUserId,
        Map.of(
            "reason", reason,
            "revokedTokenCount", revokedTokenCount,
            "tokenVersionBumped", true,
            "newTokenVersion", targetUser.getTokenVersion()),
        null);

    // Publish event
    eventPublisher.publishEvent(
        new ForceLogoutEvent(this, targetUserId, targetUser.getEmail(), reason, revokedTokenCount));

    log.info(
        "User force logged out: {} (tokens revoked: {}, version bumped: true)",
        targetUser.getEmail(),
        revokedTokenCount);

    return ForceLogoutResponse.builder()
        .userId(hashidService.encode(targetUserId))
        .revokedTokenCount(revokedTokenCount)
        .build();
  }

  /** Unlock a team (system override) */
  @Transactional
  public UnlockTeamResponse unlockTeam(String teamHashId, Long actorId, String reason) {
    // Decode team ID
    Long teamId = hashidService.decode(teamHashId);

    // Fetch team
    Team team =
        teamRepository.findById(teamId).orElseThrow(() -> new TeamNotFoundException(teamHashId));

    // Update team (idempotent)
    team.setIsLocked(false);
    teamRepository.save(team);

    // Log to audit
    auditService.log("SYSTEM_TEAM_UNLOCKED", "TEAM", teamId, Map.of("reason", reason), null);

    // Fetch team members for event
    List<UserDto> teamMembers =
        team.getMembers().stream()
            .map(
                m ->
                    UserDto.builder()
                        .id(hashidService.encode(m.getUser().getId()))
                        .email(m.getUser().getEmail())
                        .firstName(m.getUser().getFirstName())
                        .lastName(m.getUser().getLastName())
                        .build())
            .collect(Collectors.toList());

    // Publish event
    eventPublisher.publishEvent(
        new TeamUnlockedBySystemEvent(this, teamId, team.getName(), teamMembers, reason));

    log.info("Team unlocked by system admin: {} (reason: {})", team.getName(), reason);

    return UnlockTeamResponse.builder()
        .teamId(hashidService.encode(teamId))
        .isLocked(false)
        .build();
  }

  /** Reopen a published evaluation (system override) */
  @Transactional
  public ReopenEvaluationResponse reopenEvaluation(
      String evaluationHashId, Long actorId, String reason) {
    // Decode evaluation ID
    Long evaluationId = hashidService.decode(evaluationHashId);

    // Fetch evaluation
    Evaluation evaluation =
        evaluationRepository
            .findById(evaluationId)
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
        null);

    // Fetch instructor and team member emails for event
    Team team = evaluation.getSubmission().getTeam();
    String instructorEmail = evaluation.getInstructor().getEmail();
    List<String> teamMemberEmails =
        team.getMembers().stream().map(m -> m.getUser().getEmail()).collect(Collectors.toList());

    // Publish event
    eventPublisher.publishEvent(
        new EvaluationReopenedEvent(
            this,
            evaluationId,
            team.getId(),
            instructorEmail,
            teamMemberEmails,
            scoringSnapshot,
            reason));

    log.info("Evaluation reopened by system admin: (reason: {})", reason);

    return ReopenEvaluationResponse.builder()
        .evaluationId(hashidService.encode(evaluationId))
        .isDraft(true)
        .build();
  }

  /** Collect and push system metrics to all SYSTEM_ADMIN users via WebSocket */
  public void collectAndPushMetrics() {
    try {
      // Collect metrics
      Runtime runtime = Runtime.getRuntime();
      long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024); // MB
      long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB
      int threadCount = Thread.activeCount();
      double cpuUsage =
          java.lang.management.ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
      if (cpuUsage < 0) {
        cpuUsage = 0.0; // System load average not available
      }
      SystemMetricsDto metricsDto =
          SystemMetricsDto.builder()
              .jvm(
                  SystemMetricsDto.JvmMetrics.builder()
                      .usedMemory(usedMemory)
                      .maxMemory(maxMemory)
                      .cpuUsage(cpuUsage)
                      .threadCount(threadCount)
                      .build())
              .db(buildDbMetrics())
              .cache(buildCacheMetrics())
              .uptimeSeconds(
                  java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
              .recentSecurityEvents(getRecentSecurityEvents())
              .timestamp(
                  Instant.now()
                      .atZone(ZoneId.systemDefault())
                      .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
              .build();

      // Find all SYSTEM_ADMIN users
      Page<User> adminPage =
          userRepository.findByRoleAndIsActive(
              UserRole.SYSTEM_ADMIN, true, PageRequest.of(0, 1000));
      List<User> systemAdmins =
          adminPage != null ? adminPage.getContent() : Collections.emptyList();

      // Send to each SYSTEM_ADMIN user
      for (User admin : systemAdmins) {
        messagingTemplate.convertAndSendToUser(
            admin.getId().toString(), "/queue/system-metrics", metricsDto);
      }

      log.debug("System metrics pushed to {} SYSTEM_ADMIN users", systemAdmins.size());
    } catch (Exception e) {
      log.error("Error collecting and pushing system metrics", e);
    }
  }

  private SystemMetricsDto.DbMetrics buildDbMetrics() {
    HikariPoolMXBean pool = getHikariMXBean();
    int active = pool != null ? pool.getActiveConnections() : -1;
    int idle = pool != null ? pool.getIdleConnections() : -1;
    int max = pool != null ? pool.getTotalConnections() : -1;
    return SystemMetricsDto.DbMetrics.builder()
        .activeConnections(active)
        .idleConnections(idle)
        .maxConnections(max)
        .build();
  }

  private SystemMetricsDto.CacheMetrics buildCacheMetrics() {
    return SystemMetricsDto.CacheMetrics.builder()
        .adminStatsHitRate(safeRate(getCacheHitRate(CACHE_ADMIN_STATS)))
        .unreadCountHitRate(safeRate(getCacheHitRate(CACHE_UNREAD_COUNT)))
        .userCoursesHitRate(safeRate(getCacheHitRate(CACHE_USER_COURSES)))
        .assignmentDetailHitRate(safeRate(getCacheHitRate(CACHE_ASSIGNMENT)))
        .build();
  }

  private HikariPoolMXBean getHikariMXBean() {
    if (dataSource instanceof HikariDataSource hikari) {
      return hikari.getHikariPoolMXBean();
    }
    return null;
  }

  private double getCacheHitRate(String cacheName) {
    org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
    if (cache == null) {
      return -1.0;
    }
    Object nativeCache = cache.getNativeCache();
    if (nativeCache instanceof Cache<?, ?> caffeine) {
      com.github.benmanes.caffeine.cache.stats.CacheStats stats = caffeine.stats();
      return stats != null ? stats.hitRate() : -1.0;
    }
    return -1.0;
  }

  private double safeRate(double rate) {
    if (rate < 0) {
      return rate;
    }
    return Double.isNaN(rate) || Double.isInfinite(rate) ? 0.0 : rate;
  }

  private int getRecentSecurityEvents() {
    try {
      Counter failedLogins =
          meterRegistry.find("reviewflow.security.login").tag("result", "failed").counter();
      Counter lockouts = meterRegistry.find("reviewflow.security.lockout").counter();
      double total =
          (failedLogins != null ? failedLogins.count() : 0)
              + (lockouts != null ? lockouts.count() : 0);
      return (int) Math.round(total);
    } catch (Exception e) {
      return -1;
    }
  }

  /** PRD-18: SYSTEM_ADMIN moderation — list conversations in a course. */
  public java.util.Map<String, Object> moderationListCourseConversations(
      Long courseId, Long actorId, String ip) {
    return systemMessagingService.getCourseConversationsForApi(courseId, actorId, ip);
  }

  /** PRD-18: SYSTEM_ADMIN moderation — full message history including soft-deleted. */
  public org.springframework.data.domain.Page<com.reviewflow.messaging.dto.response.MessageDto>
      moderationListConversationMessages(
          Long conversationId, Long actorId, String ip, int page, int size) {
    return systemMessagingService.getConversationMessagesForApi(
        conversationId, actorId, ip, page, size);
  }

  /** Build a JSON snapshot of evaluation scores for audit trail */
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
