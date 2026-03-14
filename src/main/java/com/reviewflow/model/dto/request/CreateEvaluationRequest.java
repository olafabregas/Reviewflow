package com.reviewflow.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateEvaluationRequest {
    @NotBlank
    private String submissionId;
}
