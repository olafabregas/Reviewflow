package com.reviewflow.notification.event;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.reviewflow.announcement.event.AnnouncementPublishedEvent;
import com.reviewflow.discussion.event.DiscussionPublishedEvent;
import com.reviewflow.discussion.event.DiscussionReminderBatchEvent;
import com.reviewflow.discussion.event.DiscussionReplyEvent;
import com.reviewflow.evaluation.event.EvaluationPublishedEvent;
import com.reviewflow.evaluation.event.EvaluationReopenedEvent;
import com.reviewflow.evaluation.event.PdfReadyEvent;
import com.reviewflow.extension.event.ExtensionDecidedEvent;
import com.reviewflow.extension.event.ExtensionRequestedEvent;
import com.reviewflow.submission.event.SubmissionUploadedEvent;
import com.reviewflow.team.event.TeamInviteEvent;
import com.reviewflow.team.event.TeamLockedEvent;
import com.reviewflow.team.event.TeamUnlockedBySystemEvent;
import com.reviewflow.infrastructure.email.event.AnnouncementPostedEmailEvent;
import com.reviewflow.infrastructure.email.event.AssignmentDueSoonEmailEvent;
import com.reviewflow.infrastructure.email.event.DiscussionInstructorReplyEmailEvent;
import com.reviewflow.infrastructure.email.event.DiscussionPublishedEmailEvent;
import com.reviewflow.infrastructure.email.event.DiscussionReminderEmailEvent;
import com.reviewflow.infrastructure.email.event.EvaluationPublishedEmailEvent;
import com.reviewflow.infrastructure.email.event.EvaluationReopenedInstructorEmailEvent;
import com.reviewflow.infrastructure.email.event.EvaluationReopenedTeamEmailEvent;
import com.reviewflow.infrastructure.email.event.ExtensionDecisionEmailEvent;
import com.reviewflow.infrastructure.email.event.ExtensionRequestReceivedEmailEvent;
import com.reviewflow.infrastructure.email.event.ForceLogoutEmailEvent;
import com.reviewflow.infrastructure.email.event.SubmissionReceivedEmailEvent;
import com.reviewflow.infrastructure.email.event.TeamInviteReceivedEmailEvent;
import com.reviewflow.infrastructure.email.event.TeamUnlockedEmailEvent;
import com.reviewflow.notification.dto.response.NotificationDto;
import com.reviewflow.notification.service.NotificationService;
import com.reviewflow.course.service.CourseService;
import com.reviewflow.user.service.UserService;
import com.reviewflow.shared.domain.CourseEnrollment;
import com.reviewflow.shared.domain.Notification;
import com.reviewflow.shared.domain.NotificationType;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.dto.UserDto;
import com.reviewflow.shared.event.CacheEvictedEvent;
import com.reviewflow.shared.event.ForceLogoutEvent;
import com.reviewflow.shared.util.HashidService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private final SimpMessagingTemplate messagingTemplate;
  private final HashidService hashidService;
  private final UserService userService;
  private final CourseService courseService;
  private final NotificationService notificationService;
  private final ApplicationEventPublisher eventPublisher;

  // -- ANNOUNCEMENT PUBLISHED ------------------------------------
  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onAnnouncementPublished(AnnouncementPublishedEvent event) {
    List<Long> recipientUserIds;

    if ("COURSE".equals(event.getTarget())) {
      recipientUserIds = courseService.findEnrolledUserIdsByCourseId(event.getCourseId());
    } else {
      if ("ALL_STUDENTS".equals(event.getRecipientType())) {
        recipientUserIds = userService.findAllUserIdsByRole(UserRole.STUDENT);
      } else if ("ALL_INSTRUCTORS".equals(event.getRecipientType())) {
        recipientUserIds = userService.findAllUserIdsByRole(UserRole.INSTRUCTOR);
      } else {
        recipientUserIds = userService.findAllUserIds();
      }
    }

    String message = event.getTitle();
    for (Long recipientUserId : recipientUserIds) {
      saveAndPush(
          recipientUserId,
          NotificationType.ANNOUNCEMENT,
          "Announcement",
          message,
          "/announcements/{id}",
          event.getAnnouncementId());

      userService
          .findUserById(recipientUserId)
          .ifPresent(
              user ->
                  eventPublisher.publishEvent(
                      new AnnouncementPostedEmailEvent(
                          user.getEmail(),
                          user.getFullNameOrEmail(),
                          event.getTitle(),
                          event.getBody(),
                          event.getCreatedByName(),
                          "")));
    }
  }

  // -- DISCUSSION (PRD-17) ----------------------------------------
  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDiscussionPublished(DiscussionPublishedEvent event) {
    String actionUrl = "/discussions/{id}";
    String title = "New discussion";
    String message = event.title();
    String discussionHashId = hashidService.encode(event.discussionId());
    for (CourseEnrollment e : courseService.findEnrollmentsWithUserByCourseId(event.courseId())) {
      if (e.getUser().getRole() != UserRole.STUDENT) {
        continue;
      }
      saveAndPush(
          e.getUser().getId(),
          NotificationType.DISCUSSION_PUBLISHED,
          title,
          message,
          actionUrl,
          event.discussionId());
      eventPublisher.publishEvent(
          new DiscussionPublishedEmailEvent(
              e.getUser().getEmail(),
              e.getUser().getFullNameOrEmail(),
              event.title(),
              event.dueAt(),
              event.courseCode(),
              discussionHashId));
    }
    log.debug(
        "Discussion published in-app notifications for discussion {} course {}",
        event.discussionId(),
        event.courseId());
  }

  @Async("notificationExecutor")
  @EventListener
  public void onDiscussionReply(DiscussionReplyEvent event) {
    if (event.originalAuthorId() == null
        || event.originalAuthorId().equals(event.replierUserId())) {
      return;
    }
    saveAndPush(
        event.originalAuthorId(),
        NotificationType.DISCUSSION_REPLY,
        "New reply",
        event.replierName() + " replied to your post.",
        "/discussions/{id}",
        event.discussionId());
    if (event.originalAuthorRole() == UserRole.STUDENT && isStaffRole(event.replierRole())) {
      userService
          .findUserById(event.originalAuthorId())
          .ifPresent(
              original ->
                  eventPublisher.publishEvent(
                      new DiscussionInstructorReplyEmailEvent(
                          original.getEmail(),
                          original.getFullNameOrEmail(),
                          event.replierName(),
                          event.discussionTitle(),
                          event.replySnippet(),
                          hashidService.encode(event.discussionId()))));
    }
  }

  @Async("notificationExecutor")
  @EventListener
  public void onDiscussionReminderBatch(DiscussionReminderBatchEvent event) {
    String actionUrl = "/discussions/{id}";
    String title = "Discussion due soon";
    DateTimeFormatter dueFmt =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneOffset.UTC);
    String dueStr = dueFmt.format(event.dueAt());
    for (DiscussionReminderBatchEvent.ReminderRecipient r : event.recipients()) {
      String message =
          String.format(
              "\"%s\" is due %s. Post your response before the deadline.",
              event.discussionTitle(), dueStr);
      notificationService
          .tryCreateDedupedDiscussionReminder(
              r.studentId(), event.discussionId(), title, message, actionUrl)
          .ifPresent(
              n -> {
                try {
                  messagingTemplate.convertAndSendToUser(
                      r.studentId().toString(),
                      "/queue/notifications",
                      NotificationDto.from(n, hashidService));
                } catch (Exception ex) {
                  log.debug("User {} offline - reminder saved only", r.studentId());
                }
                eventPublisher.publishEvent(
                    new DiscussionReminderEmailEvent(
                        r.email(),
                        reminderRecipientDisplayName(r),
                        event.discussionTitle(),
                        event.dueAt(),
                        hashidService.encode(event.discussionId())));
              });
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
        event.invitedByFirstName()
            + " invited you to join team \""
            + event.teamName()
            + "\" for "
            + event.assignmentTitle(),
        "/teams/{id}",
        event.teamId());

    userService
        .findUserById(event.inviteeUserId())
        .ifPresent(
            user ->
                eventPublisher.publishEvent(
                    new TeamInviteReceivedEmailEvent(
                        user.getEmail(),
                        user.getFullNameOrEmail(),
                        event.invitedByFirstName(),
                        event.assignmentTitle(),
                        event.teamName(),
                        hashidService.encode(event.teamId()))));
  }

  // -- NEW SUBMISSION --------------------------------------------
  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSubmissionUploaded(SubmissionUploadedEvent event) {
    String message =
        event.uploaderName()
            + " uploaded version "
            + event.versionNumber()
            + " for "
            + event.assignmentTitle();
    saveAndPushMany(
        event.recipientUserIds(),
        NotificationType.NEW_SUBMISSION,
        "New Submission Uploaded",
        message,
        "/assignments/" + event.assignmentId() + "/submissions");

    for (Long recipientUserId : event.recipientUserIds()) {
      userService
          .findUserById(recipientUserId)
          .ifPresent(
              user ->
                  eventPublisher.publishEvent(
                      new SubmissionReceivedEmailEvent(
                          user.getEmail(),
                          user.getFullNameOrEmail(),
                          event.uploaderName(),
                          event.assignmentTitle(),
                          hashidService.encode(event.submissionId()),
                          event.versionNumber())));
    }
  }

  // -- FEEDBACK PUBLISHED ----------------------------------------
  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEvaluationPublished(EvaluationPublishedEvent event) {
    String message =
        "Your evaluation for "
            + event.assignmentTitle()
            + " is now available. Score: "
            + event.totalScore()
            + "/"
            + event.maxPossibleScore();
    if (event.submissionType() == SubmissionType.INDIVIDUAL && event.studentId() != null) {
      saveAndPush(
          event.studentId(),
          NotificationType.FEEDBACK_PUBLISHED,
          "Feedback Published",
          message,
          "/assignments/" + event.assignmentId() + "/feedback");

      userService
          .findUserById(event.studentId())
          .ifPresent(
              user ->
                  eventPublisher.publishEvent(
                      new EvaluationPublishedEmailEvent(
                          user.getEmail(),
                          user.getFullNameOrEmail(),
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
        "/assignments/" + event.assignmentId() + "/feedback");

    for (Long recipientUserId : event.recipientUserIds()) {
      userService
          .findUserById(recipientUserId)
          .ifPresent(
              user ->
                  eventPublisher.publishEvent(
                      new EvaluationPublishedEmailEvent(
                          user.getEmail(),
                          user.getFullNameOrEmail(),
                          event.assignmentTitle(),
                          "N/A",
                          event.totalScore(),
                          hashidService.encode(event.evaluationId()))));
    }
  }

  // -- PDF READY (PRD-21, in-app only) ----------------------------
  @Async("notificationExecutor")
  @EventListener
  public void onPdfReady(PdfReadyEvent event) {
    String message =
        "Your evaluation report for " + event.assignmentTitle() + " is ready to download.";
    String actionUrl = "/evaluations/" + hashidService.encode(event.evaluationId()) + "/pdf";
    saveAndPushMany(
        event.recipientUserIds(),
        NotificationType.FEEDBACK_PUBLISHED,
        "Evaluation Report Ready",
        message,
        actionUrl);
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
        "/assignments/" + event.assignmentId() + "/team");
  }

  // -- DEADLINE WARNING ------------------------------------------
  @Async("notificationExecutor")
  @EventListener
  public void onDeadlineWarning(DeadlineWarningEvent event) {
    NotificationType type =
        event.hoursUntilDue() <= 24
            ? NotificationType.DEADLINE_WARNING_24H
            : NotificationType.DEADLINE_WARNING_48H;

    String title = "Assignment Due Soon";
    String message =
        event.assignmentTitle()
            + " ("
            + event.courseCode()
            + ") is due in "
            + event.hoursUntilDue()
            + " hours. You have not submitted yet.";
    String actionUrl = "/assignments/" + event.assignmentId() + "/submit";

    for (Long recipientUserId : event.recipientUserIds()) {
      notificationService
          .tryCreateDedupedDeadlineReminder(
              recipientUserId, type, event.assignmentId(), title, message, actionUrl)
          .ifPresent(
              saved -> {
                pushNotification(recipientUserId, saved);
                userService
                    .findUserById(recipientUserId)
                    .ifPresent(
                        user ->
                            eventPublisher.publishEvent(
                                new AssignmentDueSoonEmailEvent(
                                    user.getEmail(),
                                    user.getFullNameOrEmail(),
                                    event.assignmentTitle(),
                                    event.dueAt(),
                                    event.courseCode(),
                                    hashidService.encode(event.assignmentId()))));
              });
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
          event.extensionRequestId());

      userService
          .findUserById(instructorUserId)
          .ifPresent(
              user ->
                  eventPublisher.publishEvent(
                      new ExtensionRequestReceivedEmailEvent(
                          user.getEmail(),
                          user.getFullNameOrEmail(),
                          event.studentName(),
                          event.assignmentTitle(),
                          event.requestedDueAt(),
                          event.reason(),
                          hashidService.encode(event.extensionRequestId()))));
    }
  }

  // -- EXTENSION DECIDED -----------------------------------------
  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onExtensionDecided(ExtensionDecidedEvent event) {
    String title = event.approved() ? "Extension Approved" : "Extension Denied";
    String message =
        event.approved()
            ? "Your extension request for " + event.assignmentTitle() + " was approved"
            : "Your extension request for " + event.assignmentTitle() + " was denied";

    saveAndPushMany(
        event.recipientUserIds(),
        NotificationType.SYSTEM,
        title,
        message,
        "/extension-requests/{id}");

    for (Long recipientUserId : event.recipientUserIds()) {
      userService
          .findUserById(recipientUserId)
          .ifPresent(
              user ->
                  eventPublisher.publishEvent(
                      new ExtensionDecisionEmailEvent(
                          user.getEmail(),
                          user.getFullNameOrEmail(),
                          event.assignmentTitle(),
                          event.approved(),
                          event.instructorNote(),
                          event.newDueAt())));
    }
  }

  // -- HELPERS ----------------------------------------------------
  private static boolean isStaffRole(UserRole role) {
    return role == UserRole.INSTRUCTOR
        || role == UserRole.ADMIN
        || role == UserRole.SYSTEM_ADMIN;
  }

  private static String reminderRecipientDisplayName(DiscussionReminderBatchEvent.ReminderRecipient r) {
    if (r.firstName() != null && !r.firstName().isBlank()) {
      return r.firstName().strip();
    }
    return "there";
  }

  private void saveAndPush(
      Long userId, NotificationType type, String title, String message, String actionUrl) {
    saveAndPush(userId, type, title, message, actionUrl, null);
  }

  private void saveAndPush(
      Long userId,
      NotificationType type,
      String title,
      String message,
      String actionUrl,
      Long targetId) {
    Notification saved =
        notificationService.create(userId, type, title, message, actionUrl, targetId);
    pushNotification(userId, saved);
  }

  private void pushNotification(Long userId, Notification notification) {
    try {
      messagingTemplate.convertAndSendToUser(
          userId.toString(),
          "/queue/notifications",
          NotificationDto.from(notification, hashidService));
      log.debug("Pushed {} to user {}", notification.getType(), userId);
    } catch (Exception e) {
      log.debug("User {} offline - {} saved to DB only", userId, notification.getType());
    }
  }

  private void saveAndPushMany(
      List<Long> userIds, NotificationType type, String title, String message, String actionUrl) {
    for (Long userId : userIds) {
      saveAndPush(userId, type, title, message, actionUrl);
    }
  }

  // -- PRD-09: SYSTEM ADMIN EVENTS --------------------------------
  @Async("notificationExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onForceLogout(ForceLogoutEvent event) {
    String message =
        "Your session has been terminated by platform administrators. Reason: " + event.getReason();
    saveAndPush(
        event.getTargetUserId(),
        NotificationType.SYSTEM,
        "Session Terminated",
        message,
        "/auth/login");

    eventPublisher.publishEvent(
        new ForceLogoutEmailEvent(
            this, event.getTargetUserEmail(), event.getReason(), event.getRevokedTokenCount()));

    log.info("Force logout notification sent to user {}", event.getTargetUserId());
  }

  @Async("notificationExecutor")
  @EventListener
  public void onTeamUnlockedBySystem(TeamUnlockedBySystemEvent event) {
    String message =
        "Team \""
            + event.getTeamName()
            + "\" has been unlocked by system administrators. Reason: "
            + event.getReason();

    for (UserDto member : event.getTeamMembers()) {
      saveAndPush(
          Long.valueOf(member.getId()),
          NotificationType.SYSTEM,
          "Team Unlocked",
          message,
          "/teams/" + hashidService.encode(event.getTeamId()));

      userService
          .findUserById(Long.valueOf(member.getId()))
          .ifPresent(
              user ->
                  eventPublisher.publishEvent(
                      new TeamUnlockedEmailEvent(
                          this,
                          user.getEmail(),
                          user.getFullNameOrEmail(),
                          event.getTeamName(),
                          event.getReason())));
    }

    log.info("Team unlock notification sent to {} team members", event.getTeamMembers().size());
  }

  @Async("notificationExecutor")
  @EventListener
  public void onEvaluationReopened(EvaluationReopenedEvent event) {
    String instructorMessage =
        "Evaluation has been reopened by system administrators for editing. Reason: "
            + event.getReason();
    String instructorEmail = event.getInstructorEmail();

    userService
        .findUserByEmail(instructorEmail)
        .ifPresent(
            instructor ->
                saveAndPush(
                    instructor.getId(),
                    NotificationType.SYSTEM,
                    "Evaluation Reopened",
                    instructorMessage,
                    "/evaluations/" + hashidService.encode(event.getEvaluationId())));

    eventPublisher.publishEvent(
        new EvaluationReopenedInstructorEmailEvent(
            this, instructorEmail, event.getReason(), event.getScoringSnapshot()));

    String teamMessage =
        "An evaluation for your team has been reopened by system administrators. Instructor will"
            + " update the scores. Reason: "
            + event.getReason();
    for (String teamMemberEmail : event.getTeamMemberEmails()) {
      userService
          .findUserByEmail(teamMemberEmail)
          .ifPresent(
              member -> {
                saveAndPush(
                    member.getId(),
                    NotificationType.SYSTEM,
                    "Evaluation Reopened",
                    teamMessage,
                    "/evaluations");
              });

      eventPublisher.publishEvent(
          new EvaluationReopenedTeamEmailEvent(this, teamMemberEmail, event.getReason()));
    }

    log.info(
        "Evaluation reopen notification sent to {} team members and instructor",
        event.getTeamMemberEmails().size());
  }

  @EventListener
  public void onCacheEvicted(CacheEvictedEvent event) {
    log.info("Cache evicted: {} (entries: {})", event.getCacheName(), event.getEntryCount());
  }
}
