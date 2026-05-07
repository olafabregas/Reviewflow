package com.reviewflow.infrastructure.email;

import com.reviewflow.infrastructure.email.event.AccountReactivatedEmailEvent;
import com.reviewflow.infrastructure.email.event.AnnouncementPostedEmailEvent;
import com.reviewflow.infrastructure.email.event.AssignmentDueSoonEmailEvent;
import com.reviewflow.infrastructure.email.event.EmailEvent;
import com.reviewflow.infrastructure.email.event.EvaluationPublishedEmailEvent;
import com.reviewflow.infrastructure.email.event.ExtensionDecisionEmailEvent;
import com.reviewflow.infrastructure.email.event.ExtensionRequestReceivedEmailEvent;
import com.reviewflow.infrastructure.email.event.PasswordResetCompletedEmailEvent;
import com.reviewflow.infrastructure.email.event.PasswordResetRequestedEmailEvent;
import com.reviewflow.infrastructure.email.event.SubmissionReceivedEmailEvent;
import com.reviewflow.infrastructure.email.event.TeamInviteReceivedEmailEvent;
import com.reviewflow.infrastructure.email.event.TeamInviteRespondedEmailEvent;
import com.reviewflow.infrastructure.email.event.WelcomeEmailEvent;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.infrastructure.email.EmailService;
import com.reviewflow.infrastructure.email.EmailTemplateService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventListener {

  private final EmailService emailService;
  private final EmailTemplateService templateService;
  private final UserRepository userRepository;

  @Value("${app.base-url:http://localhost:5173}")
  private String appBaseUrl;

  @Async("emailTaskExecutor")
  @EventListener
  public void handleWelcome(WelcomeEmailEvent event) {
    sendEmail(
        event,
        "welcome",
        withCommon(event, vars("firstName", event.getFirstName())),
        "Welcome to ReviewFlow");
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handleEvaluationPublished(EvaluationPublishedEmailEvent event) {
    sendEmail(
        event,
        "evaluation-published",
        withCommon(
            event,
            vars(
                "recipientName", event.getRecipientName(),
                "assignmentTitle", event.getAssignmentTitle(),
                "courseCode", event.getCourseCode(),
                "totalScore", event.getTotalScore(),
                "evaluationHashId", event.getEvaluationHashId())),
        "Your evaluation for " + event.getAssignmentTitle() + " is ready");
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handleSubmissionReceived(SubmissionReceivedEmailEvent event) {
    if (!isEmailEnabled(event)) {
      return;
    }
    sendEmail(
        event,
        "submission-received",
        withCommon(
            event,
            vars(
                "instructorName", event.getRecipientName(),
                "submitterName", event.getTeamOrStudentName(),
                "assignmentTitle", event.getAssignmentTitle(),
                "versionNumber", event.getVersionNumber(),
                "submissionHashId", event.getSubmissionHashId())),
        "New submission: " + event.getAssignmentTitle());
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handleTeamInviteReceived(TeamInviteReceivedEmailEvent event) {
    if (!isEmailEnabled(event)) {
      return;
    }
    sendEmail(
        event,
        "team-invite-received",
        withCommon(
            event,
            vars(
                "recipientName", event.getRecipientName(),
                "inviterName", event.getInviterName(),
                "assignmentTitle", event.getAssignmentTitle(),
                "teamName", event.getTeamName(),
                "teamMemberHashId", event.getTeamMemberHashId())),
        "Team invite for " + event.getAssignmentTitle());
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handleTeamInviteResponded(TeamInviteRespondedEmailEvent event) {
    if (!isEmailEnabled(event)) {
      return;
    }
    sendEmail(
        event,
        "team-invite-responded",
        withCommon(
            event,
            vars(
                "recipientName", event.getRecipientName(),
                "inviteeName", event.getInviteeName(),
                "accepted", event.getAccepted(),
                "teamName", event.getTeamName(),
                "assignmentTitle", event.getAssignmentTitle())),
        "Team invite response for " + event.getAssignmentTitle());
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handleAssignmentDueSoon(AssignmentDueSoonEmailEvent event) {
    if (!isEmailEnabled(event)) {
      return;
    }
    sendEmail(
        event,
        "assignment-due-soon",
        withCommon(
            event,
            vars(
                "recipientName", event.getRecipientName(),
                "assignmentTitle", event.getAssignmentTitle(),
                "dueAt", event.getDueAt(),
                "courseCode", event.getCourseCode(),
                "assignmentHashId", event.getAssignmentHashId())),
        "Upcoming due date: " + event.getAssignmentTitle());
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handleAnnouncementPosted(AnnouncementPostedEmailEvent event) {
    sendEmail(
        event,
        "announcement-posted",
        withCommon(
            event,
            vars(
                "recipientName", event.getRecipientName(),
                "announcementTitle", event.getAnnouncementTitle(),
                "body", event.getBody(),
                "senderName", event.getSenderName(),
                "courseCode", event.getCourseCode())),
        "New announcement: " + event.getAnnouncementTitle());
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handleExtensionRequestReceived(ExtensionRequestReceivedEmailEvent event) {
    if (!isEmailEnabled(event)) {
      return;
    }
    sendEmail(
        event,
        "extension-request-received",
        withCommon(
            event,
            vars(
                "recipientName", event.getRecipientName(),
                "studentName", event.getStudentName(),
                "assignmentTitle", event.getAssignmentTitle(),
                "requestedDueAt", event.getRequestedDueAt(),
                "reason", event.getReason(),
                "extensionRequestHashId", event.getExtensionRequestHashId())),
        "Extension request received: " + event.getAssignmentTitle());
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handleExtensionDecision(ExtensionDecisionEmailEvent event) {
    sendEmail(
        event,
        "extension-decision",
        withCommon(
            event,
            vars(
                "recipientName", event.getRecipientName(),
                "assignmentTitle", event.getAssignmentTitle(),
                "approved", event.getApproved(),
                "instructorNote", event.getInstructorNote(),
                "newDueAt", event.getNewDueAt())),
        "Extension decision: " + event.getAssignmentTitle());
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handleAccountReactivated(AccountReactivatedEmailEvent event) {
    sendEmail(
        event,
        "account-reactivated",
        withCommon(event, vars("firstName", event.getFirstName())),
        "Your ReviewFlow account has been reactivated");
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handlePasswordResetRequested(PasswordResetRequestedEmailEvent event) {
    sendEmail(
        event,
        "password-reset-requested",
        withCommon(
            event,
            vars(
                "firstName", event.getFirstName(),
                "resetUrl", event.getResetUrl())),
        "Reset your ReviewFlow password");
  }

  @Async("emailTaskExecutor")
  @EventListener
  public void handlePasswordResetCompleted(PasswordResetCompletedEmailEvent event) {
    if (!isEmailEnabled(event)) {
      return;
    }
    sendEmail(
        event,
        "password-reset-completed",
        withCommon(event, vars("firstName", event.getFirstName())),
        "Your ReviewFlow password was changed");
  }

  private Map<String, Object> withCommon(EmailEvent event, Map<String, Object> variables) {
    String effectiveBaseUrl =
        appBaseUrl == null || appBaseUrl.isBlank() ? "http://localhost:5173" : appBaseUrl;
    variables.put("appBaseUrl", effectiveBaseUrl);
    if (event.getCategory() == EmailEvent.EmailCategory.STANDARD) {
      variables.put("preferencesUrl", effectiveBaseUrl + "/settings/preferences");
    } else {
      variables.put("preferencesUrl", null);
    }
    return variables;
  }

  private boolean isEmailEnabled(EmailEvent event) {
    if (event.getCategory() == EmailEvent.EmailCategory.CRITICAL) {
      return true;
    }
    return userRepository.findEmailPreferenceByEmail(event.getRecipientEmail()).orElse(true);
  }

  private void sendEmail(
      EmailEvent event, String template, Map<String, Object> variables, String subject) {
    try {
      String html = templateService.renderHtml(template, variables);
      String text = templateService.renderText(template, variables);
      emailService.send(event.getRecipientEmail(), subject, html, text);
      log.info(
          "Email sent: event={}, recipient={}",
          event.getClass().getSimpleName(),
          event.getRecipientEmail());
    } catch (Exception e) {
      // Never break request flows due to email processing.
      log.error(
          "Email handler failed: template={}, recipient={}, error={}",
          template,
          event.getRecipientEmail(),
          e.getMessage());
    }
  }

  private Map<String, Object> vars(Object... keyValues) {
    Map<String, Object> variables = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      variables.put((String) keyValues[i], keyValues[i + 1]);
    }
    return variables;
  }
}
