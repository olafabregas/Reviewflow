package com.reviewflow.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateAssignmentRequest {

    @NotBlank
    private String title;
    private String description;
    private Instant dueAt;
    private Integer maxTeamSize;
    private Instant teamLockAt;
}
