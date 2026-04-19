package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InstructorScoreImportCommitResponse {

    int created;
    int updated;
    String message;
}
