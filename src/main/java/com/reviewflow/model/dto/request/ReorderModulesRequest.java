package com.reviewflow.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ReorderModulesRequest {

  @Schema(
      description = "Ordered list of module hashids",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull
  @NotEmpty
  private List<String> order;
}
