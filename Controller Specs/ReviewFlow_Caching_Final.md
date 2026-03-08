# ReviewFlow — Caching Implementation Guide
> Package: `com.reviewflow` | Spring Boot 4 | Caffeine In-Memory Cache | Spring Cache Abstraction
> Tailored to your exact service structure. Single-server deployment with clear Redis upgrade path.
> Follow every step in order. Incorrect eviction is worse than no cache at all.

---

## Architecture Decision Record

### Why Caffeine
ReviewFlow is a single-server deployment. Caffeine is:
- **In-process** — zero network hop, microsecond latency
- **Near-optimal eviction** — uses Window TinyLFU algorithm, outperforms LRU
- **Zero infrastructure** — no extra server to manage, monitor, or secure
- **Drop-in replaceable** — when you scale to multiple servers, swap Caffeine
  for Redis by changing one dependency and one config class. Every `@Cacheable`
  and `@CacheEvict` annotation across all services stays completely unchanged.

### What Gets Cached and Why

| Cache | Why |
|---|---|
| `adminStats` | Queries every table — expensive. Data changes infrequently. |
| `unreadCount:{userId}` | Called on every page load by every active user. Must be instant. |
| `userCourses:{userId}` | Called on every dashboard load. Course membership rarely changes. |
| `assignmentDetail:{id}` | Rubric criteria never change after publish. Fetched on every student page load. |

### What Does NOT Get Cached
- `AuthService` — login, logout, token operations must always be live
- `TeamService` — member list changes frequently via invites and accepts
- `EvaluationService` — draft state and publish state change regularly
- `RateLimiterService` — rate limit counters must never be stale
- `FileSecurityValidator` — security checks must always run fresh
- `AuditService` — append-only, no reads to cache
- Any write endpoint — never cache mutations

### Note on NotificationService.create()
Your `create()` method is kept but unused — all notification creation now
goes through the event listener. You have two options:
- **Keep it** — harmless, may be useful for future admin-triggered notifications
- **Remove it** — cleaner codebase, forces all notifications through the event system

Either choice does not affect caching. This document assumes you keep it.

---

## Implementation Order

```
Step 1  → Add dependencies
Step 2  → Add @EnableCaching to main class
Step 3  → Create CacheConfig
Step 4  → Create AdminStatsDto
Step 5  → Create AdminStatsService (cache 1 — new class)
Step 6  → Wire adminStats eviction into CourseService, SubmissionService, TeamService, UserService
Step 7  → Add unreadCount caching to NotificationService (cache 2)
Step 8  → Update NotificationEventListener to evict on new notification
Step 9  → Add userCourses caching to CourseService (cache 3)
Step 10 → Add assignmentDetail caching to AssignmentService (cache 4)
Step 11 → Add missing repository methods
Step 12 → Expose cache metrics via Actuator
Step 13 → Verification
```

---

## Step 1 — Add Dependencies

Add to `pom.xml`:

```xml
<!-- Spring Cache abstraction -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<!-- Caffeine — high performance in-memory cache -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

✅ Save and confirm Maven downloads cleanly and the project still compiles.

---

## Step 2 — Enable Caching on Main Class

Add `@EnableCaching` alongside your existing annotations:

```java
// src/main/java/com/reviewflow/ReviewFlowApplication.java

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching      // ADD THIS
public class ReviewFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewFlowApplication.class, args);
    }
}
```

---

## Step 3 — CacheConfig

Each cache gets its own TTL and max size. Never use a single global config —
different data has different staleness tolerances.

The `public static final` constants are critical — use them everywhere instead
of raw strings so typos cause compile errors rather than silent cache misses.

```java
// src/main/java/com/reviewflow/config/CacheConfig.java

package com.reviewflow.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    // Use these constants everywhere — never raw strings
    public static final String CACHE_ADMIN_STATS  = "adminStats";
    public static final String CACHE_UNREAD_COUNT = "unreadCount";
    public static final String CACHE_USER_COURSES = "userCourses";
    public static final String CACHE_ASSIGNMENT   = "assignmentDetail";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(

            // Admin stats — expensive multi-table aggregate query
            // TTL: 60s | Max: 1 entry (only one global stats object ever exists)
            buildCache(CACHE_ADMIN_STATS, 60, 1),

            // Unread notification count — one entry per active user
            // TTL: 30s | Short because it is also evicted immediately on new notification
            buildCache(CACHE_UNREAD_COUNT, 30, 500),

            // Course list — one entry per active user
            // TTL: 5 min | Evicted on enrollment change or course archive
            buildCache(CACHE_USER_COURSES, 300, 500),

            // Assignment detail + rubric — one entry per assignment
            // TTL: 10 min | Evicted when instructor edits assignment or rubric
            buildCache(CACHE_ASSIGNMENT, 600, 200)

        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, int ttlSeconds, int maxSize) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .recordStats() // enables hit/miss metrics via /actuator/metrics
                .build()
        );
    }
}
```

✅ Run the app. Confirm it starts cleanly with no cache-related errors.

---

## Step 4 — AdminStatsDto

```java
// src/main/java/com/reviewflow/model/dto/response/AdminStatsDto.java

package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminStatsDto {
    long          totalUsers;
    RoleBreakdown usersByRole;
    long          totalCourses;
    long          activeCourses;
    long          archivedCourses;
    long          totalAssignments;
    long          publishedAssignments;
    long          totalTeams;
    long          totalSubmissions;
    long          storageUsedBytes;
    String        storageUsedFormatted;

    @Value
    @Builder
    public static class RoleBreakdown {
        long students;
        long instructors;
        long admins;
    }
}
```

---

## Step 5 — AdminStatsService (new class)

You do not have this service yet — create it from scratch.
The `evictStats()` method is intentionally empty — `@CacheEvict` does the work.
Other services call it after data-changing operations (wired in Step 6).

```java
// src/main/java/com/reviewflow/service/AdminStatsService.java

package com.reviewflow.service;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.model.dto.response.AdminStatsDto;
import com.reviewflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository       userRepository;
    private final CourseRepository     courseRepository;
    private final AssignmentRepository assignmentRepository;
    private final TeamRepository       teamRepository;
    private final SubmissionRepository submissionRepository;

    @Cacheable(value = CacheConfig.CACHE_ADMIN_STATS, key = "'global'")
    @Transactional(readOnly = true)
    public AdminStatsDto getSystemStats() {
        long totalUsers           = userRepository.count();
        long totalStudents        = userRepository.countByRole("STUDENT");
        long totalInstructors     = userRepository.countByRole("INSTRUCTOR");
        long totalAdmins          = userRepository.countByRole("ADMIN");
        long totalCourses         = courseRepository.count();
        long activeCourses        = courseRepository.countByIsArchivedFalse();
        long archivedCourses      = courseRepository.countByIsArchivedTrue();
        long totalAssignments     = assignmentRepository.count();
        long publishedAssignments = assignmentRepository.countByIsPublishedTrue();
        long totalTeams           = teamRepository.count();
        long totalSubmissions     = submissionRepository.count();
        long storageBytes         = submissionRepository.sumFileSizeBytes();

        return AdminStatsDto.builder()
                .totalUsers(totalUsers)
                .usersByRole(AdminStatsDto.RoleBreakdown.builder()
                        .students(totalStudents)
                        .instructors(totalInstructors)
                        .admins(totalAdmins)
                        .build())
                .totalCourses(totalCourses)
                .activeCourses(activeCourses)
                .archivedCourses(archivedCourses)
                .totalAssignments(totalAssignments)
                .publishedAssignments(publishedAssignments)
                .totalTeams(totalTeams)
                .totalSubmissions(totalSubmissions)
                .storageUsedBytes(storageBytes)
                .storageUsedFormatted(formatBytes(storageBytes))
                .build();
    }

    // Called by other services after data-changing operations
    // Method body is intentionally empty — @CacheEvict does the work
    @CacheEvict(value = CacheConfig.CACHE_ADMIN_STATS, key = "'global'")
    public void evictStats() {}

    private String formatBytes(long bytes) {
        if (bytes < 1024)               return bytes + " B";
        if (bytes < 1024 * 1024)        return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB",  bytes / (1024.0 * 1024 * 1024));
    }
}
```

---

## Step 6 — Wire adminStats Eviction Into Your Services

Add `adminStatsService.evictStats()` as the last line of each method listed.
These are the methods in your existing services that change data counted in stats.

### Add field to each service listed below:
```java
private final AdminStatsService adminStatsService;
```

### CourseService
```java
// In createCourse() — last line:
adminStatsService.evictStats();

// In archiveCourse() — last line (covers both archive and unarchive):
adminStatsService.evictStats();
```

### SubmissionService
```java
// In upload() — last line after saving submission:
adminStatsService.evictStats();
```

### TeamService
```java
// In createTeam() — last line after saving team:
adminStatsService.evictStats();
```

### UserService
```java
// In createUser() — last line:
adminStatsService.evictStats();

// In deactivateUser() — last line:
adminStatsService.evictStats();

// In reactivateUser() — last line:
adminStatsService.evictStats();
```

> **Note on your other services:**
> `AuthService` handles login/logout — does not change user counts, no eviction needed.
> `AuditService` is append-only — audit entries are not counted in stats, no eviction needed.
> `RateLimiterService` and `FileSecurityValidator` do not affect any cached data.

---

## Step 7 — Add unreadCount Caching to NotificationService

Update your existing `NotificationService`. Only three methods change.
Everything else — including `create()`, `deleteNotification()`, and
`getNotifications()` — stays exactly as-is.

```java
// Add imports to NotificationService:
import com.reviewflow.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

// ── Replace getUnreadCount: ───────────────────────────────────────
@Cacheable(value = CacheConfig.CACHE_UNREAD_COUNT, key = "#userId")
@Transactional(readOnly = true)
public long getUnreadCount(Long userId) {
    return notificationRepository.countByUserIdAndIsReadFalse(userId);
}

// ── Replace markAsRead: ───────────────────────────────────────────
@CacheEvict(value = CacheConfig.CACHE_UNREAD_COUNT, key = "#userId")
@Transactional
public void markAsRead(Long id, Long userId) {
    Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", id));

    if (!notification.getUserId().equals(userId)) {
        throw new AccessDeniedException("Not authorized to access this notification");
    }

    notification.setIsRead(true);
    notificationRepository.save(notification);
}

// ── Replace markAllAsRead: ────────────────────────────────────────
@CacheEvict(value = CacheConfig.CACHE_UNREAD_COUNT, key = "#userId")
@Transactional
public int markAllAsRead(Long userId) {
    return notificationRepository.markAllReadByUserId(userId);
}
```

> **Note on create():** Does NOT get `@CacheEvict` here. Eviction when a
> new notification is created is handled inside the event listener in Step 8,
> which is where notifications are actually persisted. If you ever start
> using `create()` again in future, add
> `@CacheEvict(value = CacheConfig.CACHE_UNREAD_COUNT, key = "#userId")`
> to it at that point.

---

## Step 8 — Evict unreadCount From NotificationEventListener

When the event listener saves a new notification, the cached unread count
for that user is now stale. Evict it immediately so the next call to
`GET /notifications/unread-count` returns the correct incremented value.

Update `NotificationEventListener.java`:

```java
// Add field:
private final CacheManager cacheManager;

// Update the saveAndPush helper — add eviction after notificationRepository.save():
private void saveAndPush(Long userId, NotificationType type,
                          String title, String message, String actionUrl) {

    Notification notification = Notification.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .message(message)
            .actionUrl(actionUrl)
            .isRead(false)
            .build();

    notificationRepository.save(notification);

    // Evict stale unread count for this user — it just increased by 1
    var cache = cacheManager.getCache(CacheConfig.CACHE_UNREAD_COUNT);
    if (cache != null) cache.evict(userId);

    // Push via WebSocket — offline users receive via GET /notifications on next load
    try {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications",
                NotificationDto.from(notification)
        );
        log.debug("Pushed {} to user {}", type, userId);
    } catch (Exception e) {
        log.debug("User {} offline — {} saved to DB only", userId, type);
    }
}
```

Add import:
```java
import org.springframework.cache.CacheManager;
```

---

## Step 9 — Add userCourses Caching to CourseService

Update your existing `CourseService`. Add annotations to these methods.
Your existing logic inside each method does not change:

```java
// Add imports to CourseService:
import com.reviewflow.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

// ── Cache the role-filtered course list per user ──────────────────
@Cacheable(value = CacheConfig.CACHE_USER_COURSES, key = "#userId")
@Transactional(readOnly = true)
public List<CourseDto> getCoursesForUser(Long userId, String role) {
    // your existing logic unchanged
}

// ── Evict the specific student's cache on enroll ─────────────────
@CacheEvict(value = CacheConfig.CACHE_USER_COURSES, key = "#studentId")
@Transactional
public void enrollStudent(Long courseId, Long studentId) {
    // your existing logic unchanged
    adminStatsService.evictStats(); // already added in Step 6
}

// ── Evict the specific student's cache on unenroll ───────────────
@CacheEvict(value = CacheConfig.CACHE_USER_COURSES, key = "#studentId")
@Transactional
public void unenrollStudent(Long courseId, Long studentId) {
    // your existing logic unchanged
}

// ── Evict ALL entries when a course is archived ───────────────────
// allEntries = true because archiving affects every enrolled student
// and every instructor assigned to that course
@CacheEvict(value = CacheConfig.CACHE_USER_COURSES, allEntries = true)
@Transactional
public CourseDto archiveCourse(Long courseId) {
    // your existing logic unchanged
    adminStatsService.evictStats(); // already added in Step 6
}

// ── Evict ALL entries when instructor is assigned or removed ──────
// Affects the instructor's own course list
@CacheEvict(value = CacheConfig.CACHE_USER_COURSES, allEntries = true)
@Transactional
public void assignInstructor(Long courseId, Long instructorId) {
    // your existing logic unchanged
}

@CacheEvict(value = CacheConfig.CACHE_USER_COURSES, allEntries = true)
@Transactional
public void removeInstructor(Long courseId, Long instructorId) {
    // your existing logic unchanged
}
```

---

## Step 10 — Add assignmentDetail Caching to AssignmentService

Update your existing `AssignmentService`. Add annotations to these methods.
Your existing logic inside each method does not change:

```java
// Add imports to AssignmentService:
import com.reviewflow.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

// ── Cache full assignment + rubric by assignment ID ───────────────
@Cacheable(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
@Transactional(readOnly = true)
public AssignmentDto getAssignmentById(Long assignmentId, Long requestingUserId) {
    // your existing logic unchanged
}

// ── Evict on any change to the assignment itself ──────────────────
@CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
@Transactional
public AssignmentDto updateAssignment(Long assignmentId,
                                      UpdateAssignmentRequest request) {
    // your existing logic unchanged
}

@CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
@Transactional
public AssignmentDto togglePublish(Long assignmentId) {
    // your existing logic unchanged
}

// ── Evict on any rubric change ────────────────────────────────────
@CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
@Transactional
public RubricCriterionDto addCriterion(Long assignmentId,
                                        CreateCriterionRequest request) {
    // your existing logic unchanged
}

@CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
@Transactional
public RubricCriterionDto updateCriterion(Long assignmentId, Long criterionId,
                                           UpdateCriterionRequest request) {
    // your existing logic unchanged
}

@CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
@Transactional
public void deleteCriterion(Long assignmentId, Long criterionId) {
    // your existing logic unchanged
}
```

---

## Step 11 — Missing Repository Methods

Add any of these that do not already exist. Check before adding — you may have some already:

### UserRepository
```java
long countByRole(String role);
```

### CourseRepository
```java
long countByIsArchivedFalse();
long countByIsArchivedTrue();
```

### AssignmentRepository
```java
long countByIsPublishedTrue();
```

### SubmissionRepository
```java
import org.springframework.data.jpa.repository.Query;

// COALESCE handles the case where the table is empty — returns 0 not null
@Query("SELECT COALESCE(SUM(s.fileSizeBytes), 0) FROM Submission s")
long sumFileSizeBytes();
```

---

## Step 12 — Expose Cache Metrics via Actuator

Caffeine records hit and miss stats automatically via `recordStats()` set in Step 3.

### In `application-local.properties` — add:
```properties
management.endpoints.web.exposure.include=health,info,metrics,caches
management.endpoint.caches.enabled=true
```

### Useful endpoints after startup:
```
# List all active caches and their current sizes
GET http://localhost:8081/actuator/caches

# Hit rate, miss rate, eviction count per cache
GET http://localhost:8081/actuator/metrics/cache.gets?tag=name:adminStats
GET http://localhost:8081/actuator/metrics/cache.gets?tag=name:unreadCount
GET http://localhost:8081/actuator/metrics/cache.gets?tag=name:userCourses
GET http://localhost:8081/actuator/metrics/cache.gets?tag=name:assignmentDetail
```

A healthy cache shows a hit rate above 80%.
Below 50% consistently means the TTL is too short or eviction is too aggressive.

---

## Step 13 — Verification

### Enable SQL logging temporarily
Add to `application-local.properties`:
```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```
No SQL in console on second call = cache HIT.
SQL fires = cache MISS or eviction working correctly.
Remove these lines after verification.

### Postman test sequence

**adminStats:**
```
GET  /api/v1/admin/stats     → SQL fires                     (cache MISS — first call)
GET  /api/v1/admin/stats     → no SQL                        (cache HIT)
POST /api/v1/submissions     → upload a file
GET  /api/v1/admin/stats     → SQL fires again               (evicted by SubmissionService)
POST /api/v1/admin/users     → create a user
GET  /api/v1/admin/stats     → SQL fires again               (evicted by UserService)
```

**unreadCount:**
```
GET  /api/v1/notifications/unread-count  → SQL fires         (cache MISS)
GET  /api/v1/notifications/unread-count  → no SQL            (cache HIT)

(Invite a student to a team — triggers TEAM_INVITE event)
GET  /api/v1/notifications/unread-count  → SQL fires, count incremented
                                           (evicted by NotificationEventListener)

PATCH /api/v1/notifications/{id}/read
GET   /api/v1/notifications/unread-count → SQL fires, count decremented
                                           (evicted by markAsRead)

PATCH /api/v1/notifications/read-all
GET   /api/v1/notifications/unread-count → SQL fires, returns 0
                                           (evicted by markAllAsRead)
```

**userCourses:**
```
GET  /api/v1/courses                     → SQL fires         (cache MISS)
GET  /api/v1/courses                     → no SQL            (cache HIT)
POST /api/v1/courses/{id}/enroll         → enroll a student
GET  /api/v1/courses  (as that student)  → SQL fires         (evicted for that user only)
```

**assignmentDetail:**
```
GET  /api/v1/assignments/{id}            → SQL fires         (cache MISS)
GET  /api/v1/assignments/{id}            → no SQL            (cache HIT)
POST /api/v1/assignments/{id}/rubric     → add a criterion
GET  /api/v1/assignments/{id}            → SQL fires, new criterion visible
                                           (evicted by addCriterion)
PATCH /api/v1/assignments/{id}/publish
GET   /api/v1/assignments/{id}           → SQL fires, isPublished updated
                                           (evicted by togglePublish)
```

### Full verification checklist

- [ ] App starts with no errors after adding dependencies
- [ ] `GET /actuator/caches` returns 4 named caches
- [ ] `GET /actuator/health` still returns UP
- [ ] adminStats: second call hits cache (no SQL)
- [ ] adminStats: evicted after submission upload
- [ ] adminStats: evicted after user created
- [ ] adminStats: evicted after course created
- [ ] adminStats: evicted after course archived
- [ ] adminStats: evicted after team created
- [ ] unreadCount: second call hits cache
- [ ] unreadCount: evicted when new notification created via event listener
- [ ] unreadCount: evicted when markAsRead called
- [ ] unreadCount: evicted when markAllAsRead called
- [ ] userCourses: second call hits cache
- [ ] userCourses: evicted when student enrolled
- [ ] userCourses: evicted when student unenrolled
- [ ] userCourses: all entries evicted when course archived
- [ ] userCourses: all entries evicted when instructor assigned or removed
- [ ] assignmentDetail: second call hits cache
- [ ] assignmentDetail: evicted when assignment updated
- [ ] assignmentDetail: evicted when published or unpublished
- [ ] assignmentDetail: evicted when rubric criterion added
- [ ] assignmentDetail: evicted when rubric criterion updated
- [ ] assignmentDetail: evicted when rubric criterion deleted

---

## Eviction Reference — Complete Map

### `adminStats` cache

| Evict when | Service | Method |
|---|---|---|
| New user created | `UserService` | `createUser()` |
| User deactivated | `UserService` | `deactivateUser()` |
| User reactivated | `UserService` | `reactivateUser()` |
| New course created | `CourseService` | `createCourse()` |
| Course archived or unarchived | `CourseService` | `archiveCourse()` |
| Submission uploaded | `SubmissionService` | `upload()` |
| New team created | `TeamService` | `createTeam()` |

### `unreadCount:{userId}` cache

| Evict when | Where | How |
|---|---|---|
| New notification saved | `NotificationEventListener.saveAndPush()` | `cacheManager.getCache().evict(userId)` |
| Notification marked read | `NotificationService.markAsRead()` | `@CacheEvict` |
| All notifications marked read | `NotificationService.markAllAsRead()` | `@CacheEvict` |

### `userCourses:{userId}` cache

| Evict when | Service method | Scope |
|---|---|---|
| Student enrolled | `CourseService.enrollStudent()` | evict `studentId` only |
| Student unenrolled | `CourseService.unenrollStudent()` | evict `studentId` only |
| Course archived or updated | `CourseService.archiveCourse()` | evict `allEntries` |
| Instructor assigned | `CourseService.assignInstructor()` | evict `allEntries` |
| Instructor removed | `CourseService.removeInstructor()` | evict `allEntries` |

### `assignmentDetail:{assignmentId}` cache

| Evict when | Service method |
|---|---|
| Assignment updated | `AssignmentService.updateAssignment()` |
| Assignment published or unpublished | `AssignmentService.togglePublish()` |
| Rubric criterion added | `AssignmentService.addCriterion()` |
| Rubric criterion updated | `AssignmentService.updateCriterion()` |
| Rubric criterion deleted | `AssignmentService.deleteCriterion()` |

### Services with NO caching involvement

| Service | Why |
|---|---|
| `AuthService` | Login/logout — must always be live |
| `AuditService` | Append-only — no reads to cache |
| `RateLimiterService` | Counters must never be stale |
| `FileSecurityValidator` | Security checks must always run fresh |
| `EvaluationService` | Draft/publish state changes too frequently |
| `TeamService` (reads) | Member list changes frequently via invites |
| `NotificationService.create()` | Unused — not wired to any cache |

---

## Redis Upgrade Path

When you scale to multiple servers make exactly these two changes and nothing else.
All `@Cacheable` and `@CacheEvict` annotations remain completely unchanged.

### 1. Replace dependencies in `pom.xml`:
```xml
<!-- Remove: spring-boot-starter-cache and caffeine -->
<!-- Add: -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 2. Replace `CacheConfig.java` entirely:
```java
@Configuration
public class CacheConfig {

    // Constants stay identical — nothing else in the codebase changes
    public static final String CACHE_ADMIN_STATS  = "adminStats";
    public static final String CACHE_UNREAD_COUNT = "unreadCount";
    public static final String CACHE_USER_COURSES = "userCourses";
    public static final String CACHE_ASSIGNMENT   = "assignmentDetail";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put(CACHE_ADMIN_STATS,  ttl(60));
        configs.put(CACHE_UNREAD_COUNT, ttl(30));
        configs.put(CACHE_USER_COURSES, ttl(300));
        configs.put(CACHE_ASSIGNMENT,   ttl(600));

        return RedisCacheManager.builder(factory)
                .withInitialCacheConfigurations(configs)
                .build();
    }

    private RedisCacheConfiguration ttl(int seconds) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(seconds));
    }
}
```

### 3. Add to `application.properties`:
```properties
spring.data.redis.host=your-redis-host
spring.data.redis.port=6379
```
