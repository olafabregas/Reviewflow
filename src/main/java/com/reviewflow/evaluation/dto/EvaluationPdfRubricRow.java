package com.reviewflow.evaluation.dto;

import java.math.BigDecimal;

public record EvaluationPdfRubricRow(
    String criterionName, BigDecimal score, int maxScore, String comment) {}
