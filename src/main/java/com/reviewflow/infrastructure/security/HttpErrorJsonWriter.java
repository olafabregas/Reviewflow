package com.reviewflow.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.shared.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Writes {@link ErrorResponse}-shaped JSON from servlet filters (same contract as controller advice). */
@Component
@RequiredArgsConstructor
public class HttpErrorJsonWriter {

  private final ObjectMapper objectMapper;

  public void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds, String message)
      throws IOException {
    writeTooManyRequests(response, retryAfterSeconds, message, 0, 0);
  }

  public void writeError(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    ErrorResponse body =
        ErrorResponse.builder()
            .error(ErrorResponse.ErrorDetail.builder().code(code).message(message).build())
            .timestamp(Instant.now())
            .build();
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }

  public void writeTooManyRequests(
      HttpServletResponse response,
      long retryAfterSeconds,
      String message,
      long limitCapacity,
      long resetEpochSeconds)
      throws IOException {
    ErrorResponse body =
        ErrorResponse.builder()
            .error(
                ErrorResponse.ErrorDetail.builder()
                    .code("TOO_MANY_REQUESTS")
                    .message(message)
                    .build())
            .timestamp(Instant.now())
            .build();
    response.setStatus(429);
    response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    response.setHeader("X-RateLimit-Remaining", "0");
    if (limitCapacity > 0) {
      response.setHeader("X-RateLimit-Limit", String.valueOf(limitCapacity));
    }
    if (resetEpochSeconds > 0) {
      response.setHeader("X-RateLimit-Reset", String.valueOf(resetEpochSeconds));
    }
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
