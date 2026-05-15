package com.reviewflow.discussion.dto.response;

import java.time.Instant;

public record MyPostResponse(String id, String content, Instant createdAt) {}
