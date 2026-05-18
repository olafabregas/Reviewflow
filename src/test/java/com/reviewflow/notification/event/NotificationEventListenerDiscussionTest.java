package com.reviewflow.notification.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.discussion.event.DiscussionPublishedEvent;
import com.reviewflow.discussion.event.DiscussionReminderBatchEvent;
import com.reviewflow.discussion.event.DiscussionReplyEvent;
import com.reviewflow.infrastructure.email.event.DiscussionInstructorReplyEmailEvent;
import com.reviewflow.infrastructure.email.event.DiscussionPublishedEmailEvent;
import com.reviewflow.infrastructure.email.event.DiscussionReminderEmailEvent;
import com.reviewflow.notification.repository.NotificationRepository;
import com.reviewflow.notification.service.NotificationService;
import com.reviewflow.shared.domain.CourseEnrollment;
import com.reviewflow.shared.domain.Notification;
import com.reviewflow.shared.domain.NotificationType;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerDiscussionTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private SimpMessagingTemplate messagingTemplate;
  @Mock private CacheManager cacheManager;
  @Mock private HashidService hashidService;
  @Mock private UserRepository userRepository;
  @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
  @Mock private NotificationService notificationService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private NotificationEventListener listener;

  private void stubHashids() {
    when(hashidService.encode(anyLong())).thenAnswer(inv -> "H" + inv.getArgument(0));
  }

  private void stubSaveAndPush() {
    stubHashids();
    when(cacheManager.getCache(any())).thenReturn(null);
    AtomicLong id = new AtomicLong(5000L);
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(
            inv -> {
              Notification n = inv.getArgument(0);
              if (n.getId() == null) {
                n.setId(id.getAndIncrement());
              }
              return n;
            });
  }

  @Test
  void onDiscussionPublished_emailsEnrolledStudentsOnly() {
    stubSaveAndPush();
    User student =
        User.builder()
            .id(10L)
            .email("student@test.local")
            .firstName("Ada")
            .lastName("Lovelace")
            .role(UserRole.STUDENT)
            .build();
    User instructor =
        User.builder()
            .id(20L)
            .email("instructor@test.local")
            .firstName("Grace")
            .lastName("Hopper")
            .role(UserRole.INSTRUCTOR)
            .build();
    when(courseEnrollmentRepository.findWithUserByCourseId(1L))
        .thenReturn(
            List.of(
                CourseEnrollment.builder().user(student).build(),
                CourseEnrollment.builder().user(instructor).build()));

    Instant dueAt = Instant.parse("2026-05-01T18:00:00Z");
    listener.onDiscussionPublished(
        new DiscussionPublishedEvent(1L, "CSC301", 99L, "Week 3 Reflection", dueAt));

    verify(notificationRepository, times(1)).save(any(Notification.class));

    ArgumentCaptor<DiscussionPublishedEmailEvent> emailCaptor =
        ArgumentCaptor.forClass(DiscussionPublishedEmailEvent.class);
    verify(eventPublisher).publishEvent(emailCaptor.capture());
    DiscussionPublishedEmailEvent email = emailCaptor.getValue();
    assertEquals("student@test.local", email.getRecipientEmail());
    assertEquals("Week 3 Reflection", email.getDiscussionTitle());
    assertEquals("CSC301", email.getCourseCode());
    assertEquals(dueAt, email.getDueAt());
    assertEquals("H99", email.getDiscussionHashId());
  }

  @Test
  void onDiscussionReply_instructorToStudent_publishesInstructorReplyEmail() {
    stubSaveAndPush();
    when(userRepository.findById(30L))
        .thenReturn(
            Optional.of(
                User.builder()
                    .id(30L)
                    .email("student@test.local")
                    .firstName("Alan")
                    .lastName("Turing")
                    .role(UserRole.STUDENT)
                    .build()));

    listener.onDiscussionReply(
        new DiscussionReplyEvent(
            77L,
            30L,
            UserRole.STUDENT,
            40L,
            UserRole.INSTRUCTOR,
            "Prof. Smith",
            "Week 3 Reflection",
            "Thanks for your post."));

    verify(notificationRepository).save(any(Notification.class));

    ArgumentCaptor<DiscussionInstructorReplyEmailEvent> emailCaptor =
        ArgumentCaptor.forClass(DiscussionInstructorReplyEmailEvent.class);
    verify(eventPublisher).publishEvent(emailCaptor.capture());
    DiscussionInstructorReplyEmailEvent email = emailCaptor.getValue();
    assertEquals("student@test.local", email.getRecipientEmail());
    assertEquals("Prof. Smith", email.getReplierName());
    assertEquals("Thanks for your post.", email.getReplySnippet());
    assertEquals("H77", email.getDiscussionHashId());
  }

  @Test
  void onDiscussionReply_studentPeerReply_noEmail() {
    stubSaveAndPush();
    listener.onDiscussionReply(
        new DiscussionReplyEvent(
            77L,
            30L,
            UserRole.STUDENT,
            41L,
            UserRole.STUDENT,
            "Peer Student",
            "Week 3 Reflection",
            "I agree."));

    verify(notificationRepository).save(any(Notification.class));
    verify(eventPublisher, never()).publishEvent(any(DiscussionInstructorReplyEmailEvent.class));
    verify(eventPublisher, never()).publishEvent(any(DiscussionPublishedEmailEvent.class));
  }

  @Test
  void onDiscussionReminderBatch_newNotification_publishesReminderEmail() {
    stubHashids();
    Instant dueAt = Instant.parse("2026-05-02T12:00:00Z");
    Notification saved =
        Notification.builder()
            .id(9001L)
            .userId(50L)
            .type(NotificationType.DISCUSSION_REMINDER)
            .title("Discussion due soon")
            .message("msg")
            .build();
    when(notificationService.tryCreateDedupedDiscussionReminder(
            eq(50L), eq(88L), any(), any(), eq("/discussions/{id}")))
        .thenReturn(Optional.of(saved));

    listener.onDiscussionReminderBatch(
        new DiscussionReminderBatchEvent(
            88L,
            "Week 4 Prompt",
            dueAt,
            List.of(new DiscussionReminderBatchEvent.ReminderRecipient(50L, "ada@test.local", "Ada"))));

    ArgumentCaptor<DiscussionReminderEmailEvent> emailCaptor =
        ArgumentCaptor.forClass(DiscussionReminderEmailEvent.class);
    verify(eventPublisher).publishEvent(emailCaptor.capture());
    DiscussionReminderEmailEvent email = emailCaptor.getValue();
    assertEquals("ada@test.local", email.getRecipientEmail());
    assertEquals("Ada", email.getRecipientName());
    assertEquals("Week 4 Prompt", email.getDiscussionTitle());
    assertEquals(dueAt, email.getDueAt());
    assertEquals("H88", email.getDiscussionHashId());
  }

  @Test
  void onDiscussionReminderBatch_dedupedSkip_noEmail() {
    when(notificationService.tryCreateDedupedDiscussionReminder(
            eq(50L), eq(88L), any(), any(), eq("/discussions/{id}")))
        .thenReturn(Optional.empty());

    listener.onDiscussionReminderBatch(
        new DiscussionReminderBatchEvent(
            88L,
            "Week 4 Prompt",
            Instant.parse("2026-05-02T12:00:00Z"),
            List.of(new DiscussionReminderBatchEvent.ReminderRecipient(50L, "ada@test.local", "Ada"))));

    verify(eventPublisher, never()).publishEvent(any(DiscussionReminderEmailEvent.class));
  }
}
