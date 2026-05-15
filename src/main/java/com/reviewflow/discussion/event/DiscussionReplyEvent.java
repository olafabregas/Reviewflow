package com.reviewflow.discussion.event;

public record DiscussionReplyEvent(
    Long discussionId, Long originalAuthorId, Long replierUserId, String replierName) {}
