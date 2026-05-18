package com.reviewflow.messaging.repository;

import java.time.Instant;

/** Batch stats for SYSTEM_ADMIN moderation conversation list. */
public interface ConversationModerationStatsView {

  Long getConversationId();

  Long getMessageCount();

  Instant getLastActivity();
}
