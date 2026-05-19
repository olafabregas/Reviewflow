package com.reviewflow.infrastructure.email.event;

import java.time.Instant;
import lombok.Getter;

@Getter
public class DiscussionPublishedEmailEvent extends EmailEvent {

  private final String discussionTitle;
  private final Instant dueAt;
  private final String courseCode;
  private final String discussionHashId;

  public DiscussionPublishedEmailEvent(
      String recipientEmail,
      String recipientName,
      String discussionTitle,
      Instant dueAt,
      String courseCode,
      String discussionHashId) {
    super(recipientEmail, recipientName, EmailCategory.STANDARD);
    this.discussionTitle = discussionTitle;
    this.dueAt = dueAt;
    this.courseCode = courseCode;
    this.discussionHashId = discussionHashId;
  }
}
