package com.reviewflow.infrastructure.email.event;

import lombok.Getter;

@Getter
public class DiscussionInstructorReplyEmailEvent extends EmailEvent {

  private final String replierName;
  private final String discussionTitle;
  private final String replySnippet;
  private final String discussionHashId;

  public DiscussionInstructorReplyEmailEvent(
      String recipientEmail,
      String recipientName,
      String replierName,
      String discussionTitle,
      String replySnippet,
      String discussionHashId) {
    super(recipientEmail, recipientName, EmailCategory.STANDARD);
    this.replierName = replierName;
    this.discussionTitle = discussionTitle;
    this.replySnippet = replySnippet;
    this.discussionHashId = discussionHashId;
  }
}
