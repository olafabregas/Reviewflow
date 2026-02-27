package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class TeamResponse {

    Long id;
    Long assignmentId;
    String name;
    Boolean isLocked;
    List<TeamMemberResponse> members;

    @Value
    @Builder
    public static class TeamMemberResponse {
        Long userId;
        String status;
    }
}
