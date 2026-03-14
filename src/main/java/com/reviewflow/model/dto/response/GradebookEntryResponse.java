package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class GradebookEntryResponse {
    String teamId;
    String teamName;
    List<String> memberNames;
    Integer latestVersion;
    Instant submittedAt;
    Boolean isLate;
    BigDecimal totalScore;
    String evaluationStatus; // NOT_STARTED | DRAFT | PUBLISHED
}
