package com.reviewflow.model.dto.response;

import com.reviewflow.model.enums.SubmissionType;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AssignmentModuleResponse {

  String id;
  String name;
  Integer displayOrder;
  List<AssignmentSummary> assignments;

  @Value
  @Builder
  public static class AssignmentSummary {

    String id;
    String title;
    Instant dueAt;
    SubmissionType submissionType;
    String groupId;
    String groupName;
  }
}
