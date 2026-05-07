package com.reviewflow.auth.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SessionListResponse {
  List<SessionEntryResponse> sessions;
}
