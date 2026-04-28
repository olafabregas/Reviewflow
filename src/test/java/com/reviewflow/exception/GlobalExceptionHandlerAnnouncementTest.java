package com.reviewflow.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerAnnouncementTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void annonuncementNotFound_returns404() {
    var response = handler.handleAnnouncementNotFound(new AnnouncementNotFoundException(123L));
    assertEquals(404, response.getStatusCode().value());
    assertEquals("ANNOUNCEMENT_NOT_FOUND", response.getBody().getError().getCode());
  }

  @Test
  void alreadyPublished_returns409() {
    var response = handler.handleAlreadyPublished(new AlreadyPublishedException());
    assertEquals(409, response.getStatusCode().value());
    assertEquals("ALREADY_PUBLISHED", response.getBody().getError().getCode());
  }

  @Test
  void courseNotOwned_returns403() {
    var response = handler.handleCourseNotOwned(new CourseNotOwnedException());
    assertEquals(403, response.getStatusCode().value());
    assertEquals("COURSE_NOT_OWNED", response.getBody().getError().getCode());
  }
}
