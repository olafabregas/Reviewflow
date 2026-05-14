package com.reviewflow.messaging.repository;

import com.reviewflow.shared.domain.ConversationParticipant;
import com.reviewflow.shared.domain.ConversationParticipantKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationParticipantRepository
    extends JpaRepository<ConversationParticipant, ConversationParticipantKey> {

  Optional<ConversationParticipant> findByConversationIdAndUserId(
      Long conversationId, Long userId);

  List<ConversationParticipant> findByConversationId(Long conversationId);

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE ConversationParticipant p SET p.lastReadMessageId = :maxId "
          + "WHERE p.conversationId = :conversationId AND p.userId = :userId")
  int updateLastRead(
      @Param("conversationId") Long conversationId,
      @Param("userId") Long userId,
      @Param("maxId") Long maxMessageId);
}
