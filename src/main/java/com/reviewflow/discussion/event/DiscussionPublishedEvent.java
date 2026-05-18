package com.reviewflow.discussion.event;

import java.time.Instant;

public record DiscussionPublishedEvent(
    Long courseId, String courseCode, Long discussionId, String title, Instant dueAt) {}
