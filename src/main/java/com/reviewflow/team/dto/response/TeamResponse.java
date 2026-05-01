package com.reviewflow.team.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

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
