package com.reviewflow.discussion.dto.response;

import java.time.Instant;
import java.util.List;

public record DiscussionPostResponse(
    String id,
    String content,
    String authorName,
    String authorAvatarUrl,
    boolean isInstructor,
    boolean isPinned,
    int wordCount,
    boolean isDeleted,
    Instant createdAt,
    List<DiscussionReplyResponse> replies) {}
