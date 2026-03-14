package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class TeamResponse {

    String id;
    String assignmentId;
    String assignmentTitle;
    String name;
    Boolean isLocked;
    String createdById;
    Integer memberCount;
    List<TeamMemberResponse> members;

    @Value
    @Builder
    public static class TeamMemberResponse {
        String teamMemberId;
        String userId;
        String firstName;
        String lastName;
        String email;
        String status;
        String invitedById;
        Instant joinedAt;
    }
}
