package com.reviewflow.discussion.event;

import com.reviewflow.shared.domain.UserRole;

public record DiscussionReplyEvent(
    Long discussionId,
    Long originalAuthorId,
    UserRole originalAuthorRole,
    Long replierUserId,
    UserRole replierRole,
    String replierName,
    String discussionTitle,
    String replySnippet) {}
