package com.reviewflow.messaging.repository;

import com.reviewflow.shared.domain.Message;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

  @Query(
      "SELECT m FROM Message m WHERE m.conversation.id = :conversationId "
          + "AND (:beforeId IS NULL OR m.id < :beforeId) "
          + "ORDER BY m.sentAt DESC, m.id DESC")
  List<Message> findPageForConversation(
      @Param("conversationId") Long conversationId,
      @Param("beforeId") Long beforeMessageId,
      Pageable pageable);

  @Query(
      "SELECT m FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.sentAt ASC, m.id ASC")
  List<Message> findAllByConversationIdForModeration(@Param("conversationId") Long conversationId);

  @Query(
      "SELECT COALESCE(MAX(m.id), 0) FROM Message m WHERE m.conversation.id = :conversationId")
  long findMaxMessageId(@Param("conversationId") Long conversationId);

  @Query(
      value =
          "SELECT COUNT(*) FROM messages m "
              + "INNER JOIN conversation_participants cp ON cp.conversation_id = m.conversation_id "
              + "AND cp.user_id = :userId "
              + "WHERE m.sender_id <> :userId AND m.is_deleted = 0 "
              + "AND (cp.last_read_message_id IS NULL OR m.id > cp.last_read_message_id)",
      nativeQuery = true)
  long countTotalUnreadForUser(@Param("userId") Long userId);

  @Query(
      value =
          "SELECT COUNT(*) FROM messages m "
              + "INNER JOIN conversation_participants cp ON cp.conversation_id = m.conversation_id "
              + "AND cp.user_id = :userId "
              + "WHERE m.conversation_id = :conversationId AND m.sender_id <> :userId AND m.is_deleted = 0 "
              + "AND (cp.last_read_message_id IS NULL OR m.id > cp.last_read_message_id)",
      nativeQuery = true)
  long countUnreadInConversation(
      @Param("conversationId") Long conversationId, @Param("userId") Long userId);

  @Query(
      "SELECT m FROM Message m WHERE m.conversation.id = :conversationId "
          + "ORDER BY m.sentAt DESC, m.id DESC")
  List<Message> findLatestMessage(@Param("conversationId") Long conversationId, Pageable pageable);

  long countByConversation_Id(Long conversationId);

  @Query("SELECT MAX(m.sentAt) FROM Message m WHERE m.conversation.id = :cid")
  java.util.Optional<java.time.Instant> findMaxSentAtByConversation(
      @Param("cid") Long conversationId);
}
