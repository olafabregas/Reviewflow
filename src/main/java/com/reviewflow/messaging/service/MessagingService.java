package com.reviewflow.messaging.service;

import com.reviewflow.admin.service.AuditService;
import com.reviewflow.config.MessagingRedisConfig;
import com.reviewflow.messaging.RedisMessagePayload;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.course.repository.CourseRepository;
import com.reviewflow.infrastructure.config.ValidationConfig;
import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.infrastructure.ratelimit.RateLimitResult;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.MSG_CREATE;
import static com.reviewflow.infrastructure.ratelimit.RateLimitStrategy.MSG_SEND;
import com.reviewflow.infrastructure.storage.FileSecurityValidator;
import com.reviewflow.infrastructure.storage.S3KeyBuilder;
import com.reviewflow.infrastructure.storage.S3Service;
import com.reviewflow.messaging.dto.response.ConversationListItemDto;
import com.reviewflow.messaging.dto.response.CreateDirectConversationResponse;
import com.reviewflow.messaging.dto.response.EditMessageResponse;
import com.reviewflow.messaging.dto.response.LastMessagePreviewDto;
import com.reviewflow.messaging.dto.response.MessageAttachmentDto;
import com.reviewflow.messaging.dto.response.MessageDto;
import com.reviewflow.messaging.dto.response.MessagesPageResponse;
import com.reviewflow.messaging.dto.response.ModerationConversationSummaryDto;
import com.reviewflow.messaging.dto.response.ParticipantSummaryDto;
import com.reviewflow.messaging.dto.response.SendMessageResponse;
import com.reviewflow.messaging.exception.MessagingClientException;
import com.reviewflow.messaging.repository.ConversationListMetadataView;
import com.reviewflow.messaging.repository.ConversationModerationStatsView;
import com.reviewflow.messaging.repository.ConversationParticipantRepository;
import com.reviewflow.messaging.repository.ConversationRepository;
import com.reviewflow.messaging.repository.MessageRepository;
import com.reviewflow.shared.domain.Conversation;
import com.reviewflow.shared.domain.ConversationParticipant;
import com.reviewflow.shared.domain.ConversationType;
import com.reviewflow.shared.domain.Course;
import com.reviewflow.shared.domain.Message;
import com.reviewflow.shared.domain.MessageAttachment;
import com.reviewflow.shared.domain.Team;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.BusinessRuleException;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.shared.exception.FileUploadTimeoutException;
import com.reviewflow.shared.exception.StorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingService {

  private final ConversationRepository conversationRepository;
  private final ConversationParticipantRepository participantRepository;
  private final MessageRepository messageRepository;
  private final CourseRepository courseRepository;
  private final CourseEnrollmentRepository courseEnrollmentRepository;
  private final CourseInstructorRepository courseInstructorRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final S3Service s3Service;
  private final FileSecurityValidator fileSecurityValidator;
  private final HashidService hashidService;
  private final SimpMessagingTemplate messagingTemplate;
  private final RateLimitService rateLimitService;
  private final ReviewFlowMetrics reviewFlowMetrics;
  private final ObjectMapper objectMapper;
  private final MessagingPersistenceService messagingPersistenceService;

  @Autowired(required = false)
  private StringRedisTemplate redisTemplate;

  @Value("${redis.messaging.pubsub.enabled:false}")
  private boolean pubSubEnabled;

  @Value("${message.edit-window-hours:1}")
  private int editWindowHours;

  @Value("${message.fetch-page-size:50}")
  private int fetchPageSize;

  @Value("${message.preview-max-chars:80}")
  private int previewMaxChars;

  @Value("${message.max-attachments-per-message:5}")
  private int maxAttachmentsPerMessage;

  @Value("${async.upload.timeout-seconds:60}")
  private int uploadTimeoutSeconds;

  @jakarta.annotation.Resource(name = "uploadExecutor")
  private Executor uploadExecutor;

  /**
   * NOTE: WebSocket push via SimpMessagingTemplate works on single-node only. Before horizontal
   * scaling, configure Spring WebSocket with Redis pub/sub broker relay.
   */
  @Transactional
  public void ensureTeamChatForNewTeam(Team team, Long creatorUserId) {
    if (conversationRepository.findByTeam_Id(team.getId()).isPresent()) {
      return;
    }
    Course course = team.getAssignment().getCourse();
    Conversation conv =
        Conversation.builder()
            .course(course)
            .conversationType(ConversationType.TEAM_CHAT)
            .team(team)
            .createdAt(Instant.now())
            .build();
    conv = conversationRepository.save(conv);
    saveParticipant(conv.getId(), creatorUserId, Instant.now());
  }

  @Transactional
  public void addTeamMemberToTeamChat(Long teamId, Long acceptedUserId, String joinerDisplayName) {
    Optional<Conversation> convOpt = conversationRepository.findByTeam_Id(teamId);
    if (convOpt.isEmpty()) {
      log.warn("No TEAM_CHAT conversation for teamId={}", teamId);
      return;
    }
    Conversation conv = convOpt.get();
    if (participantRepository.findByConversationIdAndUserId(conv.getId(), acceptedUserId).isPresent()) {
      return;
    }
    saveParticipant(conv.getId(), acceptedUserId, Instant.now());
    List<ConversationParticipant> others =
        participantRepository.findByConversationId(conv.getId()).stream()
            .filter(p -> !p.getUserId().equals(acceptedUserId))
            .toList();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "PARTICIPANT_JOINED");
    payload.put("conversationId", hashidService.encode(conv.getId()));
    payload.put("userName", joinerDisplayName);
    for (ConversationParticipant p : others) {
      pushToRecipient(String.valueOf(p.getUserId()), payload);
    }
  }

  @Transactional
  public CreateDirectConversationResponse createDirectConversation(
      Long courseId, Long initiatorId, Long recipientId, String ip) {
    User initiator =
        userRepository.findById(initiatorId).orElseThrow(() -> new ResourceNotFoundException("User"));
    if (initiator.getRole() != UserRole.STUDENT && initiator.getRole() != UserRole.INSTRUCTOR) {
      throw MessagingClientException.forbidden(
          "FORBIDDEN", "Only students and instructors can start direct conversations");
    }
    if (Objects.equals(initiatorId, recipientId)) {
      throw new BusinessRuleException("Cannot message yourself", "CANNOT_MESSAGE_SELF");
    }
    Course course =
        courseRepository
            .findById(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    assertCourseNotArchived(course);
    if (!isCourseMember(initiatorId, courseId)) {
      throw MessagingClientException.forbidden("NOT_ENROLLED", "You are not enrolled in this course");
    }
    if (!isCourseMember(recipientId, courseId)) {
      throw new BusinessRuleException("Recipient is not in this course", "RECIPIENT_NOT_IN_COURSE");
    }
    Optional<Conversation> existing =
        conversationRepository.findDirectConversation(
            courseId, initiatorId, recipientId, ConversationType.DIRECT);
    if (existing.isPresent()) {
      return toCreateDirectResponse(existing.get(), true);
    }
    RateLimitResult createLimit =
        rateLimitService.tryConsume(
            String.valueOf(initiatorId), MSG_CREATE, initiator.getRole());
    if (!createLimit.allowed()) {
      throw MessagingClientException.tooManyRequests(
          "MESSAGING_RATE_LIMIT_EXCEEDED",
          "Too many new conversations. Retry after " + createLimit.retryAfterSeconds() + " seconds.");
    }
    Conversation conv =
        Conversation.builder()
            .course(course)
            .conversationType(ConversationType.DIRECT)
            .team(null)
            .createdAt(Instant.now())
            .build();
    conv = conversationRepository.save(conv);
    saveParticipant(conv.getId(), initiatorId, Instant.now());
    saveParticipant(conv.getId(), recipientId, Instant.now());
    User recipient =
        userRepository.findById(recipientId).orElseThrow(() -> new ResourceNotFoundException("User"));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "NEW_CONVERSATION");
    payload.put("conversationId", hashidService.encode(conv.getId()));
    payload.put("initiatorName", displayName(initiator));
    pushToRecipient(String.valueOf(recipientId), payload);
    return toCreateDirectResponse(conv, false);
  }

  public SendMessageResponse sendMessage(
      Long conversationId, Long senderId, String content, List<MultipartFile> files, String ip)
      throws IOException {
    Conversation conv = loadConversation(conversationId);
    assertParticipant(conv.getId(), senderId);
    assertCourseNotArchived(conv.getCourse());
    assertCanWriteAsCourseMember(senderId, conv.getCourse().getId());
    List<MultipartFile> fileList = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
    if ((content == null || content.isBlank()) && fileList.isEmpty()) {
      throw new BusinessRuleException("Message cannot be empty", "EMPTY_MESSAGE");
    }
    if (fileList.size() > maxAttachmentsPerMessage) {
      throw new BusinessRuleException("Too many attachments", "TOO_MANY_ATTACHMENTS");
    }
    for (MultipartFile f : fileList) {
      fileSecurityValidator.validateMessageAttachment(f, ValidationConfig.MESSAGE);
    }
    User sender = userRepository.findById(senderId).orElseThrow();
    RateLimitResult sendLimit =
        rateLimitService.tryConsume(String.valueOf(senderId), MSG_SEND, sender.getRole());
    if (!sendLimit.allowed()) {
      throw MessagingClientException.tooManyRequests(
          "MESSAGING_RATE_LIMIT_EXCEEDED",
          "Too many messages. Retry after " + sendLimit.retryAfterSeconds() + " seconds.");
    }
    String trimmedContent = content != null && !content.isBlank() ? content.trim() : null;
    List<PendingMessageAttachment> pendingAttachments = stageAttachments(fileList);
    Message msg =
        messagingPersistenceService.persistMessageWithoutAttachments(conv, senderId, trimmedContent);
    Long messageId = msg.getId();
    String hashedConv = hashidService.encode(conv.getId());
    String hashedMsg = hashidService.encode(messageId);
    List<UploadedMessageAttachment> uploaded = new ArrayList<>();
    try {
      for (PendingMessageAttachment pending : pendingAttachments) {
        String key =
            S3KeyBuilder.messageAttachmentKey(hashedConv, hashedMsg, pending.fileName());
        uploadBytesToS3(key, pending.bytes(), pending.contentType());
        uploaded.add(
            new UploadedMessageAttachment(
                pending.fileName(), pending.fileSizeBytes(), pending.contentType(), key));
      }
      if (!uploaded.isEmpty()) {
        msg = messagingPersistenceService.attachUploadedFiles(msg, uploaded);
      }
    } catch (RuntimeException e) {
      for (UploadedMessageAttachment u : uploaded) {
        s3Service.deleteObject(u.storagePath());
      }
      messagingPersistenceService.deleteMessageAndAttachments(messageId);
      throw e;
    }
    List<MessageAttachmentDto> attachmentDtos = toAttachmentDtos(msg, true);
    auditService.log(
        senderId,
        "MESSAGE_SENT",
        "CONVERSATION",
        conv.getId(),
        Map.of("conversationId", conv.getId(), "hasAttachments", !fileList.isEmpty()),
        ip);
    Map<String, Object> ws = new LinkedHashMap<>();
    ws.put("type", "NEW_MESSAGE");
    ws.put("conversationId", hashidService.encode(conv.getId()));
    ws.put("messageId", hashidService.encode(msg.getId()));
    ws.put("senderName", displayName(sender));
    ws.put("senderAvatarUrl", sender.getAvatarUrl());
    ws.put("contentPreview", preview(content));
    ws.put("hasAttachments", !fileList.isEmpty());
    ws.put("sentAt", msg.getSentAt().toString());
    for (ConversationParticipant p : participantRepository.findByConversationId(conv.getId())) {
      if (!p.getUserId().equals(senderId)) {
        pushToRecipient(String.valueOf(p.getUserId()), ws);
      }
    }
    return SendMessageResponse.builder()
        .messageId(hashidService.encode(msg.getId()))
        .content(msg.getContent())
        .attachments(attachmentDtos)
        .sentAt(msg.getSentAt())
        .editedAt(null)
        .build();
  }

  @Transactional
  public EditMessageResponse editMessage(Long messageId, Long editorId, String newContent, String ip) {
    Message msg =
        messageRepository
            .findById(messageId)
            .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));
    Conversation conv = msg.getConversation();
    assertParticipant(conv.getId(), editorId);
    assertCourseNotArchived(conv.getCourse());
    assertCanWriteAsCourseMember(editorId, conv.getCourse().getId());
    if (!msg.getSender().getId().equals(editorId)) {
      throw MessagingClientException.forbidden(
          "CANNOT_EDIT_OTHERS_MESSAGE", "You can only edit your own messages");
    }
    if (Boolean.TRUE.equals(msg.getIsDeleted())) {
      throw MessagingClientException.conflict("MESSAGE_DELETED", "Cannot edit a deleted message");
    }
    Instant deadline = msg.getSentAt().plus(Duration.ofHours(editWindowHours));
    if (Instant.now().isAfter(deadline)) {
      throw MessagingClientException.forbidden(
          "EDIT_WINDOW_EXPIRED",
          "Messages can only be edited within "
              + editWindowHours
              + " hour(s) of sending. This message can no longer be edited.");
    }
    if (newContent == null || newContent.isBlank()) {
      throw new BusinessRuleException("Content cannot be blank", "EMPTY_MESSAGE");
    }
    msg.setContent(newContent.trim());
    msg.setEditedAt(Instant.now());
    messageRepository.save(msg);
    auditService.log(
        editorId,
        "MESSAGE_EDITED",
        "MESSAGE",
        msg.getId(),
        Map.of("messageId", msg.getId(), "conversationId", conv.getId()),
        ip);
    Map<String, Object> ws = new LinkedHashMap<>();
    ws.put("type", "MESSAGE_EDITED");
    ws.put("conversationId", hashidService.encode(conv.getId()));
    ws.put("messageId", hashidService.encode(msg.getId()));
    ws.put("newContent", msg.getContent());
    ws.put("editedAt", msg.getEditedAt().toString());
    for (ConversationParticipant p : participantRepository.findByConversationId(conv.getId())) {
      pushToRecipient(String.valueOf(p.getUserId()), ws);
    }
    return EditMessageResponse.builder()
        .messageId(hashidService.encode(msg.getId()))
        .content(msg.getContent())
        .editedAt(msg.getEditedAt())
        .attachments(toAttachmentDtos(msg, true))
        .build();
  }

  @Transactional(readOnly = true)
  public MessagesPageResponse getMessagesForParticipant(
      Long conversationId, Long userId, Long beforeMessageId, Integer limitParam) {
    Conversation conv = loadConversation(conversationId);
    assertParticipant(conv.getId(), userId);
    int requested = limitParam != null ? limitParam : fetchPageSize;
    int effectiveLimit = Math.min(Math.max(requested, 1), fetchPageSize);
    List<Message> page =
        messageRepository.findPageForConversation(
            conv.getId(), beforeMessageId, PageRequest.of(0, effectiveLimit + 1));
    boolean hasMore = page.size() > effectiveLimit;
    List<Message> slice = hasMore ? page.subList(0, effectiveLimit) : page;
    List<Message> asc = new ArrayList<>(slice);
    asc.sort(Comparator.comparing(Message::getSentAt).thenComparing(Message::getId));
    long maxFetchedId =
        asc.stream().mapToLong(Message::getId).max().orElse(0L);
    if (maxFetchedId > 0) {
      participantRepository.updateLastRead(conv.getId(), userId, maxFetchedId);
    }
    List<MessageDto> dtos = new ArrayList<>();
    for (Message m : asc) {
      dtos.add(toMessageDto(m, false, true));
    }
    String oldestId =
        asc.isEmpty() ? null : hashidService.encode(asc.get(0).getId());
    return MessagesPageResponse.builder()
        .messages(dtos)
        .hasMore(hasMore)
        .oldestMessageId(oldestId)
        .build();
  }

  @Transactional(readOnly = true)
  public List<ConversationListItemDto> listCourseConversations(Long courseId, Long userId) {
    boolean isCurrentMember = isCourseMember(userId, courseId);
    boolean isFormerParticipant =
        conversationRepository.existsByCourseIdAndParticipantUserId(courseId, userId);
    if (!isCurrentMember && !isFormerParticipant) {
      throw MessagingClientException.forbidden(
          "NOT_A_COURSE_MEMBER", "Not a member of this course");
    }
    List<Conversation> convs =
        conversationRepository.findDistinctByCourseIdAndParticipantUserIdWithDetails(courseId, userId);
    if (convs.isEmpty()) {
      return List.of();
    }
    List<Long> conversationIds = convs.stream().map(Conversation::getId).toList();
    Map<Long, List<ConversationParticipant>> participantsByConversation =
        participantRepository.findByConversationIdInWithUser(conversationIds).stream()
            .collect(Collectors.groupingBy(ConversationParticipant::getConversationId));
    Map<Long, ConversationListMetadataView> metadataByConversation =
        messageRepository.findListMetadataByConversationIds(conversationIds, userId).stream()
            .collect(
                Collectors.toMap(
                    ConversationListMetadataView::getConversationId,
                    Function.identity(),
                    (a, b) -> a));
    List<ConversationListItemDto> out = new ArrayList<>();
    for (Conversation c : convs) {
      out.add(
          toConversationListItem(
              c,
              participantsByConversation.getOrDefault(c.getId(), List.of()),
              metadataByConversation.get(c.getId())));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public long sumUnreadForUserAcrossConversations(Long userId) {
    return messageRepository.countTotalUnreadForUser(userId);
  }

  @Transactional
  public long markConversationRead(Long conversationId, Long userId) {
    assertParticipant(conversationId, userId);
    long maxId = messageRepository.findMaxMessageId(conversationId);
    if (maxId > 0) {
      participantRepository.updateLastRead(conversationId, userId, maxId);
    }
    return messageRepository.countUnreadInConversation(conversationId, userId);
  }

  @Transactional
  public void deleteMessage(Long messageId, Long currentUserId, boolean systemAdmin, String ip) {
    Message msg =
        messageRepository
            .findById(messageId)
            .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));
    Conversation conv = msg.getConversation();
    assertCourseNotArchived(conv.getCourse());
    boolean isSender = msg.getSender().getId().equals(currentUserId);
    if (!isSender && !systemAdmin) {
      throw MessagingClientException.forbidden(
          "CANNOT_DELETE_OTHERS_MESSAGE", "You cannot delete another user's message");
    }
    if (isSender && !systemAdmin) {
      assertCanWriteAsCourseMember(currentUserId, conv.getCourse().getId());
    }
    msg.setIsDeleted(true);
    msg.setContent(null);
    messageRepository.save(msg);
    Map<String, Object> ws = new LinkedHashMap<>();
    ws.put("type", "MESSAGE_DELETED");
    ws.put("messageId", hashidService.encode(msg.getId()));
    for (ConversationParticipant p : participantRepository.findByConversationId(conv.getId())) {
      pushToRecipient(String.valueOf(p.getUserId()), ws);
    }
  }

  @Transactional(readOnly = true)
  public List<ModerationConversationSummaryDto> listConversationsForModeration(Long courseId) {
    List<Conversation> convs = conversationRepository.findByCourse_Id(courseId);
    if (convs.isEmpty()) {
      return List.of();
    }
    List<Long> conversationIds = convs.stream().map(Conversation::getId).toList();
    Map<Long, List<ConversationParticipant>> participantsByConversation =
        participantRepository.findByConversationIdInWithUser(conversationIds).stream()
            .collect(Collectors.groupingBy(ConversationParticipant::getConversationId));
    Map<Long, ConversationModerationStatsView> statsByConversation =
        messageRepository.findModerationStatsByConversationIds(conversationIds).stream()
            .collect(
                Collectors.toMap(
                    ConversationModerationStatsView::getConversationId,
                    Function.identity(),
                    (a, b) -> a));
    List<ModerationConversationSummaryDto> out = new ArrayList<>();
    for (Conversation c : convs) {
      ConversationModerationStatsView stats = statsByConversation.get(c.getId());
      long count = stats != null ? stats.getMessageCount() : 0L;
      Instant last =
          stats != null && stats.getLastActivity() != null
              ? stats.getLastActivity()
              : c.getCreatedAt();
      out.add(
          ModerationConversationSummaryDto.builder()
              .id(hashidService.encode(c.getId()))
              .type(c.getConversationType())
              .participants(
                  participantSummaries(
                      participantsByConversation.getOrDefault(c.getId(), List.of())))
              .messageCount(count)
              .lastActivity(last)
              .build());
    }
    return out;
  }

  @Transactional(readOnly = true)
  public List<MessageDto> listMessagesForModeration(Long conversationId) {
    loadConversation(conversationId);
    List<Message> msgs =
        messageRepository.findAllByConversationIdForModerationWithDetails(conversationId);
    return msgs.stream().map(m -> toMessageDto(m, true, true)).collect(Collectors.toList());
  }

  private Conversation loadConversation(Long id) {
    return conversationRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Conversation", id));
  }

  private void assertParticipant(Long conversationId, Long userId) {
    if (participantRepository.findByConversationIdAndUserId(conversationId, userId).isEmpty()) {
      throw MessagingClientException.forbidden("NOT_A_PARTICIPANT", "Not a participant in this conversation");
    }
  }

  private void assertCourseNotArchived(Course course) {
    if (Boolean.TRUE.equals(course.getIsArchived())) {
      throw MessagingClientException.conflict("COURSE_ARCHIVED", "This course is archived");
    }
  }

  private boolean isCourseMember(Long userId, Long courseId) {
    return courseEnrollmentRepository.existsByCourseIdAndUserId(courseId, userId)
        || courseInstructorRepository.existsByCourseIdAndUserId(courseId, userId);
  }

  private void assertCanWriteAsCourseMember(Long userId, Long courseId) {
    if (!isCourseMember(userId, courseId)) {
      throw MessagingClientException.forbidden("NOT_ENROLLED", "You are no longer enrolled in this course");
    }
  }

  private void saveParticipant(Long conversationId, Long userId, Instant joinedAt) {
    ConversationParticipant p =
        ConversationParticipant.builder()
            .conversationId(conversationId)
            .userId(userId)
            .conversation(conversationRepository.getReferenceById(conversationId))
            .user(userRepository.getReferenceById(userId))
            .joinedAt(joinedAt)
            .lastReadMessageId(null)
            .build();
    participantRepository.save(p);
  }

  private CreateDirectConversationResponse toCreateDirectResponse(Conversation conv, boolean existed) {
    return CreateDirectConversationResponse.builder()
        .conversationId(hashidService.encode(conv.getId()))
        .type(conv.getConversationType().name())
        .alreadyExisted(existed)
        .participants(participantSummaries(conv))
        .build();
  }

  private List<ParticipantSummaryDto> participantSummaries(Conversation conv) {
    return participantSummaries(participantRepository.findByConversationId(conv.getId()));
  }

  private List<ParticipantSummaryDto> participantSummaries(
      List<ConversationParticipant> participants) {
    return participants.stream()
        .map(
            cp -> {
              User u = cp.getUser();
              if (u == null) {
                u = userRepository.findById(cp.getUserId()).orElseThrow();
              }
              return ParticipantSummaryDto.builder()
                  .id(hashidService.encode(u.getId()))
                  .name(displayName(u))
                  .avatarUrl(u.getAvatarUrl())
                  .build();
            })
        .collect(Collectors.toList());
  }

  private ConversationListItemDto toConversationListItem(
      Conversation c,
      List<ConversationParticipant> participants,
      ConversationListMetadataView metadata) {
    Course course = c.getCourse();
    String teamName = c.getTeam() != null ? c.getTeam().getName() : null;
    long unread = metadata != null && metadata.getUnreadCount() != null ? metadata.getUnreadCount() : 0L;
    LastMessagePreviewDto last = null;
    if (metadata != null && metadata.getLatestMessageId() != null) {
      String previewContent =
          Boolean.TRUE.equals(metadata.getLatestIsDeleted()) ? null : metadata.getLatestContent();
      User sender =
          User.builder()
              .id(metadata.getLatestSenderId())
              .firstName(metadata.getLatestSenderFirstName())
              .lastName(metadata.getLatestSenderLastName())
              .email(metadata.getLatestSenderEmail())
              .avatarUrl(metadata.getLatestSenderAvatarUrl())
              .build();
      last =
          LastMessagePreviewDto.builder()
              .content(preview(previewContent))
              .senderName(displayName(sender))
              .sentAt(metadata.getLatestSentAt())
              .hasAttachments(metadata.getHasAttachments() != null && metadata.getHasAttachments() > 0)
              .build();
    }
    return ConversationListItemDto.builder()
        .id(hashidService.encode(c.getId()))
        .type(c.getConversationType())
        .teamName(teamName)
        .courseCode(course.getCode())
        .participants(participantSummaries(participants))
        .lastMessage(last)
        .unreadCount(unread)
        .build();
  }

  private List<PendingMessageAttachment> stageAttachments(List<MultipartFile> fileList)
      throws IOException {
    List<PendingMessageAttachment> staged = new ArrayList<>();
    for (MultipartFile f : fileList) {
      byte[] bytes;
      long size = f.getSize();
      if (size >= 0) {
        try (InputStream in = f.getInputStream()) {
          bytes = in.readAllBytes();
        }
      } else {
        bytes = f.getBytes();
        size = bytes.length;
      }
      String contentType =
          f.getContentType() != null ? f.getContentType() : "application/octet-stream";
      staged.add(
          new PendingMessageAttachment(
              f.getOriginalFilename() != null ? f.getOriginalFilename() : "file",
              size,
              contentType,
              bytes));
    }
    return staged;
  }

  private MessageDto toMessageDto(Message m, boolean moderationIncludeDeletedContent, boolean includePresignedUrls) {
    User s = m.getSender();
    String content = m.getContent();
    if (Boolean.TRUE.equals(m.getIsDeleted()) && !moderationIncludeDeletedContent) {
      content = null;
    }
    return MessageDto.builder()
        .id(hashidService.encode(m.getId()))
        .senderId(hashidService.encode(s.getId()))
        .senderName(displayName(s))
        .senderAvatarUrl(s.getAvatarUrl())
        .content(content)
        .attachments(toAttachmentDtos(m, includePresignedUrls && !Boolean.TRUE.equals(m.getIsDeleted())))
        .isDeleted(Boolean.TRUE.equals(m.getIsDeleted()))
        .sentAt(m.getSentAt())
        .editedAt(m.getEditedAt())
        .build();
  }

  private List<MessageAttachmentDto> toAttachmentDtos(Message m, boolean presign) {
    if (m.getAttachments() == null || m.getAttachments().isEmpty()) {
      return List.of();
    }
    List<MessageAttachmentDto> list = new ArrayList<>();
    for (MessageAttachment a : m.getAttachments()) {
      String url = presign ? s3Service.generatePresignedDownloadUrl(a.getStoragePath()) : null;
      list.add(
          MessageAttachmentDto.builder()
              .id(hashidService.encode(a.getId()))
              .fileName(a.getFileName())
              .fileSizeBytes(a.getFileSizeBytes())
              .contentType(a.getContentType())
              .downloadUrl(url)
              .build());
    }
    return list;
  }

  private String displayName(User u) {
    String fn = u.getFirstName() != null ? u.getFirstName() : "";
    String ln = u.getLastName() != null ? u.getLastName() : "";
    String full = (fn + " " + ln).trim();
    return full.isEmpty() ? u.getEmail() : full;
  }

  private String preview(String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    String t = content.trim();
    return t.length() <= previewMaxChars ? t : t.substring(0, previewMaxChars);
  }

  private void pushToRecipient(String rawUserId, Object payload) {
    if (pubSubEnabled && redisTemplate != null) {
      try {
        redisTemplate.convertAndSend(
            MessagingRedisConfig.MESSAGING_CHANNEL,
            objectMapper.writeValueAsString(new RedisMessagePayload(rawUserId, payload)));
      } catch (Exception e) {
        log.warn("Redis pub/sub push failed userId={}: {}", rawUserId, e.getMessage());
        reviewFlowMetrics.recordWebSocketPushFailed("messaging-redis");
      }
    } else {
      try {
        messagingTemplate.convertAndSendToUser(rawUserId, "/queue/messages", payload);
      } catch (Exception e) {
        log.warn("WebSocket push failed userId={}: {}", rawUserId, e.getMessage());
        reviewFlowMetrics.recordWebSocketPushFailed("messaging");
      }
    }
  }

  private void uploadBytesToS3(String key, byte[] bytes, String contentType) {
    try {
      CompletableFuture.runAsync(
              () ->
                  s3Service.putObject(
                      key, new ByteArrayInputStream(bytes), bytes.length, contentType),
              uploadExecutor)
          .get(uploadTimeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      s3Service.deleteObjectSilently(key);
      throw new FileUploadTimeoutException(uploadTimeoutSeconds);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      throw new StorageException("Attachment upload failed", cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StorageException("Attachment upload interrupted", e);
    }
  }
}
