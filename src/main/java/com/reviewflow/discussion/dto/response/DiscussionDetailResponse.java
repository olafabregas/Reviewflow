package com.reviewflow.discussion.dto.response;

import java.time.Instant;

public record DiscussionDetailResponse(
    String id,
    String title,
    String prompt,
    Instant dueAt,
    boolean requirePostBeforeReading,
    boolean allowAnonymous,
    boolean isGraded,
    String assignmentId,
    boolean isPublished,
    Instant publishedAt) {}
