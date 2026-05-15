package com.reviewflow.discussion.dto.response;

import java.time.Instant;

public record CreatePostResponse(
    String id, String content, String authorName, Instant createdAt, int wordCount) {}
