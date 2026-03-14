package com.reviewflow.event;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.model.dto.response.NotificationDto;
import com.reviewflow.model.entity.Notification;
import com.reviewflow.model.enums.NotificationType;
import com.reviewflow.repository.NotificationRepository;
import com.reviewflow.service.HashidService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
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
    private final CacheManager           cacheManager;
    private final HashidService          hashidService;

    // ── TEAM INVITE ───────────────────────────────────────────────
    @Async("notificationExecutor")
    @EventListener
    public void onTeamInvite(TeamInviteEvent event) {
        saveAndPush(
                event.inviteeUserId(),
                NotificationType.TEAM_INVITE,
                "Team Invitation",
                event.invitedByFirstName() + " invited you to join team \"" + event.teamName() 
                    + "\" for " + event.assignmentTitle(),
                "/teams/{id}",
                event.teamId()  // targetId - will be hashed in action URL
        );
    }

    // ── NEW SUBMISSION ────────────────────────────────────────────
    @Async("notificationExecutor")
    @EventListener
    public void onSubmissionUploaded(SubmissionUploadedEvent event) {
        saveAndPushMany(
                event.recipientUserIds(),
                NotificationType.NEW_SUBMISSION,
                "New Submission Uploaded",
                event.uploaderName() + " uploaded version " + event.versionNumber() 
                    + " for " + event.assignmentTitle(),
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
                "Your evaluation for " + event.assignmentTitle() + " is now available. Score: " 
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
                "Team \"" + event.teamName() + "\" has been locked for " + event.assignmentTitle(),
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
                "Assignment Due Soon",
                event.assignmentTitle() + " (" + event.courseCode() + ") is due in " 
                    + event.hoursUntilDue() + " hours. You have not submitted yet.",
                "/assignments/" + event.assignmentId() + "/submit"
        );
    }

    // ── HELPERS ───────────────────────────────────────────────────

    private void saveAndPush(Long userId, NotificationType type,
                              String title, String message, String actionUrl) {
        saveAndPush(userId, type, title, message, actionUrl, null);
    }

    private void saveAndPush(Long userId, NotificationType type,
                              String title, String message, String actionUrl, Long targetId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .actionUrl(actionUrl)
                .targetId(targetId)  // Optional - for action URL rewriting with hashed IDs
                .build();

        notificationRepository.save(notification);

        // Evict stale unread count for this user — it just increased by 1
        var cache = cacheManager.getCache(CacheConfig.CACHE_UNREAD_COUNT);
        if (cache != null) cache.evict(userId);

        // Push via WebSocket — if user is offline this is silently ignored
        // The notification is safely persisted in DB and delivered via
        // GET /notifications on the user's next page load
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/notifications",
                    NotificationDto.from(notification, hashidService)
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
