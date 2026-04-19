package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class InstructorScoreImportPreviewResponse {

    String importId;
    int totalRows;
    List<ValidRow> valid;
    List<RowIssue> errors;
    List<RowIssue> warnings;
    int expiresInSeconds;

    @Value
    @Builder
    public static class ValidRow {
        String studentEmail;
        String teamId;
        BigDecimal score;
        String comment;
    }

    @Value
    @Builder
    public static class RowIssue {
        int row;
        String studentEmail;
        String teamId;
        String error;
        String warning;
    }
}
