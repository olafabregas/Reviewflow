package com.reviewflow.discussion.repository;

import java.time.Instant;

/** Native-query projection for {@link DiscussionRepository#findStudentsNeedingDiscussionReminder}. */
public interface DiscussionReminderRow {

  Long getDiscussionId();

  String getDiscussionTitle();

  Instant getDueAt();

  Long getStudentId();

  String getStudentEmail();

  String getStudentFirstName();
}
