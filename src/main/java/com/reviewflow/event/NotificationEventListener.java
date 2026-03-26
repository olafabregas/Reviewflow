package com.reviewflow.event;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.event.email.AssignmentDueSoonEmailEvent;
import com.reviewflow.event.email.EvaluationPublishedEmailEvent;
import com.reviewflow.event.email.SubmissionReceivedEmailEvent;
import com.reviewflow.event.email.TeamInviteReceivedEmailEvent;
import com.reviewflow.model.dto.response.NotificationDto;
import com.reviewflow.model.entity.Notification;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.enums.NotificationType;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.NotificationRepository;
import com.reviewflow.repository.UserRepository;
import com.reviewflow.service.HashidService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
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
    private final SimpMessagingTemplate messagingTemplate;
    private final CacheManager cacheManager;
    private final HashidService hashidService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

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
                event.teamId() // targetId - will be hashed in action URL
        );

        userRepository.findById(event.inviteeUserId()).ifPresent(user
                -> eventPublisher.publishEvent(new TeamInviteReceivedEmailEvent(
                        user.getEmail(),
                        fullNameOrEmail(user),
                        event.invitedByFirstName(),
                        event.assignmentTitle(),
                        event.teamName(),
                        hashidService.encode(event.teamId()))));
    }

    // ── NEW SUBMISSION ────────────────────────────────────────────
    @Async("notificationExecutor")
    @EventListener
    public void onSubmissionUploaded(SubmissionUploadedEvent event) {
        String message = event.uploaderName() + " uploaded version " + event.versionNumber()
                + " for " + event.assignmentTitle();
        saveAndPushMany(
                event.recipientUserIds(),
                NotificationType.NEW_SUBMISSION,
                "New Submission Uploaded",
                message,
                "/assignments/" + event.assignmentId() + "/submissions"
        );

        for (Long recipientUserId : event.recipientUserIds()) {
            userRepository.findById(recipientUserId).ifPresent(user
                    -> eventPublisher.publishEvent(new SubmissionReceivedEmailEvent(
                            user.getEmail(),
                            fullNameOrEmail(user),
                            event.uploaderName(),
                            event.assignmentTitle(),
                            hashidService.encode(event.submissionId()),
                            event.versionNumber())));
        }
    }

    // ── FEEDBACK PUBLISHED ────────────────────────────────────────
    @Async("notificationExecutor")
    @EventListener
    public void onEvaluationPublished(EvaluationPublishedEvent event) {
        String message = "Your evaluation for " + event.assignmentTitle() + " is now available. Score: "
                + event.totalScore() + "/" + event.maxPossibleScore();
        if (event.submissionType() == SubmissionType.INDIVIDUAL && event.studentId() != null) {
            saveAndPush(
                    event.studentId(),
                    NotificationType.FEEDBACK_PUBLISHED,
                    "Feedback Published",
                    message,
                    "/assignments/" + event.assignmentId() + "/feedback"
            );

            userRepository.findById(event.studentId()).ifPresent(user
                    -> eventPublisher.publishEvent(new EvaluationPublishedEmailEvent(
                            user.getEmail(),
                            fullNameOrEmail(user),
                            event.assignmentTitle(),
                            "N/A",
                            event.totalScore(),
                            hashidService.encode(event.evaluationId()))));
            return;
        }

        saveAndPushMany(
                event.recipientUserIds(),
                NotificationType.FEEDBACK_PUBLISHED,
                "Feedback Published",
                message,
                "/assignments/" + event.assignmentId() + "/feedback"
        );

        for (Long recipientUserId : event.recipientUserIds()) {
            userRepository.findById(recipientUserId).ifPresent(user
                    -> eventPublisher.publishEvent(new EvaluationPublishedEmailEvent(
                            user.getEmail(),
                            fullNameOrEmail(user),
                            event.assignmentTitle(),
                            "N/A",
                            event.totalScore(),
                            hashidService.encode(event.evaluationId()))));
        }
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

        for (Long recipientUserId : event.recipientUserIds()) {
            userRepository.findById(recipientUserId).ifPresent(user
                    -> eventPublisher.publishEvent(new AssignmentDueSoonEmailEvent(
                            user.getEmail(),
                            fullNameOrEmail(user),
                            event.assignmentTitle(),
                            event.dueAt(),
                            event.courseCode(),
                            hashidService.encode(event.assignmentId()))));
        }
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
                .targetId(targetId) // Optional - for action URL rewriting with hashed IDs
                .build();

        notificationRepository.save(notification);

        // Evict stale unread count for this user — it just increased by 1
        var cache = cacheManager.getCache(CacheConfig.CACHE_UNREAD_COUNT);
        if (cache != null) {
            cache.evict(userId);
        }

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

    private String fullNameOrEmail(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }
}
