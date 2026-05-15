package com.reviewflow.discussion.dto.response;

import java.time.Instant;

public record CreateDiscussionResponse(String id, String title, boolean isPublished) {}
