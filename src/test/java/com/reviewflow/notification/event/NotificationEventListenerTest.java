package com.reviewflow.notification.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.reviewflow.evaluation.event.EvaluationPublishedEvent;
import com.reviewflow.evaluation.event.PdfReadyEvent;
import com.reviewflow.submission.event.SubmissionUploadedEvent;
import com.reviewflow.team.event.TeamInviteEvent;
import com.reviewflow.team.event.TeamLockedEvent;
import com.reviewflow.infrastructure.email.event.AssignmentDueSoonEmailEvent;
import com.reviewflow.infrastructure.email.event.EvaluationPublishedEmailEvent;
import com.reviewflow.infrastructure.email.event.SubmissionReceivedEmailEvent;
import com.reviewflow.infrastructure.email.event.TeamInviteReceivedEmailEvent;
import com.reviewflow.notification.service.NotificationService;
import com.reviewflow.user.service.UserService;
import com.reviewflow.course.service.CourseService;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.Notification;
import com.reviewflow.shared.domain.NotificationType;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.shared.util.HashidService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventListenerTest {

  @Mock private NotificationService notificationService;
  @Mock private SimpMessagingTemplate messagingTemplate;
  @Mock private HashidService hashidService;
  @Mock private UserService userService;
  @Mock private CourseService courseService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private NotificationEventListener listener;

  private User user(Long id, String email, String firstName, String lastName) {
    return User.builder().id(id).email(email).firstName(firstName).lastName(lastName).build();
  }

  private void stubNotificationCreate() {
    AtomicLong id = new AtomicLong(1000L);
    when(notificationService.create(
            anyLong(),
            any(NotificationType.class),
            any(),
            any(),
            any(),
            any()))
        .thenAnswer(
            invocation -> {
              Notification n =
                  Notification.builder()
                      .id(id.getAndIncrement())
                      .userId(invocation.getArgument(0))
                      .type(invocation.getArgument(1))
                      .title(invocation.getArgument(2))
                      .message(invocation.getArgument(3))
                      .actionUrl(invocation.getArgument(4))
                      .targetId(invocation.getArgument(5))
                      .build();
              return n;
            });
    when(notificationService.create(
            anyLong(), any(NotificationType.class), any(), any(), any()))
        .thenAnswer(
            invocation ->
                Notification.builder()
                    .id(id.getAndIncrement())
                    .userId(invocation.getArgument(0))
                    .type(invocation.getArgument(1))
                    .title(invocation.getArgument(2))
                    .message(invocation.getArgument(3))
                    .actionUrl(invocation.getArgument(4))
                    .build());
  }

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    when(hashidService.encode(anyLong())).thenAnswer(invocation -> "H" + invocation.getArgument(0));
    stubNotificationCreate();
  }

  @Test
  void onTeamInvite_persistsNotificationAndPublishesEmail() {
    TeamInviteEvent event = new TeamInviteEvent(10L, 77L, "Team Delta", "Alex", 12L, "Essay 1");
    when(userService.findUserById(10L))
        .thenReturn(Optional.of(user(10L, "invitee@test.local", "Ada", "Lovelace")));

    listener.onTeamInvite(event);

    verify(notificationService)
        .create(
            eq(10L),
            eq(NotificationType.TEAM_INVITE),
            eq("Team Invitation"),
            any(),
            eq("/teams/{id}"),
            eq(77L));

    ArgumentCaptor<TeamInviteReceivedEmailEvent> emailCaptor =
        ArgumentCaptor.forClass(TeamInviteReceivedEmailEvent.class);
    verify(eventPublisher).publishEvent(emailCaptor.capture());
    TeamInviteReceivedEmailEvent email = emailCaptor.getValue();
    assertEquals("invitee@test.local", email.getRecipientEmail());
    assertEquals("H77", email.getTeamMemberHashId());
  }

  @Test
  void onSubmissionUploaded_publishesSubmissionEmailWithSubmissionHash() {
    SubmissionUploadedEvent event =
        new SubmissionUploadedEvent(
            List.of(20L, 21L),
            "Alice Lee",
            null,
            77L,
            null,
            10L,
            "Essay 1",
            2,
            SubmissionType.INDIVIDUAL,
            501L);

    when(userService.findUserById(20L))
        .thenReturn(Optional.of(user(20L, "instructor1@test.local", "Prof", "Kim")));
    when(userService.findUserById(21L))
        .thenReturn(Optional.of(user(21L, "instructor2@test.local", "Grace", "Hopper")));

    listener.onSubmissionUploaded(event);

    verify(notificationService, times(2))
        .create(
            anyLong(),
            eq(NotificationType.NEW_SUBMISSION),
            any(),
            any(),
            any(),
            nullable(Long.class));

    ArgumentCaptor<SubmissionReceivedEmailEvent> emailCaptor =
        ArgumentCaptor.forClass(SubmissionReceivedEmailEvent.class);
    verify(eventPublisher, times(2)).publishEvent(emailCaptor.capture());
    List<SubmissionReceivedEmailEvent> emails = emailCaptor.getAllValues();
    assertEquals(2, emails.size());
    assertEquals("H501", emails.get(0).getSubmissionHashId());
    assertEquals("H501", emails.get(1).getSubmissionHashId());
    assertEquals(2, emails.get(0).getVersionNumber());
  }

  @Test
  void onEvaluationPublished_individualPath_persistsSingleNotificationAndEmail() {
    EvaluationPublishedEvent event =
        new EvaluationPublishedEvent(
            List.of(200L, 201L), 200L, 88L, 18L, "Project 2", 92, 100, SubmissionType.INDIVIDUAL);

    when(userService.findUserById(200L))
        .thenReturn(Optional.of(user(200L, "student@test.local", "Ada", "Lovelace")));

    listener.onEvaluationPublished(event);

    verify(notificationService)
        .create(
            eq(200L),
            eq(NotificationType.FEEDBACK_PUBLISHED),
            any(),
            any(),
            any(),
            nullable(Long.class));

    ArgumentCaptor<EvaluationPublishedEmailEvent> emailCaptor =
        ArgumentCaptor.forClass(EvaluationPublishedEmailEvent.class);
    verify(eventPublisher).publishEvent(emailCaptor.capture());
    EvaluationPublishedEmailEvent email = emailCaptor.getValue();
    assertEquals("student@test.local", email.getRecipientEmail());
    assertEquals("H88", email.getEvaluationHashId());
    assertEquals(92, email.getTotalScore());
  }

  @Test
  void onEvaluationPublished_teamPath_persistsAndPublishesForAllRecipients() {
    EvaluationPublishedEvent event =
        new EvaluationPublishedEvent(
            List.of(300L, 301L), null, 99L, 19L, "Project Team", 75, 100, SubmissionType.TEAM);

    when(userService.findUserById(300L))
        .thenReturn(Optional.of(user(300L, "team1@test.local", "Alan", "Turing")));
    when(userService.findUserById(301L))
        .thenReturn(Optional.of(user(301L, "team2@test.local", "Katherine", "Johnson")));

    listener.onEvaluationPublished(event);

    verify(notificationService, times(2))
        .create(
            anyLong(),
            eq(NotificationType.FEEDBACK_PUBLISHED),
            any(),
            any(),
            any(),
            nullable(Long.class));

    ArgumentCaptor<EvaluationPublishedEmailEvent> emailCaptor =
        ArgumentCaptor.forClass(EvaluationPublishedEmailEvent.class);
    verify(eventPublisher, times(2)).publishEvent(emailCaptor.capture());
    List<EvaluationPublishedEmailEvent> emails = emailCaptor.getAllValues();
    assertEquals(2, emails.size());
    assertEquals("H99", emails.get(0).getEvaluationHashId());
    assertEquals("H99", emails.get(1).getEvaluationHashId());
  }

  @Test
  void onPdfReady_persistsInAppNotificationForAllRecipients() {
    PdfReadyEvent event =
        new PdfReadyEvent(88L, 18L, List.of(200L, 201L), "Project 2");

    listener.onPdfReady(event);

    verify(notificationService, times(2))
        .create(
            anyLong(),
            eq(NotificationType.FEEDBACK_PUBLISHED),
            eq("Evaluation Report Ready"),
            any(),
            eq("/evaluations/H88/pdf"),
            nullable(Long.class));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void onTeamLocked_persistsForAllRecipients() {
    TeamLockedEvent event =
        new TeamLockedEvent(List.of(40L, 41L, 42L), 200L, "Alpha", 10L, "Essay 1");

    listener.onTeamLocked(event);

    verify(notificationService, times(3))
        .create(
            anyLong(),
            eq(NotificationType.TEAM_LOCKED),
            any(),
            any(),
            any(),
            nullable(Long.class));
  }

  @Test
  void onDeadlineWarning_publishesDueAtInAssignmentDueSoonEmail() {
    Instant dueAt = Instant.parse("2026-03-25T10:00:00Z");
    DeadlineWarningEvent event =
        new DeadlineWarningEvent(List.of(31L), 12L, "Project 2", "CSC101", 24, dueAt);

    Notification saved =
        Notification.builder()
            .id(1L)
            .userId(31L)
            .type(NotificationType.DEADLINE_WARNING_24H)
            .build();
    when(notificationService.tryCreateDedupedDeadlineReminder(
            eq(31L), eq(NotificationType.DEADLINE_WARNING_24H), eq(12L), any(), any(), any()))
        .thenReturn(Optional.of(saved));
    when(userService.findUserById(31L))
        .thenReturn(Optional.of(user(31L, "student@test.local", "Ada", "Lovelace")));

    listener.onDeadlineWarning(event);

    ArgumentCaptor<AssignmentDueSoonEmailEvent> emailCaptor =
        ArgumentCaptor.forClass(AssignmentDueSoonEmailEvent.class);
    verify(eventPublisher).publishEvent(emailCaptor.capture());
    AssignmentDueSoonEmailEvent published = emailCaptor.getValue();
    assertEquals("student@test.local", published.getRecipientEmail());
    assertNotNull(published.getDueAt());
    assertEquals(dueAt, published.getDueAt());
    assertEquals("H12", published.getAssignmentHashId());

    verify(notificationService)
        .tryCreateDedupedDeadlineReminder(
            eq(31L), eq(NotificationType.DEADLINE_WARNING_24H), eq(12L), any(), any(), any());
  }

  @Test
  void onDeadlineWarning_48HourPath_setsCorrectNotificationType() {
    Instant dueAt = Instant.parse("2026-03-26T10:00:00Z");
    DeadlineWarningEvent event =
        new DeadlineWarningEvent(List.of(41L), 13L, "Project 3", "CSC201", 48, dueAt);

    Notification saved =
        Notification.builder()
            .id(2L)
            .userId(41L)
            .type(NotificationType.DEADLINE_WARNING_48H)
            .build();
    when(notificationService.tryCreateDedupedDeadlineReminder(
            eq(41L), eq(NotificationType.DEADLINE_WARNING_48H), eq(13L), any(), any(), any()))
        .thenReturn(Optional.of(saved));

    listener.onDeadlineWarning(event);

    verify(notificationService)
        .tryCreateDedupedDeadlineReminder(
            eq(41L), eq(NotificationType.DEADLINE_WARNING_48H), eq(13L), any(), any(), any());
  }

  @Test
  void onTeamInvite_pushFailureStillPersistsAndReturns() {
    TeamInviteEvent event = new TeamInviteEvent(55L, 900L, "Beta", "Maria", 44L, "Capstone");
    when(userService.findUserById(55L))
        .thenReturn(Optional.of(user(55L, "beta@test.local", "Beta", "User")));
    doThrow(new RuntimeException("offline"))
        .when(messagingTemplate)
        .convertAndSendToUser(eq("55"), eq("/queue/notifications"), any());

    assertDoesNotThrow(() -> listener.onTeamInvite(event));
    verify(notificationService)
        .create(
            eq(55L),
            eq(NotificationType.TEAM_INVITE),
            any(),
            any(),
            any(),
            eq(900L));
    verify(eventPublisher).publishEvent(any(TeamInviteReceivedEmailEvent.class));
  }
}
