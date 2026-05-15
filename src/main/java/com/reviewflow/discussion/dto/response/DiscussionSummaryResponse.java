package com.reviewflow.discussion.dto.response;

import java.time.Instant;

public record DiscussionSummaryResponse(
    String id, String title, boolean isPublished, Instant dueAt, boolean isGraded) {}
