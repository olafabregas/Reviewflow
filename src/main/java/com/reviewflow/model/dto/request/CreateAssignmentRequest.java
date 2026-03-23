package com.reviewflow.model.dto.request;

import com.reviewflow.model.enums.SubmissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateAssignmentRequest {

    @NotBlank
    private String title;
    
    private String description;
    
    @NotNull
    private Instant dueAt;

    private Integer maxTeamSize;

    private SubmissionType submissionType;
    
    private Instant teamLockAt;
    
    private Boolean isPublished;
}
