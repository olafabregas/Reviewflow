package com.reviewflow.messaging.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.admin.service.AuditService;
import com.reviewflow.config.MessagingRedisConfig;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.course.repository.CourseRepository;
import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.infrastructure.security.RateLimiterService;
import com.reviewflow.infrastructure.storage.FileSecurityValidator;
import com.reviewflow.infrastructure.storage.S3Service;
import com.reviewflow.messaging.repository.ConversationParticipantRepository;
import com.reviewflow.messaging.repository.ConversationRepository;
import com.reviewflow.messaging.repository.MessageRepository;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MessagingServicePushRecipientTest {

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
  @Mock private RateLimiterService rateLimiterService;
  @Mock private ReviewFlowMetrics reviewFlowMetrics;
  @Mock private StringRedisTemplate redisTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private MessagingService messagingService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(messagingService, "objectMapper", objectMapper);
    ReflectionTestUtils.setField(messagingService, "pubSubEnabled", false);
    ReflectionTestUtils.setField(messagingService, "redisTemplate", null);
  }

  @Test
  void pushToRecipient_usesDirectWebSocketWhenPubsubDisabled() {
    Map<String, Object> payload = Map.of("type", "TEST");

    ReflectionTestUtils.invokeMethod(messagingService, "pushToRecipient", "42", payload);

    verify(messagingTemplate).convertAndSendToUser("42", "/queue/messages", payload);
    verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
  }

  @Test
  void pushToRecipient_usesRedisChannelWhenPubsubEnabled() {
    Map<String, Object> payload = Map.of("type", "TEST");
    ReflectionTestUtils.setField(messagingService, "pubSubEnabled", true);
    ReflectionTestUtils.setField(messagingService, "redisTemplate", redisTemplate);

    ReflectionTestUtils.invokeMethod(messagingService, "pushToRecipient", "42", payload);

    verify(redisTemplate)
        .convertAndSend(eq(MessagingRedisConfig.MESSAGING_CHANNEL), anyString());
    verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
  }
}
