package com.reviewflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReopenEvaluationResponse {

    @JsonProperty("evaluationId")
    private String evaluationId;

    @JsonProperty("isDraft")
    private boolean isDraft;
}
