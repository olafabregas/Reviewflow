package com.reviewflow.evaluation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EvaluationPdfContext(
    Long evaluationId,
    String assignmentTitle,
    int submissionVersion,
    String submitterLabelLine,
    String instructorDisplayName,
    Instant publishedAt,
    List<EvaluationPdfRubricRow> rubricRows,
    BigDecimal totalScore,
    String overallComment) {}
