package com.reviewflow.grading.dto.response;

import com.reviewflow.grading.job.JobStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ImportJobStartResponse {
  String jobId;
  JobStatus status;
}
