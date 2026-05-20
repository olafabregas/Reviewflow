package com.reviewflow.infrastructure.jobs;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JobProgressEvent {
  int processed;
  int total;
  int percent;
  String status;
  String errorMessage;
}
