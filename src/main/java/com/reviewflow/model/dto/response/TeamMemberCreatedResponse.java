package com.reviewflow.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TeamMemberCreatedResponse {

    @Schema(description = "Hashid of the created team membership record")
    String teamMemberId;

    @Schema(description = "Current membership status (e.g. PENDING, ACCEPTED)")
    String status;
}
