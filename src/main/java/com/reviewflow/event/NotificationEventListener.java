package com.reviewflow.event;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.event.email.AnnouncementPostedEmailEvent;
import com.reviewflow.event.email.AssignmentDueSoonEmailEvent;
import com.reviewflow.event.email.EvaluationPublishedEmailEvent;
import com.reviewflow.event.email.ExtensionDecisionEmailEvent;
import com.reviewflow.event.email.ExtensionRequestReceivedEmailEvent;
import com.reviewflow.event.email.SubmissionReceivedEmailEvent;
import com.reviewflow.event.email.TeamInviteReceivedEmailEvent;
import com.reviewflow.event.email.ForceLogoutEmailEvent;
import com.reviewflow.event.email.TeamUnlockedEmailEvent;
import com.reviewflow.event.email.EvaluationReopenedInstructorEmailEvent;
import com.reviewflow.event.email.EvaluationReopenedTeamEmailEvent;
import com.reviewflow.model.dto.response.NotificationDto;
import com.reviewflow.model.entity.Notification;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.NotificationType;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.CourseEnrollmentRepository;
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
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    // -- ANNOUNCEMENT PUBLISHED ------------------------------------
    @Async("notificationExecutor")
    @EventListener
    public void onAnnouncementPublished(AnnouncementPublishedEvent event) {
        List<Long> recipientUserIds;

        if ("COURSE".equals(event.getTarget())) {
            // Course announcement - send to all enrolled students in this course
            recipientUserIds = courseEnrollmentRepository.findUserIdsByCourse_ID(event.getCourseId());
        } else {
            // Platform announcement - send based on recipient_type
            if ("ALL_STUDENTS".equals(event.getRecipientType())) {
                recipientUserIds = userRepository.findAllIdsByRole(UserRole.STUDENT);
            } else if ("ALL_INSTRUCTORS".equals(event.getRecipientType())) {
                recipientUserIds = userRepository.findAllIdsByRole(UserRole.INSTRUCTOR);
            } else {
                // ALL_USERS
                recipientUserIds = userRepository.findAllIds();
            }
        }

        // Create and push notifications, fire email events
        String message = event.getTitle();
        for (Long recipientUserId : recipientUserIds) {
            saveAndPush(
                    recipientUserId,
                    NotificationType.ANNOUNCEMENT,
                    "Announcement",
                    message,
                    "/announcements/{id}",
                    event.getAnnouncementId()
            );

            userRepository.findById(recipientUserId).ifPresent(user
                    -> eventPublisher.publishEvent(new AnnouncementPostedEmailEvent(
                            user.getEmail(),
                            fullNameOrEmail(user),
                            event.getTitle(),
                            event.getBody(),
                            event.getCreatedByName(),
                            "" // courseCode - empty string for platform announcements, not available in event
                    )));
        }
    }

    // -- TEAM INVITE -----------------------------------------------
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

    // -- NEW SUBMISSION --------------------------------------------
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

    // -- FEEDBACK PUBLISHED ----------------------------------------
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

    // -- TEAM LOCKED -----------------------------------------------
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

    // -- DEADLINE WARNING ------------------------------------------
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

    // -- EXTENSION REQUESTED ---------------------------------------
    @Async("notificationExecutor")
    @EventListener
    public void onExtensionRequested(ExtensionRequestedEvent event) {
        for (Long instructorUserId : event.instructorUserIds()) {
            saveAndPush(
                    instructorUserId,
                    NotificationType.SYSTEM,
                    "Extension Request",
                    "Extension request from " + event.studentName() + " for " + event.assignmentTitle(),
                    "/extension-requests/{id}",
                    event.extensionRequestId()
            );

            userRepository.findById(instructorUserId).ifPresent(user
                    -> eventPublisher.publishEvent(new ExtensionRequestReceivedEmailEvent(
                            user.getEmail(),
                            fullNameOrEmail(user),
                            event.studentName(),
                            event.assignmentTitle(),
                            event.requestedDueAt(),
                            event.reason(),
                            hashidService.encode(event.extensionRequestId()))));
        }
    }

    // -- EXTENSION DECIDED -----------------------------------------
    @Async("notificationExecutor")
    @EventListener
    public void onExtensionDecided(ExtensionDecidedEvent event) {
        String title = event.approved() ? "Extension Approved" : "Extension Denied";
        String message = event.approved()
                ? "Your extension request for " + event.assignmentTitle() + " was approved"
                : "Your extension request for " + event.assignmentTitle() + " was denied";

        saveAndPushMany(
                event.recipientUserIds(),
                NotificationType.SYSTEM,
                title,
                message,
                "/extension-requests/{id}"
        );

        for (Long recipientUserId : event.recipientUserIds()) {
            userRepository.findById(recipientUserId).ifPresent(user
                    -> eventPublisher.publishEvent(new ExtensionDecisionEmailEvent(
                            user.getEmail(),
                            fullNameOrEmail(user),
                            event.assignmentTitle(),
                            event.approved(),
                            event.instructorNote(),
                            event.newDueAt())));
        }
    }

    // -- HELPERS ----------------------------------------------------
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

        // Evict stale unread count for this user - it just increased by 1
        var cache = cacheManager.getCache(CacheConfig.CACHE_UNREAD_COUNT);
        if (cache != null) {
            cache.evict(userId);
        }

        // Push via WebSocket - if user is offline this is silently ignored
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
            log.debug("User {} offline - {} saved to DB only", userId, type);
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

    // -- PRD-09: SYSTEM ADMIN EVENTS --------------------------------
    /**
     * PRD-09: Force logout event - notify target user their session was
     * terminated
     */
    @Async("notificationExecutor")
    @EventListener
    public void onForceLogout(ForceLogoutEvent event) {
        String message = "Your session has been terminated by platform administrators. Reason: " + event.getReason();
        saveAndPush(
                event.getTargetUserId(),
                NotificationType.SYSTEM,
                "Session Terminated",
                message,
                "/auth/login"
        );

        // Also publish email event for audit trail
        eventPublisher.publishEvent(new ForceLogoutEmailEvent(
                this,
                event.getTargetUserEmail(),
                event.getReason(),
                event.getRevokedTokenCount()
        ));

        log.info("Force logout notification sent to user {}", event.getTargetUserId());
    }

    /**
     * PRD-09: Team unlock event - notify team members that team has been
     * unlocked
     */
    @Async("notificationExecutor")
    @EventListener
    public void onTeamUnlockedBySystem(TeamUnlockedBySystemEvent event) {
        String message = "Team \"" + event.getTeamName() + "\" has been unlocked by system administrators. Reason: " + event.getReason();

        // Send CRITICAL notification to all team members (ignore email preference)
        for (com.reviewflow.dto.UserDto member : event.getTeamMembers()) {
            saveAndPush(
                    Long.valueOf(member.getId()),
                    NotificationType.SYSTEM,
                    "Team Unlocked",
                    message,
                    "/teams/" + hashidService.encode(event.getTeamId())
            );

            // Publish email event (CRITICAL - always send)
            userRepository.findById(Long.valueOf(member.getId())).ifPresent(user
                    -> eventPublisher.publishEvent(new TeamUnlockedEmailEvent(
                            this,
                            user.getEmail(),
                            fullNameOrEmail(user),
                            event.getTeamName(),
                            event.getReason()
                    ))
            );
        }

        log.info("Team unlock notification sent to {} team members", event.getTeamMembers().size());
    }

    /**
     * PRD-09: Evaluation reopen event - notify instructor and team that
     * evaluation has been reopened
     */
    @Async("notificationExecutor")
    @EventListener
    public void onEvaluationReopened(EvaluationReopenedEvent event) {
        // Notify instructor
        String instructorMessage = "Evaluation has been reopened by system administrators for editing. Reason: " + event.getReason();
        String instructorEmail = event.getInstructorEmail();

        userRepository.findByEmail(instructorEmail).ifPresent(instructor
                -> saveAndPush(
                        instructor.getId(),
                        NotificationType.SYSTEM,
                        "Evaluation Reopened",
                        instructorMessage,
                        "/evaluations/" + hashidService.encode(event.getEvaluationId())
                )
        );

        // Publish instructor email (CRITICAL)
        eventPublisher.publishEvent(new EvaluationReopenedInstructorEmailEvent(
                this,
                instructorEmail,
                event.getReason(),
                event.getScoringSnapshot()
        ));

        // Notify team members
        String teamMessage = "An evaluation for your team has been reopened by system administrators. Instructor will update the scores. Reason: " + event.getReason();
        for (String teamMemberEmail : event.getTeamMemberEmails()) {
            userRepository.findByEmail(teamMemberEmail).ifPresent(member -> {
                saveAndPush(
                        member.getId(),
                        NotificationType.SYSTEM,
                        "Evaluation Reopened",
                        teamMessage,
                        "/evaluations"
                );
            });

            // Publish team member email (CRITICAL)
            eventPublisher.publishEvent(new EvaluationReopenedTeamEmailEvent(
                    this,
                    teamMemberEmail,
                    event.getReason()
            ));
        }

        log.info("Evaluation reopen notification sent to {} team members and instructor", event.getTeamMemberEmails().size());
    }

    /**
     * PRD-09: Cache evicted event - trigger out-of-cycle WebSocket metrics push
     */
    @EventListener
    public void onCacheEvicted(CacheEvictedEvent event) {
        log.info("Cache evicted: {} (entries: {})", event.getCacheName(), event.getEntryCount());
        // Note: SystemMetricsPushScheduler will handle the WebSocket push
        // This listener just logs the cache eviction event
    }
}
