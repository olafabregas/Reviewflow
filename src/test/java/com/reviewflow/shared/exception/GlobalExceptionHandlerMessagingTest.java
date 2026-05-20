package com.reviewflow.shared.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.reviewflow.messaging.exception.MessagingClientException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerMessagingTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void messagingClientException_mapsNotAParticipantTo403() {
    MessagingClientException ex =
        MessagingClientException.forbidden("NOT_A_PARTICIPANT", "Not a participant");

    ResponseEntity<ErrorResponse> res = handler.handleMessagingClient(ex);

    assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    assertEquals("NOT_A_PARTICIPANT", res.getBody().getError().getCode());
  }

  @Test
  void messagingClientException_mapsCourseArchivedTo409() {
    MessagingClientException ex =
        MessagingClientException.conflict("COURSE_ARCHIVED", "Course archived");

    ResponseEntity<ErrorResponse> res = handler.handleMessagingClient(ex);

    assertEquals(HttpStatus.CONFLICT, res.getStatusCode());
    assertEquals("COURSE_ARCHIVED", res.getBody().getError().getCode());
  }

  @Test
  void messagingClientException_mapsMessagingRateLimitTo429() {
    MessagingClientException ex =
        MessagingClientException.tooManyRequests(
            "MESSAGING_RATE_LIMIT_EXCEEDED", "Too many messages. Retry after 5 seconds.", 5);

    ResponseEntity<ErrorResponse> res = handler.handleMessagingClient(ex);

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, res.getStatusCode());
    assertEquals("MESSAGING_RATE_LIMIT_EXCEEDED", res.getBody().getError().getCode());
    assertEquals("5", res.getHeaders().getFirst("Retry-After"));
    assertEquals("0", res.getHeaders().getFirst("X-RateLimit-Remaining"));
  }

  @Test
  void resourceNotFound_mapsToNotFoundEnvelope() {
    ResourceNotFoundException ex = new ResourceNotFoundException("Conversation", 999L);

    ResponseEntity<ErrorResponse> res = handler.handleNotFound(ex);

    assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
    assertEquals("NOT_FOUND", res.getBody().getError().getCode());
  }

  @Test
  void businessRule_mapsRecipientNotInCourseTo400() {
    BusinessRuleException ex =
        new BusinessRuleException("Recipient is not in this course", "RECIPIENT_NOT_IN_COURSE");

    ResponseEntity<ErrorResponse> res = handler.handleBusinessRule(ex);

    assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    assertEquals("RECIPIENT_NOT_IN_COURSE", res.getBody().getError().getCode());
  }
}
