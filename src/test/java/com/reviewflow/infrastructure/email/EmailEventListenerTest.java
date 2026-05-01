package com.reviewflow.infrastructure.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.infrastructure.email.EmailEventListener;
import com.reviewflow.infrastructure.email.event.AccountReactivatedEmailEvent;
import com.reviewflow.infrastructure.email.event.AnnouncementPostedEmailEvent;
import com.reviewflow.infrastructure.email.event.AssignmentDueSoonEmailEvent;
import com.reviewflow.infrastructure.email.event.EvaluationPublishedEmailEvent;
import com.reviewflow.infrastructure.email.event.ExtensionDecisionEmailEvent;
import com.reviewflow.infrastructure.email.event.ExtensionRequestReceivedEmailEvent;
import com.reviewflow.infrastructure.email.event.SubmissionReceivedEmailEvent;
import com.reviewflow.infrastructure.email.event.TeamInviteReceivedEmailEvent;
import com.reviewflow.infrastructure.email.event.TeamInviteRespondedEmailEvent;
import com.reviewflow.infrastructure.email.event.WelcomeEmailEvent;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.infrastructure.email.EmailService;
import com.reviewflow.infrastructure.email.EmailTemplateService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailEventListenerTest {

  @Mock private EmailService emailService;

  @Mock private EmailTemplateService templateService;

  @Mock private UserRepository userRepository;

  @InjectMocks private EmailEventListener listener;

  @Test
  void handleSubmissionReceived_emailDisabled_skipsSend() {
    SubmissionReceivedEmailEvent event =
        new SubmissionReceivedEmailEvent(
            "instructor@test.local", "Instructor", "Team Alpha", "Project 1", "SUB123", 2);
    when(userRepository.findEmailPreferenceByEmail("instructor@test.local"))
        .thenReturn(Optional.of(false));

    listener.handleSubmissionReceived(event);

    verify(templateService, never()).renderHtml(eq("submission-received"), anyMap());
    verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void handleSubmissionReceived_emailEnabled_sendsRenderedEmail() {
    SubmissionReceivedEmailEvent event =
        new SubmissionReceivedEmailEvent(
            "instructor@test.local", "Instructor", "Team Alpha", "Project 1", "SUB123", 2);
    when(userRepository.findEmailPreferenceByEmail("instructor@test.local"))
        .thenReturn(Optional.of(true));
    when(templateService.renderHtml(eq("submission-received"), anyMap())).thenReturn("<p>html</p>");
    when(templateService.renderText(eq("submission-received"), anyMap())).thenReturn("text");

    listener.handleSubmissionReceived(event);

    verify(emailService)
        .send("instructor@test.local", "New submission: Project 1", "<p>html</p>", "text");
  }

  @Test
  void handleEvaluationPublished_criticalEvent_sendsWithoutPreferenceLookup() {
    EvaluationPublishedEmailEvent event =
        new EvaluationPublishedEmailEvent(
            "student@test.local", "Student", "Project 1", "CSC101", 92, "EVAL123");
    when(templateService.renderHtml(eq("evaluation-published"), anyMap()))
        .thenReturn("<p>html</p>");
    when(templateService.renderText(eq("evaluation-published"), anyMap())).thenReturn("text");

    listener.handleEvaluationPublished(event);

    verify(emailService)
        .send(
            "student@test.local", "Your evaluation for Project 1 is ready", "<p>html</p>", "text");
    verify(userRepository, never()).findEmailPreferenceByEmail("student@test.local");
  }

  @Test
  void handleWelcome_templateFailure_isSwallowed() {
    WelcomeEmailEvent event = new WelcomeEmailEvent(10L, "student@test.local", "Ada");
    when(templateService.renderHtml(eq("welcome"), anyMap()))
        .thenThrow(new RuntimeException("template missing"));

    assertDoesNotThrow(() -> listener.handleWelcome(event));

    verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void handleTeamInviteReceived_emailEnabled_sendsRenderedEmail() {
    TeamInviteReceivedEmailEvent event =
        new TeamInviteReceivedEmailEvent(
            "student@test.local", "Student", "Inviter", "Project 2", "Team Alpha", "TM123");
    when(userRepository.findEmailPreferenceByEmail("student@test.local"))
        .thenReturn(Optional.of(true));
    when(templateService.renderHtml(eq("team-invite-received"), anyMap()))
        .thenReturn("<p>html</p>");
    when(templateService.renderText(eq("team-invite-received"), anyMap())).thenReturn("text");

    listener.handleTeamInviteReceived(event);

    verify(emailService)
        .send("student@test.local", "Team invite for Project 2", "<p>html</p>", "text");
  }

  @Test
  void handleTeamInviteReceived_emailDisabled_skipsSend() {
    TeamInviteReceivedEmailEvent event =
        new TeamInviteReceivedEmailEvent(
            "student@test.local", "Student", "Inviter", "Project 2", "Team Alpha", "TM123");
    when(userRepository.findEmailPreferenceByEmail("student@test.local"))
        .thenReturn(Optional.of(false));

    listener.handleTeamInviteReceived(event);

    verify(templateService, never()).renderHtml(eq("team-invite-received"), anyMap());
    verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void handleTeamInviteResponded_emailDisabled_skipsSend() {
    TeamInviteRespondedEmailEvent event =
        new TeamInviteRespondedEmailEvent(
            "student@test.local", "Student", "Invitee", true, "Team Alpha", "Project 2");
    when(userRepository.findEmailPreferenceByEmail("student@test.local"))
        .thenReturn(Optional.of(false));

    listener.handleTeamInviteResponded(event);

    verify(templateService, never()).renderHtml(eq("team-invite-responded"), anyMap());
    verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void handleTeamInviteResponded_emailEnabled_sendsRenderedEmail() {
    TeamInviteRespondedEmailEvent event =
        new TeamInviteRespondedEmailEvent(
            "student@test.local", "Student", "Invitee", true, "Team Alpha", "Project 2");
    when(userRepository.findEmailPreferenceByEmail("student@test.local"))
        .thenReturn(Optional.of(true));
    when(templateService.renderHtml(eq("team-invite-responded"), anyMap()))
        .thenReturn("<p>html</p>");
    when(templateService.renderText(eq("team-invite-responded"), anyMap())).thenReturn("text");

    listener.handleTeamInviteResponded(event);

    verify(emailService)
        .send("student@test.local", "Team invite response for Project 2", "<p>html</p>", "text");
  }

  @Test
  void handleAssignmentDueSoon_emailEnabled_sendsRenderedEmail() {
    AssignmentDueSoonEmailEvent event =
        new AssignmentDueSoonEmailEvent(
            "student@test.local",
            "Student",
            "Project 3",
            Instant.parse("2026-03-30T10:00:00Z"),
            "CSC301",
            "A123");
    when(userRepository.findEmailPreferenceByEmail("student@test.local"))
        .thenReturn(Optional.of(true));
    when(templateService.renderHtml(eq("assignment-due-soon"), anyMap())).thenReturn("<p>html</p>");
    when(templateService.renderText(eq("assignment-due-soon"), anyMap())).thenReturn("text");

    listener.handleAssignmentDueSoon(event);

    verify(emailService)
        .send("student@test.local", "Upcoming due date: Project 3", "<p>html</p>", "text");
  }

  @Test
  void handleAssignmentDueSoon_emailDisabled_skipsSend() {
    AssignmentDueSoonEmailEvent event =
        new AssignmentDueSoonEmailEvent(
            "student@test.local",
            "Student",
            "Project 3",
            Instant.parse("2026-03-30T10:00:00Z"),
            "CSC301",
            "A123");
    when(userRepository.findEmailPreferenceByEmail("student@test.local"))
        .thenReturn(Optional.of(false));

    listener.handleAssignmentDueSoon(event);

    verify(templateService, never()).renderHtml(eq("assignment-due-soon"), anyMap());
    verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void handleAnnouncementPosted_criticalEvent_sendsWithoutPreferenceLookup() {
    AnnouncementPostedEmailEvent event =
        new AnnouncementPostedEmailEvent(
            "student@test.local",
            "Student",
            "Exam Moved",
            "The exam is now next week.",
            "Prof. Doe",
            "CSC101");
    when(templateService.renderHtml(eq("announcement-posted"), anyMap())).thenReturn("<p>html</p>");
    when(templateService.renderText(eq("announcement-posted"), anyMap())).thenReturn("text");

    listener.handleAnnouncementPosted(event);

    verify(emailService)
        .send("student@test.local", "New announcement: Exam Moved", "<p>html</p>", "text");
    verify(userRepository, never()).findEmailPreferenceByEmail("student@test.local");
  }

  @Test
  void handleExtensionRequestReceived_emailEnabled_sendsRenderedEmail() {
    ExtensionRequestReceivedEmailEvent event =
        new ExtensionRequestReceivedEmailEvent(
            "instructor@test.local",
            "Instructor",
            "Ada",
            "Project 4",
            Instant.parse("2026-04-01T10:00:00Z"),
            "Medical reason",
            "ER456");
    when(userRepository.findEmailPreferenceByEmail("instructor@test.local"))
        .thenReturn(Optional.of(true));
    when(templateService.renderHtml(eq("extension-request-received"), anyMap()))
        .thenReturn("<p>html</p>");
    when(templateService.renderText(eq("extension-request-received"), anyMap())).thenReturn("text");

    listener.handleExtensionRequestReceived(event);

    verify(emailService)
        .send(
            "instructor@test.local",
            "Extension request received: Project 4",
            "<p>html</p>",
            "text");
  }

  @Test
  void handleExtensionRequestReceived_emailDisabled_skipsSend() {
    ExtensionRequestReceivedEmailEvent event =
        new ExtensionRequestReceivedEmailEvent(
            "instructor@test.local",
            "Instructor",
            "Ada",
            "Project 4",
            Instant.parse("2026-04-01T10:00:00Z"),
            "Medical reason",
            "ER456");
    when(userRepository.findEmailPreferenceByEmail("instructor@test.local"))
        .thenReturn(Optional.of(false));

    listener.handleExtensionRequestReceived(event);

    verify(templateService, never()).renderHtml(eq("extension-request-received"), anyMap());
    verify(emailService, never()).send(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void handleExtensionDecision_criticalEvent_sendsWithoutPreferenceLookup() {
    ExtensionDecisionEmailEvent event =
        new ExtensionDecisionEmailEvent(
            "student@test.local",
            "Student",
            "Project 5",
            true,
            "Approved",
            Instant.parse("2026-04-05T10:00:00Z"));
    when(templateService.renderHtml(eq("extension-decision"), anyMap())).thenReturn("<p>html</p>");
    when(templateService.renderText(eq("extension-decision"), anyMap())).thenReturn("text");

    listener.handleExtensionDecision(event);

    verify(emailService)
        .send("student@test.local", "Extension decision: Project 5", "<p>html</p>", "text");
    verify(userRepository, never()).findEmailPreferenceByEmail("student@test.local");
  }

  @Test
  void handleAccountReactivated_criticalEvent_sendsWithoutPreferenceLookup() {
    AccountReactivatedEmailEvent event =
        new AccountReactivatedEmailEvent("student@test.local", "Ada");
    when(templateService.renderHtml(eq("account-reactivated"), anyMap())).thenReturn("<p>html</p>");
    when(templateService.renderText(eq("account-reactivated"), anyMap())).thenReturn("text");

    listener.handleAccountReactivated(event);

    verify(emailService)
        .send(
            "student@test.local",
            "Your ReviewFlow account has been reactivated",
            "<p>html</p>",
            "text");
    verify(userRepository, never()).findEmailPreferenceByEmail("student@test.local");
  }
}
