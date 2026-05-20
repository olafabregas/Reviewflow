package com.reviewflow.messaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.course.repository.CourseRepository;
import com.reviewflow.messaging.exception.MessagingClientException;
import com.reviewflow.messaging.repository.ConversationParticipantRepository;
import com.reviewflow.messaging.repository.ConversationRepository;
import com.reviewflow.messaging.repository.MessageRepository;
import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import com.reviewflow.infrastructure.ratelimit.RateLimitTestFixtures;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.MSG_CREATE;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.MSG_SEND;
import com.reviewflow.infrastructure.storage.FileSecurityValidator;
import com.reviewflow.infrastructure.storage.S3Service;
import com.reviewflow.messaging.repository.ConversationListMetadataView;
import com.reviewflow.shared.exception.StorageException;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.Conversation;
import com.reviewflow.shared.domain.ConversationParticipant;
import com.reviewflow.shared.domain.ConversationType;
import com.reviewflow.shared.domain.Course;
import com.reviewflow.shared.domain.Message;
import com.reviewflow.shared.domain.Team;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.BusinessRuleException;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mockito.InOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class MessagingServiceTest {

  private static final long COURSE_ID = 42L;
  private static final long CONV_ID = 100L;
  private static final long SENDER_ID = 1L;
  private static final long OTHER_USER_ID = 2L;

  @Mock private ConversationRepository conversationRepository;
  @Mock private ConversationParticipantRepository participantRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
  @Mock private CourseInstructorRepository courseInstructorRepository;
  @Mock private UserRepository userRepository;
  @Mock private AuditService auditService;
  @Mock private S3Service s3Service;
  @Mock private FileSecurityValidator fileSecurityValidator;
  @Mock private HashidService hashidService;
  @Mock private SimpMessagingTemplate messagingTemplate;
  @Mock private RateLimitService rateLimitService;
  @Mock private ReviewFlowMetrics reviewFlowMetrics;
  @Mock private ObjectMapper objectMapper;
  @Mock private MessagingPersistenceService messagingPersistenceService;

  @InjectMocks private MessagingService messagingService;

  @BeforeEach
  void injectConfig() {
    ReflectionTestUtils.setField(messagingService, "editWindowHours", 48);
    ReflectionTestUtils.setField(messagingService, "fetchPageSize", 50);
    ReflectionTestUtils.setField(messagingService, "previewMaxChars", 80);
    ReflectionTestUtils.setField(messagingService, "maxAttachmentsPerMessage", 5);
    lenient()
        .when(rateLimitService.tryConsume(anyString(), any(), any()))
        .thenAnswer(
            inv ->
                RateLimitTestFixtures.allowed(
                    inv.getArgument(1, com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.class)));
  }

  private Course activeCourse() {
    return Course.builder()
        .id(COURSE_ID)
        .code("CS101")
        .name("Test Course")
        .isArchived(false)
        .build();
  }

  private User user(Long id, UserRole role) {
    return User.builder()
        .id(id)
        .email("u" + id + "@test.local")
        .passwordHash("x")
        .firstName("First" + id)
        .lastName("Last" + id)
        .role(role)
        .isActive(true)
        .build();
  }

  private Conversation directConversation() {
    return Conversation.builder()
        .id(CONV_ID)
        .course(activeCourse())
        .conversationType(ConversationType.DIRECT)
        .createdAt(Instant.now())
        .build();
  }

  @Test
  void createDirect_courseArchived_throwsConflict() {
    Long initiatorId = 10L;
    Long recipientId = 11L;
    Course archived =
        Course.builder().id(COURSE_ID).code("CS101").name("Archived").isArchived(true).build();
    when(userRepository.findById(initiatorId)).thenReturn(Optional.of(user(initiatorId, UserRole.STUDENT)));
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(archived));

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () ->
                messagingService.createDirectConversation(
                    COURSE_ID, initiatorId, recipientId, "127.0.0.1"));
    assertEquals("COURSE_ARCHIVED", ex.getCode());
  }

  @Test
  void createDirect_cannotMessageSelf_throwsBusinessRule() {
    Long userId = 10L;
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, UserRole.STUDENT)));

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () -> messagingService.createDirectConversation(COURSE_ID, userId, userId, "127.0.0.1"));
    assertEquals("CANNOT_MESSAGE_SELF", ex.getCode());
  }

  @Test
  void createDirect_nonStudentInstructorInitiator_throwsForbidden() {
    Long initiatorId = 10L;
    when(userRepository.findById(initiatorId))
        .thenReturn(Optional.of(user(initiatorId, UserRole.SYSTEM_ADMIN)));

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () ->
                messagingService.createDirectConversation(
                    COURSE_ID, initiatorId, 11L, "127.0.0.1"));
    assertEquals("FORBIDDEN", ex.getCode());
  }

  @Test
  void createDirect_initiatorNotEnrolled_throwsNotEnrolled() {
    Long initiatorId = 10L;
    Long recipientId = 11L;
    when(userRepository.findById(initiatorId)).thenReturn(Optional.of(user(initiatorId, UserRole.STUDENT)));
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(activeCourse()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, initiatorId)).thenReturn(false);
    when(courseInstructorRepository.existsByCourseIdAndUserId(COURSE_ID, initiatorId)).thenReturn(false);

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () ->
                messagingService.createDirectConversation(
                    COURSE_ID, initiatorId, recipientId, "127.0.0.1"));
    assertEquals("NOT_ENROLLED", ex.getCode());
  }

  @Test
  void createDirect_recipientNotInCourse_throwsBusinessRule() {
    Long initiatorId = 10L;
    Long recipientId = 11L;
    when(userRepository.findById(initiatorId)).thenReturn(Optional.of(user(initiatorId, UserRole.STUDENT)));
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(activeCourse()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, initiatorId)).thenReturn(true);
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, recipientId)).thenReturn(false);
    when(courseInstructorRepository.existsByCourseIdAndUserId(COURSE_ID, recipientId)).thenReturn(false);

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () ->
                messagingService.createDirectConversation(
                    COURSE_ID, initiatorId, recipientId, "127.0.0.1"));
    assertEquals("RECIPIENT_NOT_IN_COURSE", ex.getCode());
  }

  @Test
  void createDirect_existing_returnsAlreadyExisted() {
    Long initiatorId = 10L;
    Long recipientId = 11L;
    Conversation existing = directConversation();
    when(userRepository.findById(initiatorId)).thenReturn(Optional.of(user(initiatorId, UserRole.STUDENT)));
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(activeCourse()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, initiatorId)).thenReturn(true);
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, recipientId)).thenReturn(true);
    when(conversationRepository.findDirectConversation(
            COURSE_ID, initiatorId, recipientId, ConversationType.DIRECT))
        .thenReturn(Optional.of(existing));
    when(participantRepository.findByConversationId(CONV_ID))
        .thenReturn(
            List.of(
                ConversationParticipant.builder().conversationId(CONV_ID).userId(initiatorId).build(),
                ConversationParticipant.builder().conversationId(CONV_ID).userId(recipientId).build()));
    when(userRepository.findById(recipientId)).thenReturn(Optional.of(user(recipientId, UserRole.STUDENT)));
    when(hashidService.encode(CONV_ID)).thenReturn("hcnv");
    when(hashidService.encode(initiatorId)).thenReturn("hu1");
    when(hashidService.encode(recipientId)).thenReturn("hu2");

    var response =
        messagingService.createDirectConversation(COURSE_ID, initiatorId, recipientId, "127.0.0.1");

    assertTrue(response.isAlreadyExisted());
    assertEquals("hcnv", response.getConversationId());
    verify(conversationRepository, never()).save(any());
  }

  @Test
  void sendMessage_emptyContentAndNoFiles_throwsEmptyMessage() {
    Conversation conv = directConversation();
    when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conv));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () -> messagingService.sendMessage(CONV_ID, SENDER_ID, "   ", List.of(), "127.0.0.1"));
    assertEquals("EMPTY_MESSAGE", ex.getCode());
  }

  @Test
  void sendMessage_notParticipant_throwsNotAParticipant() {
    Conversation conv = directConversation();
    when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conv));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID)).thenReturn(Optional.empty());

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () -> messagingService.sendMessage(CONV_ID, SENDER_ID, "hi", null, "127.0.0.1"));
    assertEquals("NOT_A_PARTICIPANT", ex.getCode());
  }

  @Test
  void sendMessage_participantButUnenrolled_throwsNotEnrolled() {
    Conversation conv = directConversation();
    when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conv));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(false);
    when(courseInstructorRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(false);

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () -> messagingService.sendMessage(CONV_ID, SENDER_ID, "hello", null, "127.0.0.1"));
    assertEquals("NOT_ENROLLED", ex.getCode());
  }

  @Test
  void sendMessage_tooManyAttachments_throws() {
    List<MultipartFile> six = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      MultipartFile f = org.mockito.Mockito.mock(MultipartFile.class);
      when(f.isEmpty()).thenReturn(false);
      six.add(f);
    }
    Conversation conv = directConversation();
    when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conv));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () -> messagingService.sendMessage(CONV_ID, SENDER_ID, "x", six, "127.0.0.1"));
    assertEquals("TOO_MANY_ATTACHMENTS", ex.getCode());
  }

  @Test
  void sendMessage_textOnly_persistsAndNotifiesOthers() throws IOException {
    Conversation conv = directConversation();
    when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conv));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);
    Message persisted =
        Message.builder()
            .id(500L)
            .conversation(conv)
            .content("hello")
            .sentAt(Instant.now())
            .attachments(new ArrayList<>())
            .build();
    when(messagingPersistenceService.persistMessageWithoutAttachments(conv, SENDER_ID, "hello"))
        .thenReturn(persisted);
    when(userRepository.findById(SENDER_ID))
        .thenReturn(Optional.of(user(SENDER_ID, UserRole.STUDENT)));
    when(participantRepository.findByConversationId(CONV_ID))
        .thenReturn(
            List.of(
                ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build(),
                ConversationParticipant.builder()
                    .conversationId(CONV_ID)
                    .userId(OTHER_USER_ID)
                    .build()));
    when(hashidService.encode(CONV_ID)).thenReturn("hc");
    when(hashidService.encode(500L)).thenReturn("hm");

    var response =
        messagingService.sendMessage(CONV_ID, SENDER_ID, "  hello  ", null, "127.0.0.1");

    assertEquals("hm", response.getMessageId());
    assertEquals("hello", response.getContent());
    verify(messagingTemplate)
        .convertAndSendToUser(eq(String.valueOf(OTHER_USER_ID)), eq("/queue/messages"), any());
    verify(auditService)
        .log(eq(SENDER_ID), eq("MESSAGE_SENT"), eq("CONVERSATION"), eq(CONV_ID), anyMap(), eq("127.0.0.1"));
  }

  @Test
  void editMessage_editWindowExpired_throws() {
    Message msg =
        Message.builder()
            .id(77L)
            .conversation(directConversation())
            .sender(user(SENDER_ID, UserRole.STUDENT))
            .content("old")
            .isDeleted(false)
            .sentAt(Instant.now().minus(Duration.ofHours(10)))
            .build();
    when(messageRepository.findById(77L)).thenReturn(Optional.of(msg));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);
    ReflectionTestUtils.setField(messagingService, "editWindowHours", 1);

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () -> messagingService.editMessage(77L, SENDER_ID, "new text", "127.0.0.1"));
    assertEquals("EDIT_WINDOW_EXPIRED", ex.getCode());
  }

  @Test
  void editMessage_deletedMessage_throwsConflict() {
    Message msg =
        Message.builder()
            .id(77L)
            .conversation(directConversation())
            .sender(user(SENDER_ID, UserRole.STUDENT))
            .content("old")
            .isDeleted(true)
            .sentAt(Instant.now())
            .build();
    when(messageRepository.findById(77L)).thenReturn(Optional.of(msg));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () -> messagingService.editMessage(77L, SENDER_ID, "new text", "127.0.0.1"));
    assertEquals("MESSAGE_DELETED", ex.getCode());
  }

  @Test
  void editMessage_notOwner_throwsForbidden() {
    Message msg =
        Message.builder()
            .id(77L)
            .conversation(directConversation())
            .sender(user(99L, UserRole.STUDENT))
            .content("old")
            .isDeleted(false)
            .sentAt(Instant.now())
            .build();
    when(messageRepository.findById(77L)).thenReturn(Optional.of(msg));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () -> messagingService.editMessage(77L, SENDER_ID, "new text", "127.0.0.1"));
    assertEquals("CANNOT_EDIT_OTHERS_MESSAGE", ex.getCode());
  }

  @Test
  void editMessage_blankContent_throwsBusinessRule() {
    Message msg =
        Message.builder()
            .id(77L)
            .conversation(directConversation())
            .sender(user(SENDER_ID, UserRole.STUDENT))
            .content("old")
            .isDeleted(false)
            .sentAt(Instant.now())
            .build();
    when(messageRepository.findById(77L)).thenReturn(Optional.of(msg));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () -> messagingService.editMessage(77L, SENDER_ID, "   ", "127.0.0.1"));
    assertEquals("EMPTY_MESSAGE", ex.getCode());
  }

  @Test
  void deleteMessage_nonSenderNonAdmin_throws() {
    Message msg =
        Message.builder()
            .id(88L)
            .conversation(directConversation())
            .sender(user(99L, UserRole.STUDENT))
            .content("secret")
            .isDeleted(false)
            .sentAt(Instant.now())
            .build();
    when(messageRepository.findById(88L)).thenReturn(Optional.of(msg));

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () -> messagingService.deleteMessage(88L, SENDER_ID, false, "127.0.0.1"));
    assertEquals("CANNOT_DELETE_OTHERS_MESSAGE", ex.getCode());
  }

  @Test
  void deleteMessage_systemAdminCanDeleteOthersMessage() {
    Message msg =
        Message.builder()
            .id(88L)
            .conversation(directConversation())
            .sender(user(99L, UserRole.STUDENT))
            .content("secret")
            .isDeleted(false)
            .sentAt(Instant.now())
            .build();
    when(messageRepository.findById(88L)).thenReturn(Optional.of(msg));
    when(participantRepository.findByConversationId(CONV_ID)).thenReturn(List.of());
    when(hashidService.encode(88L)).thenReturn("hm88");

    messagingService.deleteMessage(88L, SENDER_ID, true, "127.0.0.1");

    verify(messageRepository).save(msg);
    assertTrue(Boolean.TRUE.equals(msg.getIsDeleted()));
  }

  @Test
  void getMessages_unknownConversation_throwsNotFound() {
    when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> messagingService.getMessagesForParticipant(999L, SENDER_ID, null, null));
  }

  @Test
  void listCourseConversations_notMemberNorParticipant_throwsNotACourseMember() {
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(false);
    when(courseInstructorRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(false);
    when(conversationRepository.existsByCourseIdAndParticipantUserId(COURSE_ID, SENDER_ID))
        .thenReturn(false);

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () -> messagingService.listCourseConversations(COURSE_ID, SENDER_ID));
    assertEquals("NOT_A_COURSE_MEMBER", ex.getCode());
  }

  @Test
  void listCourseConversations_formerParticipant_returnsTheirConversations() {
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(false);
    when(courseInstructorRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(false);
    when(conversationRepository.existsByCourseIdAndParticipantUserId(COURSE_ID, SENDER_ID))
        .thenReturn(true);
    Conversation c = directConversation();
    when(conversationRepository.findDistinctByCourseIdAndParticipantUserIdWithDetails(
            COURSE_ID, SENDER_ID))
        .thenReturn(List.of(c));
    ConversationParticipant participant =
        ConversationParticipant.builder()
            .conversationId(CONV_ID)
            .userId(SENDER_ID)
            .user(user(SENDER_ID, UserRole.STUDENT))
            .build();
    when(participantRepository.findByConversationIdInWithUser(List.of(CONV_ID)))
        .thenReturn(List.of(participant));
    ConversationListMetadataView metadata =
        new ConversationListMetadataView() {
          @Override
          public Long getConversationId() {
            return CONV_ID;
          }

          @Override
          public Long getUnreadCount() {
            return 0L;
          }

          @Override
          public Long getLatestMessageId() {
            return null;
          }

          @Override
          public String getLatestContent() {
            return null;
          }

          @Override
          public Instant getLatestSentAt() {
            return null;
          }

          @Override
          public Boolean getLatestIsDeleted() {
            return null;
          }

          @Override
          public Long getLatestSenderId() {
            return null;
          }

          @Override
          public String getLatestSenderFirstName() {
            return null;
          }

          @Override
          public String getLatestSenderLastName() {
            return null;
          }

          @Override
          public String getLatestSenderEmail() {
            return null;
          }

          @Override
          public String getLatestSenderAvatarUrl() {
            return null;
          }

          @Override
          public Long getHasAttachments() {
            return 0L;
          }
        };
    when(messageRepository.findListMetadataByConversationIds(List.of(CONV_ID), SENDER_ID))
        .thenReturn(List.of(metadata));
    when(hashidService.encode(CONV_ID)).thenReturn("hc");
    when(hashidService.encode(SENDER_ID)).thenReturn("hu");

    var list = messagingService.listCourseConversations(COURSE_ID, SENDER_ID);

    assertEquals(1, list.size());
    assertEquals("hc", list.get(0).getId());
    verify(messageRepository, never()).countUnreadInConversation(anyLong(), anyLong());
    verify(messageRepository, never()).findLatestMessage(anyLong(), any());
  }

  @Test
  void listCourseConversations_manyConversations_usesAtMostThreeRepositoryQueries() {
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);
    List<Conversation> convs = new ArrayList<>();
    for (long id = 1; id <= 12; id++) {
      convs.add(
          Conversation.builder()
              .id(id)
              .course(activeCourse())
              .conversationType(ConversationType.DIRECT)
              .createdAt(Instant.now())
              .build());
    }
    when(conversationRepository.findDistinctByCourseIdAndParticipantUserIdWithDetails(
            COURSE_ID, SENDER_ID))
        .thenReturn(convs);
    when(participantRepository.findByConversationIdInWithUser(any())).thenReturn(List.of());
    when(messageRepository.findListMetadataByConversationIds(any(), eq(SENDER_ID)))
        .thenReturn(List.of());
    when(hashidService.encode(anyLong())).thenAnswer(inv -> "h" + inv.getArgument(0));

    messagingService.listCourseConversations(COURSE_ID, SENDER_ID);

    verify(conversationRepository, times(1))
        .findDistinctByCourseIdAndParticipantUserIdWithDetails(COURSE_ID, SENDER_ID);
    verify(participantRepository, times(1)).findByConversationIdInWithUser(any());
    verify(messageRepository, times(1)).findListMetadataByConversationIds(any(), eq(SENDER_ID));
    verify(messageRepository, never()).countUnreadInConversation(anyLong(), anyLong());
    verify(messageRepository, never()).findLatestMessage(anyLong(), any());
    verify(participantRepository, never()).findByConversationId(anyLong());
  }

  @Test
  void sendMessage_whenRateLimited_throwsTooManyRequests() {
    Conversation conv = directConversation();
    when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conv));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);
    when(userRepository.findById(SENDER_ID)).thenReturn(Optional.of(user(SENDER_ID, UserRole.STUDENT)));
    when(rateLimitService.tryConsume(eq(String.valueOf(SENDER_ID)), eq(MSG_SEND), any()))
        .thenReturn(RateLimitTestFixtures.denied(MSG_SEND));

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () -> messagingService.sendMessage(CONV_ID, SENDER_ID, "hello", null, "127.0.0.1"));

    assertEquals("MESSAGING_RATE_LIMIT_EXCEEDED", ex.getCode());
    verify(messagingPersistenceService, never()).persistMessageWithoutAttachments(any(), anyLong(), any());
  }

  @Test
  void sendMessage_withAttachment_uploadsOutsidePersistenceTransaction() throws IOException {
    MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("doc.pdf");
    when(file.getContentType()).thenReturn("application/pdf");
    when(file.getSize()).thenReturn(4L);
    when(file.getInputStream())
        .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
    Conversation conv = directConversation();
    when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conv));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);
    when(userRepository.findById(SENDER_ID)).thenReturn(Optional.of(user(SENDER_ID, UserRole.STUDENT)));
    Message persisted =
        Message.builder()
            .id(500L)
            .conversation(conv)
            .content("see file")
            .sentAt(Instant.now())
            .attachments(new ArrayList<>())
            .build();
    when(messagingPersistenceService.persistMessageWithoutAttachments(conv, SENDER_ID, "see file"))
        .thenReturn(persisted);
    Message withAttachment =
        Message.builder()
            .id(500L)
            .conversation(conv)
            .content("see file")
            .sentAt(persisted.getSentAt())
            .attachments(new ArrayList<>())
            .build();
    when(messagingPersistenceService.attachUploadedFiles(eq(persisted), any())).thenReturn(withAttachment);
    when(participantRepository.findByConversationId(CONV_ID)).thenReturn(List.of());
    when(hashidService.encode(CONV_ID)).thenReturn("hc");
    when(hashidService.encode(500L)).thenReturn("hm");

    messagingService.sendMessage(CONV_ID, SENDER_ID, "see file", List.of(file), "127.0.0.1");

    InOrder order = inOrder(messagingPersistenceService, s3Service);
    order.verify(messagingPersistenceService).persistMessageWithoutAttachments(conv, SENDER_ID, "see file");
    order.verify(s3Service).putObject(anyString(), any(byte[].class), anyString());
    order.verify(messagingPersistenceService).attachUploadedFiles(eq(persisted), any());
    verify(messageRepository, never()).save(any());
  }

  @Test
  void sendMessage_s3Failure_deletesPersistedMessage() throws IOException {
    MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("doc.pdf");
    when(file.getContentType()).thenReturn("application/pdf");
    when(file.getSize()).thenReturn(4L);
    when(file.getInputStream())
        .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
    Conversation conv = directConversation();
    when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conv));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, SENDER_ID)).thenReturn(true);
    when(userRepository.findById(SENDER_ID)).thenReturn(Optional.of(user(SENDER_ID, UserRole.STUDENT)));
    Message persisted =
        Message.builder()
            .id(500L)
            .conversation(conv)
            .content(null)
            .sentAt(Instant.now())
            .attachments(new ArrayList<>())
            .build();
    when(messagingPersistenceService.persistMessageWithoutAttachments(conv, SENDER_ID, null))
        .thenReturn(persisted);
    when(s3Service.putObject(anyString(), any(byte[].class), anyString()))
        .thenThrow(new StorageException("upload failed", new RuntimeException("s3")));
    when(hashidService.encode(CONV_ID)).thenReturn("hc");
    when(hashidService.encode(500L)).thenReturn("hm");

    assertThrows(
        StorageException.class,
        () -> messagingService.sendMessage(CONV_ID, SENDER_ID, null, List.of(file), "127.0.0.1"));

    verify(messagingPersistenceService).deleteMessageAndAttachments(500L);
    verify(messagingPersistenceService, never()).attachUploadedFiles(any(), any());
    verify(s3Service, never()).deleteObject(anyString());
  }

  @Test
  void createDirect_whenCreateRateLimited_throwsTooManyRequests() {
    Long initiatorId = 10L;
    Long recipientId = 11L;
    when(userRepository.findById(initiatorId)).thenReturn(Optional.of(user(initiatorId, UserRole.STUDENT)));
    when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(activeCourse()));
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, initiatorId)).thenReturn(true);
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(COURSE_ID, recipientId)).thenReturn(true);
    when(conversationRepository.findDirectConversation(
            COURSE_ID, initiatorId, recipientId, ConversationType.DIRECT))
        .thenReturn(Optional.empty());
    when(rateLimitService.tryConsume(eq(String.valueOf(initiatorId)), eq(MSG_CREATE), any()))
        .thenReturn(RateLimitTestFixtures.denied(MSG_CREATE));

    MessagingClientException ex =
        assertThrows(
            MessagingClientException.class,
            () ->
                messagingService.createDirectConversation(
                    COURSE_ID, initiatorId, recipientId, "127.0.0.1"));

    assertEquals("MESSAGING_RATE_LIMIT_EXCEEDED", ex.getCode());
    verify(conversationRepository, never()).save(any());
  }

  @Test
  void getMessages_capsClientLimitAtFetchPageSize() {
    ReflectionTestUtils.setField(messagingService, "fetchPageSize", 10);
    Conversation conv = directConversation();
    when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conv));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().conversationId(CONV_ID).userId(SENDER_ID).build()));
    when(messageRepository.findPageForConversation(eq(CONV_ID), isNull(), eq(PageRequest.of(0, 11))))
        .thenReturn(List.of());

    messagingService.getMessagesForParticipant(CONV_ID, SENDER_ID, null, 999);

    verify(messageRepository).findPageForConversation(eq(CONV_ID), isNull(), eq(PageRequest.of(0, 11)));
  }

  @Test
  void ensureTeamChatForNewTeam_whenAlreadyPresent_doesNotSaveAgain() {
    Team team =
        Team.builder()
            .id(55L)
            .assignment(Assignment.builder().id(1L).course(activeCourse()).build())
            .build();
    when(conversationRepository.findByTeam_Id(55L))
        .thenReturn(Optional.of(Conversation.builder().id(9L).build()));

    messagingService.ensureTeamChatForNewTeam(team, SENDER_ID);

    verify(conversationRepository, never()).save(any());
  }

  @Test
  void addTeamMemberToTeamChat_whenMissingConversation_skipsQuietly() {
    when(conversationRepository.findByTeam_Id(55L)).thenReturn(Optional.empty());

    messagingService.addTeamMemberToTeamChat(55L, OTHER_USER_ID, "Joiner");

    verify(participantRepository, never()).save(any());
  }

  @Test
  void addTeamMemberToTeamChat_whenAlreadyParticipant_skipsSave() {
    Conversation conv = Conversation.builder().id(CONV_ID).build();
    when(conversationRepository.findByTeam_Id(55L)).thenReturn(Optional.of(conv));
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, OTHER_USER_ID))
        .thenReturn(Optional.of(ConversationParticipant.builder().build()));

    messagingService.addTeamMemberToTeamChat(55L, OTHER_USER_ID, "Joiner");

    verify(participantRepository, never()).save(any());
  }

  @Test
  void listMessagesForModeration_includesDeletedMessageContent() {
    Conversation conv = directConversation();
    when(conversationRepository.findById(CONV_ID)).thenReturn(Optional.of(conv));
    Message deleted =
        Message.builder()
            .id(1L)
            .conversation(conv)
            .sender(user(SENDER_ID, UserRole.STUDENT))
            .content("removed text")
            .isDeleted(true)
            .sentAt(Instant.now())
            .attachments(new ArrayList<>())
            .build();
    when(messageRepository.findAllByConversationIdForModerationWithDetails(
            eq(CONV_ID), any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(deleted)));
    when(hashidService.encode(anyLong())).thenAnswer(inv -> "h" + inv.getArgument(0));

    var page = messagingService.listMessagesForModeration(CONV_ID, 0, 50);

    assertEquals(1, page.getContent().size());
    assertEquals("removed text", page.getContent().get(0).getContent());
    assertTrue(page.getContent().get(0).isDeleted());
  }

  @Test
  void markConversationRead_nonParticipant_throws() {
    when(participantRepository.findByConversationIdAndUserId(CONV_ID, SENDER_ID)).thenReturn(Optional.empty());

    assertThrows(
        MessagingClientException.class,
        () -> messagingService.markConversationRead(CONV_ID, SENDER_ID));
  }
}
