package com.reviewflow.grading.dto.response;

import com.reviewflow.grading.job.JobStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JobStatusDto {
  String jobId;
  JobStatus status;
  String assignmentId;
  int totalRows;
  int validRows;
  int invalidRows;
  int processedRows;
  String errorMessage;
  Instant createdAt;
  Instant updatedAt;
}
