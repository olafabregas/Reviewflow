package com.reviewflow.model.dto.request;

import com.reviewflow.model.enums.SubmissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateAssignmentRequest {

    @Schema(description = "Assignment title or name", example = "Programming Assignment 1", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String title;
    
    @Schema(description = "Detailed description of assignment requirements and guidelines", example = "Implement a sorting algorithm", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
    private String description;
    
    @Schema(description = "Deadline for assignment submission", example = "2026-04-15T23:59:59Z", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
    @NotNull
    private Instant dueAt;

    @Schema(description = "Maximum number of students allowed per team, null for individual submissions", example = "3", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
    private Integer maxTeamSize;

    @Schema(description = "Type of submission format (e.g., FILE, URL, TEXT)", example = "FILE", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
    private SubmissionType submissionType;

    @Schema(description = "Hashid of the assignment group; defaults to the course Uncategorized group when omitted", example = "grp12345", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
    private String groupId;
    
    @Schema(description = "Deadline for team formation before submissions are locked", example = "2026-04-10T23:59:59Z", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
    private Instant teamLockAt;
    
    @Schema(description = "Whether the assignment is published and visible to students", example = "true", requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
    private Boolean isPublished;
}
