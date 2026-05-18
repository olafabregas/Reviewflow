package com.reviewflow.messaging.service;

import com.reviewflow.messaging.repository.MessageRepository;
import com.reviewflow.shared.domain.Conversation;
import com.reviewflow.shared.domain.Message;
import com.reviewflow.shared.domain.MessageAttachment;
import com.reviewflow.shared.domain.User;
import com.reviewflow.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short transactional boundaries for messaging persistence (keeps S3 I/O out of transactions). */
@Service
@RequiredArgsConstructor
public class MessagingPersistenceService {

  private final MessageRepository messageRepository;
  private final UserRepository userRepository;

  @Transactional
  public Message persistMessageWithoutAttachments(
      Conversation conversation, Long senderId, String content) {
    Message msg =
        Message.builder()
            .conversation(conversation)
            .sender(userRepository.getReferenceById(senderId))
            .content(content)
            .isDeleted(false)
            .sentAt(Instant.now())
            .editedAt(null)
            .build();
    return messageRepository.save(msg);
  }

  @Transactional
  public Message attachUploadedFiles(Message message, List<UploadedMessageAttachment> uploaded) {
    for (UploadedMessageAttachment u : uploaded) {
      MessageAttachment att =
          MessageAttachment.builder()
              .message(message)
              .fileName(u.fileName())
              .fileSizeBytes(u.fileSizeBytes())
              .storagePath(u.storagePath())
              .contentType(u.contentType())
              .uploadedAt(Instant.now())
              .build();
      message.getAttachments().add(att);
    }
    return messageRepository.save(message);
  }

  @Transactional
  public void deleteMessageAndAttachments(Long messageId) {
    messageRepository.deleteById(messageId);
  }
}
