package com.reviewflow.messaging.repository;

import com.reviewflow.shared.domain.Conversation;
import com.reviewflow.shared.domain.ConversationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

  Optional<Conversation> findByTeam_Id(Long teamId);

  @Query(
      "SELECT DISTINCT c FROM Conversation c "
          + "JOIN ConversationParticipant p1 ON p1.conversationId = c.id AND p1.userId = :u1 "
          + "JOIN ConversationParticipant p2 ON p2.conversationId = c.id AND p2.userId = :u2 "
          + "WHERE c.course.id = :courseId AND c.conversationType = :directType")
  Optional<Conversation> findDirectConversation(
      @Param("courseId") Long courseId,
      @Param("u1") Long user1Id,
      @Param("u2") Long user2Id,
      @Param("directType") ConversationType directType);

  @Query(
      "SELECT DISTINCT c FROM Conversation c JOIN ConversationParticipant p ON p.conversationId = c.id "
          + "WHERE c.course.id = :courseId AND p.userId = :userId")
  List<Conversation> findDistinctByCourseIdAndParticipantUserId(
      @Param("courseId") Long courseId, @Param("userId") Long userId);

  List<Conversation> findByCourse_Id(Long courseId);

  @Query(
      "SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Conversation c "
          + "JOIN ConversationParticipant p ON p.conversationId = c.id "
          + "WHERE c.course.id = :courseId AND p.userId = :userId")
  boolean existsByCourseIdAndParticipantUserId(
      @Param("courseId") Long courseId, @Param("userId") Long userId);
}
