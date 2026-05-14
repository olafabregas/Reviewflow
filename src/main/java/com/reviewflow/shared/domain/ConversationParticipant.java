package com.reviewflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "conversation_participants")
@IdClass(ConversationParticipantKey.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipant {

  @Id
  @Column(name = "conversation_id", nullable = false)
  private Long conversationId;

  @Id
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conversation_id", insertable = false, updatable = false)
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt;

  @Column(name = "last_read_message_id")
  private Long lastReadMessageId;
}
