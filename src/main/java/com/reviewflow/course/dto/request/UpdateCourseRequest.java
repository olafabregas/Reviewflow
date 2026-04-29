package com.reviewflow.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateCourseRequest {
  @Schema(
      description = "Unique course code or identifier",
      example = "CS101",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
  private String code;

  @Schema(
      description = "Full name or title of the course",
      example = "Introduction to Computer Science",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
  @NotBlank
  private String name;

  @Schema(
      description = "Academic term or semester for the course",
      example = "Spring 2026",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
  private String term;

  @Schema(
      description = "Detailed description of the course content and objectives",
      example = "Cover fundamentals of programming and algorithms",
      requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED)
  private String description;
}
