package com.reviewflow.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.reviewflow.model.entity.*;
import com.reviewflow.exception.*;
import com.reviewflow.repository.*;
import com.reviewflow.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemService Unit Tests")
class SystemServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AuditService auditService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private HashidService hashidService;

    @Mock
    private Environment environment;

    @InjectMocks
    private SystemService systemService;

    @BeforeEach
    void setUp() {
        // Using @InjectMocks, no manual setup needed
    }

    // ────────────────────────────────────────────────────────────
    // CACHE STATISTICS TESTS
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("getCacheStats() returns list of cache stats with size")
    void testGetCacheStats_Success() {
        // Arrange
        Cache<Object, Object> mockCaffeineCache = mock(Cache.class);
        when(mockCaffeineCache.estimatedSize()).thenReturn(50L);

        org.springframework.cache.Cache mockSpringCache = mock(org.springframework.cache.Cache.class);
        when(mockSpringCache.getNativeCache()).thenReturn(mockCaffeineCache);

        when(cacheManager.getCache("adminStats")).thenReturn(mockSpringCache);
        when(cacheManager.getCache("unreadCount")).thenReturn(mockSpringCache);
        when(cacheManager.getCache("userCourses")).thenReturn(mockSpringCache);
        when(cacheManager.getCache("assignmentDetail")).thenReturn(mockSpringCache);

        // Act
        List<CacheStatsDto> stats = systemService.getCacheStats();

        // Assert
        assertNotNull(stats, "Stats should not be null");
        assertEquals(4, stats.size(), "Should return 4 cache stats");
        CacheStatsDto firstStat = stats.get(0);
        assertNotNull(firstStat.getName(), "Cache name should be set");
        assertEquals(50L, firstStat.getSize(), "Cache size should be 50");
        assertEquals(0.0, firstStat.getHitRate(), "Hit rate should be 0.0");
    }

    // ────────────────────────────────────────────────────────────
    // CACHE EVICTION TESTS
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("evictCache() successfully clears cache and logs audit")
    void testEvictCache_Success() {
        // Arrange
        org.springframework.cache.Cache mockSpringCache = mock(org.springframework.cache.Cache.class);
        when(cacheManager.getCache("adminStats")).thenReturn(mockSpringCache);

        // Act
        Map<String, Object> result = systemService.evictCache("adminStats", 1L);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals("adminStats", result.get("cacheName"), "Cache name should match");
        verify(mockSpringCache, times(1)).clear();
        verify(auditService, times(1)).log(eq("CACHE_EVICTED"), eq("CACHE"), isNull(), any(Map.class), isNull());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("evictCache() throws UnknownCacheException for invalid cache name")
    void testEvictCache_UnknownCache() {
        // Act & Assert
        assertThrows(UnknownCacheException.class, ()
                -> systemService.evictCache("invalidCache", 1L),
                "Should throw UnknownCacheException for unknown cache"
        );
    }

    @Test
    @DisplayName("evictCache() returns response with cacheName and timestamp")
    void testEvictCache_ResponseStructure() {
        // Arrange
        org.springframework.cache.Cache mockSpringCache = mock(org.springframework.cache.Cache.class);
        when(cacheManager.getCache("unreadCount")).thenReturn(mockSpringCache);

        // Act
        Map<String, Object> result = systemService.evictCache("unreadCount", 1L);

        // Assert - Verify response structure
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("cacheName"), "Should have cacheName");
        assertTrue(result.containsKey("evictedAt"), "Should have evictedAt");
        assertEquals("unreadCount", result.get("cacheName"));
        assertNotNull(result.get("evictedAt"));
    }

    // ────────────────────────────────────────────────────────────
    // SAFE CONFIG TESTS
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("getSafeConfig() returns whitelisted properties without secrets")
    void testGetSafeConfig_Success() {
        // Arrange - Use lenient stubbing for any property call
        lenient().when(environment.getProperty(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if ("server.port".equals(key)) {
                return "8080";
            }
            if ("spring.application.name".equals(key)) {
                return "ReviewFlow";
            }
            return null;
        });

        // Act
        Map<String, String> config = systemService.getSafeConfig();

        // Assert
        assertNotNull(config, "Config should not be null");
        // Verify no secrets in config
        config.forEach((key, value)
                -> assertFalse(key.toLowerCase().contains("secret")
                        || key.toLowerCase().contains("password")
                        || key.toLowerCase().contains("token"),
                        "Config should not contain secrets: " + key)
        );
    }

    // ────────────────────────────────────────────────────────────
    // SECURITY EVENTS TESTS
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("getSecurityEvents() returns mapped audit events")
    void testGetSecurityEvents_Success() {
        // Arrange
        AuditLogDto auditDto = new AuditLogDto(1L, 1L, "admin@test.com", "USER_LOGIN_FAILED",
                "USER", 5L, "{}", "192.168.1.1", Instant.now());
        when(auditService.getSecurityEvents(10)).thenReturn(Arrays.asList(auditDto));

        // Act
        List<Map<String, Object>> events = systemService.getSecurityEvents(10);

        // Assert
        assertNotNull(events, "Events should not be null");
        assertEquals(1, events.size(), "Should return 1 event");
        assertEquals("USER_LOGIN_FAILED", events.get(0).get("action"));
        assertEquals("USER", events.get(0).get("targetType"));
    }

    // ────────────────────────────────────────────────────────────
    // FORCE LOGOUT TESTS
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("forceLogout() successfully revokes tokens")
    void testForceLogout_Success() {
        // Arrange
        String targetHashId = "target123";
        Long targetUserId = 5L;
        Long actorId = 1L;

        User targetUser = User.builder()
                .id(targetUserId)
                .email("target@test.com")
                .firstName("Target")
                .lastName("User")
                .build();

        when(hashidService.decode(targetHashId)).thenReturn(targetUserId);
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(hashidService.encode(targetUserId)).thenReturn(targetHashId);

        // Act
        Map<String, Object> result = systemService.forceLogout(targetHashId, actorId, "Security risk");

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(targetHashId, result.get("userId"));
        assertEquals(2, result.get("revokedTokenCount"), "Should revoke all tokens");
        verify(refreshTokenRepository, times(1)).revokeAllForUser(targetUserId);
        verify(auditService, times(1)).log(eq("FORCE_LOGOUT"), eq("USER"), eq(targetUserId), any(Map.class), isNull());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("forceLogout() throws ForceLogoutBlockedException on self-logout")
    void testForceLogout_SelfLogout() {
        // Arrange
        String selfHashId = "self123";
        Long actorId = 1L;

        User selfUser = User.builder()
                .id(actorId)
                .email("self@test.com")
                .build();

        when(hashidService.decode(selfHashId)).thenReturn(actorId);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(selfUser));

        // Act & Assert
        assertThrows(ForceLogoutBlockedException.class, ()
                -> systemService.forceLogout(selfHashId, actorId, "reason"),
                "Should prevent self-logout"
        );
    }

    // ────────────────────────────────────────────────────────────
    // UNLOCK TEAM TESTS
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("unlockTeam() clears team lock")
    void testUnlockTeam_Success() {
        // Arrange
        String teamHashId = "team123";
        Long teamId = 10L;
        Long actorId = 1L;

        Team team = Team.builder()
                .id(teamId)
                .name("Team A")
                .isLocked(true)
                .members(new ArrayList<>())
                .build();

        when(hashidService.decode(teamHashId)).thenReturn(teamId);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(hashidService.encode(teamId)).thenReturn(teamHashId);

        // Act
        Map<String, Object> result = systemService.unlockTeam(teamHashId, actorId, "Instructor on leave");

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(teamHashId, result.get("teamId"));
        assertFalse((Boolean) result.get("isLocked"), "Team should be unlocked");
        assertFalse(team.getIsLocked(), "Team object should be unlocked");
        verify(teamRepository, times(1)).save(team);
        verify(auditService, times(1)).log(eq("SYSTEM_TEAM_UNLOCKED"), eq("TEAM"), eq(teamId), any(Map.class), isNull());
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("unlockTeam() throws TeamNotFoundException for unknown team")
    void testUnlockTeam_NotFound() {
        // Arrange
        String teamHashId = "unknown123";
        when(hashidService.decode(teamHashId)).thenReturn(999L);
        when(teamRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(TeamNotFoundException.class, ()
                -> systemService.unlockTeam(teamHashId, 1L, "reason"),
                "Should throw TeamNotFoundException"
        );
    }

    // ────────────────────────────────────────────────────────────
    // REOPEN EVALUATION TESTS
    // ────────────────────────────────────────────────────────────
    @Test
    @DisplayName("reopenEvaluation() sets isDraft to true")
    void testReopenEvaluation_Success() {
        // Arrange
        String evalHashId = "eval123";
        Long evalId = 20L;
        Long actorId = 1L;

        User instructor = User.builder().id(2L).email("instructor@test.com").build();
        Team team = Team.builder().id(10L).name("Team A").members(new ArrayList<>()).build();
        Submission submission = Submission.builder().id(30L).team(team).build();
        Evaluation evaluation = Evaluation.builder()
                .id(evalId)
                .isDraft(false)
                .totalScore(new BigDecimal("85.50"))
                .instructor(instructor)
                .submission(submission)
                .build();

        when(hashidService.decode(evalHashId)).thenReturn(evalId);
        when(evaluationRepository.findById(evalId)).thenReturn(Optional.of(evaluation));
        when(hashidService.encode(evalId)).thenReturn(evalHashId);

        // Act
        Map<String, Object> result = systemService.reopenEvaluation(evalHashId, actorId, "Scoring correction");

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(evalHashId, result.get("evaluationId"));
        assertTrue((Boolean) result.get("isDraft"), "Evaluation should be draft");
        assertTrue(evaluation.getIsDraft(), "Evaluation object should be draft");
        verify(evaluationRepository, times(1)).save(evaluation);
        verify(auditService, atLeastOnce()).log(anyString(), anyString(), anyLong(), any(Map.class), isNull());
        verify(eventPublisher, atLeastOnce()).publishEvent(any());
    }

    @Test
    @DisplayName("reopenEvaluation() throws EvaluationNotFoundException for unknown evaluation")
    void testReopenEvaluation_NotFound() {
        // Arrange
        String evalHashId = "unknown123";
        when(hashidService.decode(evalHashId)).thenReturn(999L);
        when(evaluationRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EvaluationNotFoundException.class, ()
                -> systemService.reopenEvaluation(evalHashId, 1L, "reason"),
                "Should throw EvaluationNotFoundException"
        );
    }
}
