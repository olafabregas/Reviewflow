package com.reviewflow.discussion.event;

import java.time.Instant;
import java.util.List;

public record DiscussionReminderBatchEvent(
    Long discussionId,
    String discussionTitle,
    Instant dueAt,
    List<ReminderRecipient> recipients) {

  public record ReminderRecipient(Long studentId, String email, String firstName) {}
}
