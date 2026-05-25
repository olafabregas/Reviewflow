package com.reviewflow.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

class SystemControllerMessagingSecurityTest {

  @Test
  void moderationListCourseConversations_requiresSystemAdmin() throws Exception {
    PreAuthorize auth =
        SystemController.class
            .getMethod(
                "moderationListCourseConversations",
                String.class,
                Authentication.class,
                HttpServletRequest.class)
            .getAnnotation(PreAuthorize.class);
    assertNotNull(auth);
    assertEquals("hasRole('SYSTEM_ADMIN')", auth.value());
  }

  @Test
  void moderationListConversationMessages_requiresSystemAdmin() throws Exception {
    PreAuthorize auth =
        SystemController.class
            .getMethod(
                "moderationListConversationMessages",
                String.class,
                int.class,
                int.class,
                Authentication.class,
                HttpServletRequest.class)
            .getAnnotation(PreAuthorize.class);
    assertNotNull(auth);
    assertEquals("hasRole('SYSTEM_ADMIN')", auth.value());
  }
}
