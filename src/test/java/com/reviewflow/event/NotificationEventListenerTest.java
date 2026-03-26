package com.reviewflow.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.reviewflow.event.email.AssignmentDueSoonEmailEvent;
import com.reviewflow.event.email.EvaluationPublishedEmailEvent;
import com.reviewflow.event.email.SubmissionReceivedEmailEvent;
import com.reviewflow.event.email.TeamInviteReceivedEmailEvent;
import com.reviewflow.model.entity.Notification;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.enums.NotificationType;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.NotificationRepository;
import com.reviewflow.repository.UserRepository;
import com.reviewflow.service.HashidService;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;
    @Mock
    private HashidService hashidService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationEventListener listener;

    private User user(Long id, String email, String firstName, String lastName) {
        return User.builder().id(id).email(email).firstName(firstName).lastName(lastName).build();
    }

    private void stubNotificationSave() {
        AtomicLong id = new AtomicLong(1000L);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            if (n.getId() == null) {
                n.setId(id.getAndIncrement());
            }
            return n;
        });
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(cacheManager.getCache(any())).thenReturn(null);
        when(hashidService.encode(anyLong())).thenAnswer(invocation -> "H" + invocation.getArgument(0));
        stubNotificationSave();
    }

    @Test
    void onTeamInvite_persistsNotificationAndPublishesEmail() {
        TeamInviteEvent event = new TeamInviteEvent(10L, 77L, "Team Delta", "Alex", 12L, "Essay 1");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "invitee@test.local", "Ada", "Lovelace")));

        listener.onTeamInvite(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertEquals(NotificationType.TEAM_INVITE, notification.getType());
        assertEquals(10L, notification.getUserId());
        assertEquals(77L, notification.getTargetId());

        ArgumentCaptor<TeamInviteReceivedEmailEvent> emailCaptor = ArgumentCaptor.forClass(TeamInviteReceivedEmailEvent.class);
        verify(eventPublisher).publishEvent(emailCaptor.capture());
        TeamInviteReceivedEmailEvent email = emailCaptor.getValue();
        assertEquals("invitee@test.local", email.getRecipientEmail());
        assertEquals("H77", email.getTeamMemberHashId());
    }

    @Test
    void onSubmissionUploaded_publishesSubmissionEmailWithSubmissionHash() {
        SubmissionUploadedEvent event = new SubmissionUploadedEvent(
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

        when(userRepository.findById(20L)).thenReturn(Optional.of(user(20L, "instructor1@test.local", "Prof", "Kim")));
        when(userRepository.findById(21L)).thenReturn(Optional.of(user(21L, "instructor2@test.local", "Grace", "Hopper")));

        listener.onSubmissionUploaded(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        List<Notification> saved = notificationCaptor.getAllValues();
        assertEquals(2, saved.size());
        assertEquals(NotificationType.NEW_SUBMISSION, saved.get(0).getType());
        assertEquals(NotificationType.NEW_SUBMISSION, saved.get(1).getType());

        ArgumentCaptor<SubmissionReceivedEmailEvent> emailCaptor = ArgumentCaptor.forClass(SubmissionReceivedEmailEvent.class);
        verify(eventPublisher, times(2)).publishEvent(emailCaptor.capture());
        List<SubmissionReceivedEmailEvent> emails = emailCaptor.getAllValues();
        assertEquals(2, emails.size());
        assertEquals("H501", emails.get(0).getSubmissionHashId());
        assertEquals("H501", emails.get(1).getSubmissionHashId());
        assertEquals(2, emails.get(0).getVersionNumber());
    }

    @Test
    void onEvaluationPublished_individualPath_persistsSingleNotificationAndEmail() {
        EvaluationPublishedEvent event = new EvaluationPublishedEvent(
                List.of(200L, 201L),
                200L,
                88L,
                18L,
                "Project 2",
                92,
                100,
                SubmissionType.INDIVIDUAL);

        when(userRepository.findById(200L)).thenReturn(Optional.of(user(200L, "student@test.local", "Ada", "Lovelace")));

        listener.onEvaluationPublished(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertEquals(200L, notificationCaptor.getValue().getUserId());
        assertEquals(NotificationType.FEEDBACK_PUBLISHED, notificationCaptor.getValue().getType());

        ArgumentCaptor<EvaluationPublishedEmailEvent> emailCaptor = ArgumentCaptor.forClass(EvaluationPublishedEmailEvent.class);
        verify(eventPublisher).publishEvent(emailCaptor.capture());
        EvaluationPublishedEmailEvent email = emailCaptor.getValue();
        assertEquals("student@test.local", email.getRecipientEmail());
        assertEquals("H88", email.getEvaluationHashId());
        assertEquals(92, email.getTotalScore());
    }

    @Test
    void onEvaluationPublished_teamPath_persistsAndPublishesForAllRecipients() {
        EvaluationPublishedEvent event = new EvaluationPublishedEvent(
                List.of(300L, 301L),
                null,
                99L,
                19L,
                "Project Team",
                75,
                100,
                SubmissionType.TEAM);

        when(userRepository.findById(300L)).thenReturn(Optional.of(user(300L, "team1@test.local", "Alan", "Turing")));
        when(userRepository.findById(301L)).thenReturn(Optional.of(user(301L, "team2@test.local", "Katherine", "Johnson")));

        listener.onEvaluationPublished(event);

        verify(notificationRepository, times(2)).save(any(Notification.class));

        ArgumentCaptor<EvaluationPublishedEmailEvent> emailCaptor = ArgumentCaptor.forClass(EvaluationPublishedEmailEvent.class);
        verify(eventPublisher, times(2)).publishEvent(emailCaptor.capture());
        List<EvaluationPublishedEmailEvent> emails = emailCaptor.getAllValues();
        assertEquals(2, emails.size());
        assertEquals("H99", emails.get(0).getEvaluationHashId());
        assertEquals("H99", emails.get(1).getEvaluationHashId());
    }

    @Test
    void onTeamLocked_persistsForAllRecipients() {
        TeamLockedEvent event = new TeamLockedEvent(List.of(40L, 41L, 42L), 200L, "Alpha", 10L, "Essay 1");

        listener.onTeamLocked(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(3)).save(notificationCaptor.capture());
        List<Notification> notifications = notificationCaptor.getAllValues();
        List<Long> ids = new ArrayList<>();
        for (Notification n : notifications) {
            assertEquals(NotificationType.TEAM_LOCKED, n.getType());
            ids.add(n.getUserId());
        }
        assertEquals(List.of(40L, 41L, 42L), ids);
    }

    @Test
    void onDeadlineWarning_publishesDueAtInAssignmentDueSoonEmail() {
        Instant dueAt = Instant.parse("2026-03-25T10:00:00Z");
        DeadlineWarningEvent event = new DeadlineWarningEvent(
                List.of(31L),
                12L,
                "Project 2",
                "CSC101",
                24,
                dueAt);

        when(userRepository.findById(31L)).thenReturn(Optional.of(user(31L, "student@test.local", "Ada", "Lovelace")));

        listener.onDeadlineWarning(event);

        ArgumentCaptor<AssignmentDueSoonEmailEvent> emailCaptor = ArgumentCaptor.forClass(AssignmentDueSoonEmailEvent.class);
        verify(eventPublisher).publishEvent(emailCaptor.capture());
        AssignmentDueSoonEmailEvent published = emailCaptor.getValue();
        assertEquals("student@test.local", published.getRecipientEmail());
        assertNotNull(published.getDueAt());
        assertEquals(dueAt, published.getDueAt());
        assertEquals("H12", published.getAssignmentHashId());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertEquals(NotificationType.DEADLINE_WARNING_24H, notificationCaptor.getValue().getType());
    }

    @Test
    void onDeadlineWarning_48HourPath_setsCorrectNotificationType() {
        Instant dueAt = Instant.parse("2026-03-26T10:00:00Z");
        DeadlineWarningEvent event = new DeadlineWarningEvent(
                List.of(41L),
                13L,
                "Project 3",
                "CSC201",
                48,
                dueAt);

        when(userRepository.findById(41L)).thenReturn(Optional.of(user(41L, "student2@test.local", "Grace", "Hopper")));

        listener.onDeadlineWarning(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertEquals(NotificationType.DEADLINE_WARNING_48H, notificationCaptor.getValue().getType());
    }

    @Test
    void onTeamInvite_pushFailureStillPersistsAndReturns() {
        TeamInviteEvent event = new TeamInviteEvent(55L, 900L, "Beta", "Maria", 44L, "Capstone");
        when(userRepository.findById(55L)).thenReturn(Optional.of(user(55L, "beta@test.local", "Beta", "User")));
        doThrow(new RuntimeException("offline")).when(messagingTemplate)
                .convertAndSendToUser(eq("55"), eq("/queue/notifications"), any());

        assertDoesNotThrow(() -> listener.onTeamInvite(event));
        verify(notificationRepository).save(any(Notification.class));
        verify(eventPublisher).publishEvent(any(TeamInviteReceivedEmailEvent.class));
    }
}
