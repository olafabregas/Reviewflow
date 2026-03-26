package com.reviewflow.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateEmailPreferenceRequest {

    @NotNull
    private Boolean emailNotificationsEnabled;
}
