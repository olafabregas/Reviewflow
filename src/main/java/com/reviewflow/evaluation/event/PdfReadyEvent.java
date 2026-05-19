package com.reviewflow.evaluation.event;

import java.util.List;

/** In-app notification after async PDF upload completes (PRD-21). */
public record PdfReadyEvent(
    Long evaluationId, Long assignmentId, List<Long> recipientUserIds, String assignmentTitle) {}
