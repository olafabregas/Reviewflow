package com.reviewflow.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespondExtensionRequest {

    @NotNull(message = "approve is required")
    private Boolean approve;

    private String instructorNote;
}
