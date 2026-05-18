package com.reviewflow.grading.job;

public record CsvRowError(
    int row, String studentEmail, String teamId, String score, String comment, String reason) {}
