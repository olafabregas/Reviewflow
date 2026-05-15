package com.reviewflow.discussion.dto.response;

import java.time.Instant;

public record DiscussionReplyResponse(
    String id,
    String content,
    String authorName,
    String authorAvatarUrl,
    int wordCount,
    boolean isDeleted,
    Instant createdAt) {}
