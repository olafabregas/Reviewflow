package com.reviewflow.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StepUpRequest {

  @NotBlank private String password;
}
