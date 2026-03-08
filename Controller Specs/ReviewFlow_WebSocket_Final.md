# ReviewFlow — WebSocket Implementation Guide
> Package: `com.reviewflow` | Spring Boot 4 | STOMP over SockJS | JWT Cookie Auth
> Follow every step in order. Confirm the app compiles before moving to the next step.

---

## Pre-Implementation Analysis

Based on your existing code, here is exactly what needs to change before starting:

### SecurityConfig — one line to add
Your `/ws/**` endpoint must be permitted before authentication.
The STOMP handshake happens before the JWT interceptor runs:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/ws/**").permitAll()  // ADD THIS
    .anyRequest().authenticated())
```

### NotificationRepository — one method to add
```java
@Modifying
@Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
void deleteOlderThan(@Param("cutoff") Instant cutoff);
```

### NotificationService — one method to add
```java
@Transactional
public void deleteOlderThan(Instant cutoff) {
    notificationRepository.deleteOlderThan(cutoff);
}
```

Also update `getNotifications()` return type from `Page<Notification>` to
`Page<NotificationDto>` — done in Step 6 below.

---

## Implementation Order

```
Step 1  → Add WebSocket dependency
Step 2  → Enable @Async and @EnableScheduling on main class
Step 3  → Create AsyncConfig (bounded thread pool)
Step 4  → Create NotificationType enum
Step 5  → Update Notification entity + Flyway migration
Step 6  → Create NotificationDto
Step 7  → Create event record classes (5 files)
Step 8  → Create WebSocketConfig
Step 9  → Create WebSocketAuthInterceptor
Step 10 → Add GET /auth/token to AuthController
Step 11 → Create NotificationEventListener
Step 12 → Update TeamService
Step 13 → Update SubmissionService
Step 14 → Update EvaluationService
Step 15 → Add repository query methods
Step 16 → Create DeadlineWarningScheduler
Step 17 → Create NotificationCleanupScheduler
Step 18 → Final verification
```

---

## Step 1 — Add Dependency

Add to `pom.xml`:

```xml
<!-- WebSocket / STOMP -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

✅ Save and confirm Maven downloads cleanly and the project still compiles.

---

## Step 2 — Main Application Class

```java
// src/main/java/com/reviewflow/ReviewFlowApplication.java

package com.reviewflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync        // Required for @Async on event listeners
@EnableScheduling   // Required for deadline warning cron job
public class ReviewFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewFlowApplication.class, args);
    }
}
```

---

## Step 3 — Async Thread Pool Configuration

Never use Spring's default `SimpleAsyncTaskExecutor` in production — it creates
a new thread per task with no upper limit. This bounded pool caps at 10 threads
and queues up to 100 tasks before rejecting:

```java
// src/main/java/com/reviewflow/config/AsyncConfig.java

package com.reviewflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
```

---

## Step 4 — NotificationType Enum

```java
// src/main/java/com/reviewflow/model/enums/NotificationType.java

package com.reviewflow.model.enums;

public enum NotificationType {

    // Team
    TEAM_INVITE,            // Student invited to join a team
    TEAM_LOCKED,            // Team lock date passed or manually locked

    // Submissions
    NEW_SUBMISSION,         // Team member uploaded a new version

    // Evaluations
    FEEDBACK_PUBLISHED,     // Instructor published an evaluation

    // Deadlines
    DEADLINE_WARNING_48H,   // Assignment due in 48 hours — student has not submitted
    DEADLINE_WARNING_24H    // Assignment due in 24 hours — student has not submitted
}
```

---

## Step 5 — Update Notification Entity + Flyway Migration

### 5a — Updated Entity

Key changes from your current entity:
- `user` (`@ManyToOne`) → `userId` (`Long`) — no unnecessary join
- `type` (`String`) → `NotificationType` enum with `@Enumerated`
- `createdAt` → `@CreationTimestamp` so it is set automatically
- Added `@Index` annotations for query performance

```java
// src/main/java/com/reviewflow/model/entity/Notification.java

package com.reviewflow.model.entity;

import com.reviewflow.model.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user_id",    columnList = "user_id"),
    @Index(name = "idx_notifications_is_read",    columnList = "is_read"),
    @Index(name = "idx_notifications_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 150)
    @Builder.Default
    private String title = "";

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
```

### 5b — Flyway Migration

Use your next version number in the filename:

```sql
-- src/main/resources/db/migration/V10__fix_notifications_table.sql

-- Safe to truncate — all data in this table is seeded/test data
TRUNCATE TABLE notifications;

-- Drop FK constraint if it exists from old @ManyToOne mapping
ALTER TABLE notifications
    DROP FOREIGN KEY IF EXISTS fk_notifications_user_id;

-- Fix column types to match updated entity
ALTER TABLE notifications
    MODIFY COLUMN user_id     BIGINT       NOT NULL,
    MODIFY COLUMN type        VARCHAR(50)  NOT NULL,
    MODIFY COLUMN title       VARCHAR(150) NOT NULL DEFAULT '',
    MODIFY COLUMN action_url  VARCHAR(500);

-- Add indexes for query performance
CREATE INDEX IF NOT EXISTS idx_notifications_user_id
    ON notifications(user_id);

CREATE INDEX IF NOT EXISTS idx_notifications_is_read
    ON notifications(is_read);

CREATE INDEX IF NOT EXISTS idx_notifications_created_at
    ON notifications(created_at);
```

✅ Run the app now. Confirm in the console:
```
Flyway: Successfully applied 1 migration (V10__fix_notifications_table)
Tomcat started on port 8081
```
Do not continue until this is clean.

---

## Step 6 — NotificationDto

### 6a — Create the DTO

```java
// src/main/java/com/reviewflow/model/dto/response/NotificationDto.java

package com.reviewflow.model.dto.response;

import com.reviewflow.model.entity.Notification;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class NotificationDto {
    Long    id;
    String  type;
    String  title;
    String  message;
    Boolean isRead;
    String  actionUrl;
    Instant createdAt;

    public static NotificationDto from(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .type(n.getType().name())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.getIsRead())
                .actionUrl(n.getActionUrl())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
```

### 6b — Update NotificationService.getNotifications()

Replace your existing `getNotifications()` method:

```java
// Add import:
import com.reviewflow.model.dto.response.NotificationDto;

// Replace method:
public Page<NotificationDto> getNotifications(Long userId, Boolean unreadOnly,
                                               Pageable pageable) {
    if (Boolean.TRUE.equals(unreadOnly)) {
        return notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationDto::from);
    }
    return notificationRepository
            .findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(NotificationDto::from);
}
```

---

## Step 7 — Event Record Classes

Create all five files in `src/main/java/com/reviewflow/event/`:

```java
// TeamInviteEvent.java
package com.reviewflow.event;

public record TeamInviteEvent(
        Long   inviteeUserId,
        Long   teamId,
        String teamName,
        String invitedByFirstName,
        Long   assignmentId,
        String assignmentTitle
) {}
```

```java
// SubmissionUploadedEvent.java
package com.reviewflow.event;

import java.util.List;

public record SubmissionUploadedEvent(
        List<Long> recipientUserIds,    // all ACCEPTED members EXCEPT the uploader
        String     uploaderName,
        Long       teamId,
        String     teamName,
        Long       assignmentId,
        String     assignmentTitle,
        int        versionNumber
) {}
```

```java
// EvaluationPublishedEvent.java
package com.reviewflow.event;

import java.util.List;

public record EvaluationPublishedEvent(
        List<Long> recipientUserIds,    // all ACCEPTED team members
        Long       evaluationId,
        Long       assignmentId,
        String     assignmentTitle,
        int        totalScore,
        int        maxPossibleScore
) {}
```

```java
// TeamLockedEvent.java
package com.reviewflow.event;

import java.util.List;

public record TeamLockedEvent(
        List<Long> recipientUserIds,    // all ACCEPTED team members
        Long       teamId,
        String     teamName,
        Long       assignmentId,
        String     assignmentTitle
) {}
```

```java
// DeadlineWarningEvent.java
package com.reviewflow.event;

import java.util.List;

public record DeadlineWarningEvent(
        List<Long> recipientUserIds,    // enrolled students who have NOT submitted
        Long       assignmentId,
        String     assignmentTitle,
        String     courseCode,
        int        hoursUntilDue        // 48 or 24
) {}
```

---

## Step 8 — WebSocketConfig

```java
// src/main/java/com/reviewflow/config/WebSocketConfig.java

package com.reviewflow.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${reviewflow.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // /queue → point-to-point (one specific user)
        // /topic → broadcast (all subscribers) — available for future use
        config.enableSimpleBroker("/queue", "/topic");

        // Prefix for messages sent FROM client TO server
        config.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific destinations
        // Makes /user/{userId}/queue/notifications work correctly
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS(); // SockJS fallback for environments without native WebSocket
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
```

---

## Step 9 — WebSocketAuthInterceptor

Since your JWT filter extracts the user from an HTTP-only cookie, JavaScript
cannot read that cookie directly. The interceptor below reads a JWT token
passed as a STOMP `Authorization` header on the CONNECT frame.
The `GET /auth/token` endpoint in Step 10 provides that token to the frontend.

```java
// src/main/java/com/reviewflow/config/WebSocketAuthInterceptor.java

package com.reviewflow.config;

import com.reviewflow.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService         jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // Only authenticate on the initial CONNECT command
        if (!StompCommand.CONNECT.equals(accessor.getCommand())) return message;

        List<String> authHeaders = accessor.getNativeHeader("Authorization");

        if (authHeaders == null || authHeaders.isEmpty()) {
            log.warn("WebSocket CONNECT rejected — no Authorization header");
            return null; // Returning null rejects the connection
        }

        String token = authHeaders.get(0);

        try {
            String email = jwtService.extractEmail(token);
            if (email == null) {
                log.warn("WebSocket CONNECT rejected — could not extract email from token");
                return null;
            }

            var userDetails = userDetailsService.loadUserByUsername(email);

            if (!jwtService.isTokenValid(token, userDetails)) {
                log.warn("WebSocket CONNECT rejected — token invalid for {}", email);
                return null;
            }

            // Set authenticated principal on the STOMP session
            // This is what makes convertAndSendToUser() route to the right person
            var auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            accessor.setUser(auth);
            log.debug("WebSocket CONNECT authenticated: {}", email);

        } catch (Exception e) {
            log.warn("WebSocket CONNECT rejected — {}", e.getMessage());
            return null;
        }

        return message;
    }
}
```

---

## Step 10 — GET /auth/token Endpoint

Add this method to your existing `AuthController.java`.
This solves the HTTP-only cookie problem — JavaScript cannot read
the cookie directly, so the frontend calls this endpoint once,
holds the token value in memory, and passes it as the STOMP header on connect.

```java
// Add to AuthController.java

@Operation(summary = "Get access token value for WebSocket authentication")
@GetMapping("/token")
public ResponseEntity<ApiResponse<Map<String, String>>> getTokenForWebSocket(
        HttpServletRequest request,
        @AuthenticationPrincipal ReviewFlowUserDetails user) {

    if (user == null) {
        return ResponseEntity.status(401)
                .body(ApiResponse.error("UNAUTHORIZED", "Not authenticated"));
    }

    String token = getCookieValue(request, ACCESS_COOKIE);
    if (token == null) {
        return ResponseEntity.status(401)
                .body(ApiResponse.error("UNAUTHORIZED", "Access token not found"));
    }

    return ResponseEntity.ok(ApiResponse.ok(Map.of("token", token)));
}
```

✅ Run the app and test:
```
GET http://localhost:8081/api/v1/auth/token   (while logged in)
→ Expected: 200 with { "success": true, "data": { "token": "eyJ..." } }

GET http://localhost:8081/api/v1/auth/token   (while logged out)
→ Expected: 401
```

---

## Step 11 — NotificationEventListener

This is the core of the system. It listens for all events, persists to DB,
and pushes to connected users via WebSocket. The `@Async("notificationExecutor")`
ensures notifications never block the HTTP request thread.

```java
// src/main/java/com/reviewflow/event/NotificationEventListener.java

package com.reviewflow.event;

import com.reviewflow.model.dto.response.NotificationDto;
import com.reviewflow.model.entity.Notification;
import com.reviewflow.model.enums.NotificationType;
import com.reviewflow.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate  messagingTemplate;

    // ── TEAM INVITE ───────────────────────────────────────────────
    @Async("notificationExecutor")
    @EventListener
    public void onTeamInvite(TeamInviteEvent event) {
        saveAndPush(
                event.inviteeUserId(),
                NotificationType.TEAM_INVITE,
                "Team Invitation",
                event.invitedByFirstName() + " invited you to join \""
                        + event.teamName() + "\"",
                "/assignments/" + event.assignmentId() + "/team"
        );
    }

    // ── NEW SUBMISSION ────────────────────────────────────────────
    @Async("notificationExecutor")
    @EventListener
    public void onSubmissionUploaded(SubmissionUploadedEvent event) {
        saveAndPushMany(
                event.recipientUserIds(),
                NotificationType.NEW_SUBMISSION,
                "New Submission",
                event.uploaderName() + " uploaded version " + event.versionNumber()
                        + " for \"" + event.assignmentTitle() + "\"",
                "/assignments/" + event.assignmentId() + "/submissions"
        );
    }

    // ── FEEDBACK PUBLISHED ────────────────────────────────────────
    @Async("notificationExecutor")
    @EventListener
    public void onEvaluationPublished(EvaluationPublishedEvent event) {
        saveAndPushMany(
                event.recipientUserIds(),
                NotificationType.FEEDBACK_PUBLISHED,
                "Feedback Published",
                "Your feedback for \"" + event.assignmentTitle()
                        + "\" is available. Score: "
                        + event.totalScore() + "/" + event.maxPossibleScore(),
                "/assignments/" + event.assignmentId() + "/feedback"
        );
    }

    // ── TEAM LOCKED ───────────────────────────────────────────────
    @Async("notificationExecutor")
    @EventListener
    public void onTeamLocked(TeamLockedEvent event) {
        saveAndPushMany(
                event.recipientUserIds(),
                NotificationType.TEAM_LOCKED,
                "Team Locked",
                "Your team \"" + event.teamName() + "\" is now locked for \""
                        + event.assignmentTitle() + "\"",
                "/assignments/" + event.assignmentId() + "/team"
        );
    }

    // ── DEADLINE WARNING ──────────────────────────────────────────
    @Async("notificationExecutor")
    @EventListener
    public void onDeadlineWarning(DeadlineWarningEvent event) {
        NotificationType type = event.hoursUntilDue() <= 24
                ? NotificationType.DEADLINE_WARNING_24H
                : NotificationType.DEADLINE_WARNING_48H;

        saveAndPushMany(
                event.recipientUserIds(),
                type,
                "Deadline Reminder",
                "\"" + event.assignmentTitle() + "\" (" + event.courseCode()
                        + ") is due in " + event.hoursUntilDue()
                        + " hours. Don't forget to submit!",
                "/assignments/" + event.assignmentId() + "/submit"
        );
    }

    // ── HELPERS ───────────────────────────────────────────────────

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

        // Push via WebSocket — if user is offline this is silently ignored
        // The notification is safely persisted in DB and delivered via
        // GET /notifications on the user's next page load
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

    private void saveAndPushMany(List<Long> userIds, NotificationType type,
                                  String title, String message, String actionUrl) {
        for (Long userId : userIds) {
            saveAndPush(userId, type, title, message, actionUrl);
        }
    }
}
```

---

## Step 12 — Update TeamService

Remove any existing direct `NotificationService.create()` calls for invites
and team locking. Replace them with event publishing:

```java
// Add field to TeamService:
private final ApplicationEventPublisher eventPublisher;

// ── In inviteMember() — replace notification call with: ──────────
eventPublisher.publishEvent(new TeamInviteEvent(
        invitee.getId(),
        team.getId(),
        team.getName(),
        currentUser.getFirstName(),
        assignment.getId(),
        assignment.getTitle()
));

// ── In lockTeam() — replace notification call with: ──────────────
List<Long> memberIds = team.getMembers().stream()
        .filter(m -> m.getStatus() == MemberStatus.ACCEPTED)
        .map(TeamMember::getUserId)
        .toList();

eventPublisher.publishEvent(new TeamLockedEvent(
        memberIds,
        team.getId(),
        team.getName(),
        assignment.getId(),
        assignment.getTitle()
));
```

Add import:
```java
import com.reviewflow.event.TeamInviteEvent;
import com.reviewflow.event.TeamLockedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

---

## Step 13 — Update SubmissionService

```java
// Add field to SubmissionService:
private final ApplicationEventPublisher eventPublisher;

// ── After saving submission — replace notification call with: ────
List<Long> recipientIds = team.getMembers().stream()
        .filter(m -> m.getStatus() == MemberStatus.ACCEPTED)
        .map(TeamMember::getUserId)
        .filter(id -> !id.equals(currentUser.getUserId())) // exclude uploader
        .toList();

eventPublisher.publishEvent(new SubmissionUploadedEvent(
        recipientIds,
        currentUser.getFirstName() + " " + currentUser.getLastName(),
        team.getId(),
        team.getName(),
        assignment.getId(),
        assignment.getTitle(),
        savedSubmission.getVersionNumber()
));
```

Add import:
```java
import com.reviewflow.event.SubmissionUploadedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

---

## Step 14 — Update EvaluationService

```java
// Add field to EvaluationService:
private final ApplicationEventPublisher eventPublisher;

// ── After setting isDraft = false — replace notification call with: ──
List<Long> memberIds = submission.getTeam().getMembers().stream()
        .filter(m -> m.getStatus() == MemberStatus.ACCEPTED)
        .map(TeamMember::getUserId)
        .toList();

int maxPossibleScore = assignment.getRubricCriteria().stream()
        .mapToInt(RubricCriterion::getMaxScore)
        .sum();

eventPublisher.publishEvent(new EvaluationPublishedEvent(
        memberIds,
        evaluation.getId(),
        assignment.getId(),
        assignment.getTitle(),
        evaluation.getTotalScore(),
        maxPossibleScore
));
```

Add import:
```java
import com.reviewflow.event.EvaluationPublishedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

---

## Step 15 — Repository Query Methods

### AssignmentRepository — add:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

@Query("SELECT a.id FROM Assignment a " +
       "WHERE a.isPublished = true " +
       "AND a.dueAt BETWEEN :start AND :end")
List<Long> findPublishedDueBetween(
        @Param("start") Instant start,
        @Param("end")   Instant end
);
```

### CourseEnrollmentRepository — add:

```java
@Query("""
    SELECT e.userId FROM CourseEnrollment e
    JOIN Assignment a ON a.id = :assignmentId AND a.courseId = e.courseId
    WHERE e.userId NOT IN (
        SELECT s.uploadedBy FROM Submission s
        WHERE s.assignmentId = :assignmentId
    )
    """)
List<Long> findEnrolledStudentsWithoutSubmission(
        @Param("assignmentId") Long assignmentId
);
```

### NotificationRepository — add:

```java
import java.time.Instant;

@Modifying
@Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
void deleteOlderThan(@Param("cutoff") Instant cutoff);
```

---

## Step 16 — DeadlineWarningScheduler

```java
// src/main/java/com/reviewflow/scheduler/DeadlineWarningScheduler.java

package com.reviewflow.scheduler;

import com.reviewflow.event.DeadlineWarningEvent;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineWarningScheduler {

    private final AssignmentRepository      assignmentRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final ApplicationEventPublisher  eventPublisher;

    // Runs every hour on the hour
    @Scheduled(cron = "0 0 * * * *")
    public void sendDeadlineWarnings() {
        log.info("Deadline warning scheduler running");
        checkDeadlines(48);
        checkDeadlines(24);
    }

    private void checkDeadlines(int hoursUntilDue) {
        Instant windowStart = Instant.now().plus(hoursUntilDue - 1, ChronoUnit.HOURS);
        Instant windowEnd   = Instant.now().plus(hoursUntilDue,     ChronoUnit.HOURS);

        List<Long> assignmentIds =
                assignmentRepository.findPublishedDueBetween(windowStart, windowEnd);

        for (Long assignmentId : assignmentIds) {
            // Only notify students who have NOT yet submitted
            List<Long> studentIds =
                    enrollmentRepository.findEnrolledStudentsWithoutSubmission(assignmentId);

            if (studentIds.isEmpty()) {
                log.debug("Assignment {} — all students submitted, skipping", assignmentId);
                continue;
            }

            var assignment = assignmentRepository.findById(assignmentId).orElseThrow();

            eventPublisher.publishEvent(new DeadlineWarningEvent(
                    studentIds,
                    assignmentId,
                    assignment.getTitle(),
                    assignment.getCourse().getCode(),
                    hoursUntilDue
            ));

            log.info("Fired {}h deadline warning for assignment {} → {} students",
                    hoursUntilDue, assignmentId, studentIds.size());
        }
    }
}
```

---

## Step 17 — NotificationCleanupScheduler

```java
// src/main/java/com/reviewflow/scheduler/NotificationCleanupScheduler.java

package com.reviewflow.scheduler;

import com.reviewflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;

    // Runs every day at 2:00 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void deleteOldNotifications() {
        Instant cutoff = Instant.now().minus(60, ChronoUnit.DAYS);
        notificationService.deleteOlderThan(cutoff);
        log.info("Deleted notifications older than 60 days");
    }
}
```

---

## Step 18 — Final Verification

### Start the app and check the console

```
Flyway: Successfully applied 1 migration (V10)    ← schema updated
Tomcat started on port 8081                       ← app running
```

### Postman tests — run in this order

**1. Confirm WebSocket endpoint is accessible:**
```
GET http://localhost:8081/ws/info
→ Expected: 200 with SockJS JSON info object
```

**2. Confirm token endpoint works:**
```
POST http://localhost:8081/api/v1/auth/login     ← login first
GET  http://localhost:8081/api/v1/auth/token
→ Expected: 200 with { "data": { "token": "eyJ..." } }
```

**3. Confirm token endpoint rejects unauthenticated:**
```
POST http://localhost:8081/api/v1/auth/logout    ← logout first
GET  http://localhost:8081/api/v1/auth/token
→ Expected: 401
```

**4. Confirm notifications REST endpoints still work:**
```
POST http://localhost:8081/api/v1/auth/login
GET  http://localhost:8081/api/v1/notifications
→ Expected: 200 with paginated list

GET  http://localhost:8081/api/v1/notifications/unread-count
→ Expected: 200 with { "data": { "count": 0 } }
```

### Full test checklist

- [ ] App starts with no errors after adding all files
- [ ] Flyway V10 applies cleanly on startup
- [ ] `GET /ws/info` returns 200
- [ ] `GET /auth/token` returns token when authenticated
- [ ] `GET /auth/token` returns 401 when not authenticated
- [ ] `GET /notifications` returns `Page<NotificationDto>` (not raw entity)
- [ ] `GET /notifications/unread-count` returns `{ count: N }` never null
- [ ] `PATCH /notifications/{id}/read` marks as read correctly
- [ ] `PATCH /notifications/read-all` marks all as read
- [ ] `DELETE /notifications/{id}` owned by user → 200
- [ ] `DELETE /notifications/{id}` owned by different user → 403
- [ ] Invite a student → `TEAM_INVITE` notification appears in DB for invitee only
- [ ] Upload submission → `NEW_SUBMISSION` in DB for all members except uploader
- [ ] Publish evaluation → `FEEDBACK_PUBLISHED` in DB for all team members
- [ ] Lock team → `TEAM_LOCKED` in DB for all members
- [ ] User offline when event fires → notification in DB, delivered on next page load

---

## Frontend Connection Reference

For the React frontend team:

```bash
npm install @stomp/stompjs sockjs-client
```

```javascript
// src/lib/websocket.js

import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const BASE_URL = import.meta.env.VITE_API_BASE_URL; // http://localhost:8081/api/v1

let stompClient = null;

export async function connectWebSocket(onNotification) {
    // Get token from backend — HTTP-only cookie not readable by JS
    const res  = await fetch(`${BASE_URL}/auth/token`, { credentials: 'include' });
    const body = await res.json();

    if (!res.ok || !body.data?.token) {
        console.warn('WebSocket: could not obtain token — skipping');
        return;
    }

    const token = body.data.token; // held in memory only — never localStorage

    stompClient = new Client({
        webSocketFactory: () =>
            new SockJS(`${BASE_URL.replace('/api/v1', '')}/ws`),

        connectHeaders: {
            Authorization: token,
        },

        onConnect: () => {
            console.log('WebSocket connected');

            stompClient.subscribe('/user/queue/notifications', (frame) => {
                const notification = JSON.parse(frame.body);
                onNotification(notification); // update bell count + show toast
            });
        },

        onDisconnect: () => console.log('WebSocket disconnected'),

        onStompError: (frame) =>
            console.error('STOMP error:', frame.headers['message']),

        reconnectDelay: 5000,
    });

    stompClient.activate();
}

export function disconnectWebSocket() {
    if (stompClient?.active) {
        stompClient.deactivate();
        stompClient = null;
    }
}
```
