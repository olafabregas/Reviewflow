package com.reviewflow.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EnrollStudentRequest {

  @Schema(description = "Student user hashid", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank
  private String studentId;
}
