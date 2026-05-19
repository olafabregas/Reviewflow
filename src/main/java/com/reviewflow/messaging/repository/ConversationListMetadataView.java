package com.reviewflow.messaging.repository;

import java.time.Instant;

/** Batch projection for conversation list enrichment (latest message + unread). */
public interface ConversationListMetadataView {

  Long getConversationId();

  Long getUnreadCount();

  Long getLatestMessageId();

  String getLatestContent();

  Instant getLatestSentAt();

  Boolean getLatestIsDeleted();

  Long getLatestSenderId();

  String getLatestSenderFirstName();

  String getLatestSenderLastName();

  String getLatestSenderEmail();

  String getLatestSenderAvatarUrl();

  Long getHasAttachments();
}
