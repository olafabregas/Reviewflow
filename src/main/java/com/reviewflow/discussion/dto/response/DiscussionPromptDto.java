package com.reviewflow.discussion.dto.response;

import java.time.Instant;

public record DiscussionPromptDto(String title, String prompt, Instant dueAt) {}
