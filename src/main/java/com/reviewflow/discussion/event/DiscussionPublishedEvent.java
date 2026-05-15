package com.reviewflow.discussion.event;

import java.time.Instant;

public record DiscussionPublishedEvent(
    Long courseId, Long discussionId, String title, Instant dueAt) {}
