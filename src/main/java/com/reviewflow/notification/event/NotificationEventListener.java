package com.reviewflow.notification.event;

import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.reviewflow.announcement.event.AnnouncementPublishedEvent;
import com.reviewflow.evaluation.event.EvaluationPublishedEvent;
import com.reviewflow.evaluation.event.EvaluationReopenedEvent;
import com.reviewflow.extension.event.ExtensionDecidedEvent;
import com.reviewflow.extension.event.ExtensionRequestedEvent;
import com.reviewflow.submission.event.SubmissionUploadedEvent;
import com.reviewflow.team.event.TeamInviteEvent;
import com.reviewflow.team.event.TeamLockedEvent;
import com.reviewflow.team.event.TeamUnlockedBySystemEvent;
import com.reviewflow.infrastructure.email.event.AnnouncementPostedEmailEvent;
import com.reviewflow.infrastructure.email.event.AssignmentDueSoonEmailEvent;
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
import com.reviewflow.notification.repository.NotificationRepository;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.shared.constant.CacheNames;
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
      recipientUserIds = courseEnrollmentRepository.findUserIdsByCourseId(event.getCourseId());
    } else {
      if ("ALL_STUDENTS".equals(event.getRecipientType())) {
        recipientUserIds = userRepository.findAllIdsByRole(UserRole.STUDENT);
      } else if ("ALL_INSTRUCTORS".equals(event.getRecipientType())) {
        recipientUserIds = userRepository.findAllIdsByRole(UserRole.INSTRUCTOR);
      } else {
        recipientUserIds = userRepository.findAllIds();
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

      userRepository
          .findById(recipientUserId)
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

    userRepository
        .findById(event.inviteeUserId())
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
  @EventListener
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
      userRepository
          .findById(recipientUserId)
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
  @EventListener
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

      userRepository
          .findById(event.studentId())
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
      userRepository
          .findById(recipientUserId)
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

    saveAndPushMany(
        event.recipientUserIds(),
        type,
        "Assignment Due Soon",
        event.assignmentTitle()
            + " ("
            + event.courseCode()
            + ") is due in "
            + event.hoursUntilDue()
            + " hours. You have not submitted yet.",
        "/assignments/" + event.assignmentId() + "/submit");

    for (Long recipientUserId : event.recipientUserIds()) {
      userRepository
          .findById(recipientUserId)
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

      userRepository
          .findById(instructorUserId)
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
  @EventListener
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
      userRepository
          .findById(recipientUserId)
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
    Notification notification =
        Notification.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .message(message)
            .actionUrl(actionUrl)
            .targetId(targetId)
            .build();

    notificationRepository.save(notification);

    var cache = cacheManager.getCache(CacheNames.CACHE_UNREAD_COUNT);
    if (cache != null) {
      cache.evict(userId);
    }

    try {
      messagingTemplate.convertAndSendToUser(
          userId.toString(),
          "/queue/notifications",
          NotificationDto.from(notification, hashidService));
      log.debug("Pushed {} to user {}", type, userId);
    } catch (Exception e) {
      log.debug("User {} offline - {} saved to DB only", userId, type);
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
  @EventListener
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

      userRepository
          .findById(Long.valueOf(member.getId()))
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

    userRepository
        .findByEmail(instructorEmail)
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
      userRepository
          .findByEmail(teamMemberEmail)
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
