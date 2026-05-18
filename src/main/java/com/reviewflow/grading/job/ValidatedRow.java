package com.reviewflow.grading.job;

import java.math.BigDecimal;

/** Serializable row stored in S3 validated-rows.json for async commit. */
public record ValidatedRow(String studentEmail, String teamId, BigDecimal score, String comment) {}
