package com.reviewflow.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignInstructorRequest {

  @Schema(description = "Instructor user hashid", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank
  private String instructorId;
}
