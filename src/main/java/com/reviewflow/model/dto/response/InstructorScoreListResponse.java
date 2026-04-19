package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class InstructorScoreListResponse {

    List<InstructorScoreItem> scores;
    Summary summary;

    @Value
    @Builder
    public static class InstructorScoreItem {

        String id;
        String studentId;
        String studentName;
        String teamId;
        String teamName;
        java.math.BigDecimal score;
        java.math.BigDecimal maxScore;
        Boolean isPublished;
    }

    @Value
    @Builder
    public static class Summary {

        long total;
        long published;
        long draft;
        long notEntered;
    }
}
