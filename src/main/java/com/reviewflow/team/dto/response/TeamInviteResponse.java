package com.reviewflow.team.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TeamInviteResponse {
  String teamMemberId;
  String teamId;
  String teamName;
  String assignmentId;
  String assignmentTitle;
  String courseCode;
  String invitedByName;
  Instant createdAt;
}
