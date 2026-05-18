package com.reviewflow.grading.event;

public record GradePublishedEvent(Long courseId, Long studentId) {}
