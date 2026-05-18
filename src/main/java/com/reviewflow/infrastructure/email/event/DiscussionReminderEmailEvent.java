package com.reviewflow.infrastructure.email.event;

import java.time.Instant;
import lombok.Getter;

@Getter
public class DiscussionReminderEmailEvent extends EmailEvent {

  private final String discussionTitle;
  private final Instant dueAt;
  private final String discussionHashId;

  public DiscussionReminderEmailEvent(
      String recipientEmail,
      String recipientName,
      String discussionTitle,
      Instant dueAt,
      String discussionHashId) {
    super(recipientEmail, recipientName, EmailCategory.STANDARD);
    this.discussionTitle = discussionTitle;
    this.dueAt = dueAt;
    this.discussionHashId = discussionHashId;
  }
}
