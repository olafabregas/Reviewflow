package com.reviewflow.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class CourseContextInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String path = request.getRequestURI();

    extractPathSegment(path, "/courses/")
        .ifPresent(hashedCourseId -> MDC.put("courseId", hashedCourseId));

    extractPathSegment(path, "/assignments/")
        .ifPresent(hashedAssignmentId -> MDC.put("assignmentId", hashedAssignmentId));

    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception ex) {
    MDC.remove("courseId");
    MDC.remove("assignmentId");
  }

  private Optional<String> extractPathSegment(String path, String prefix) {
    int idx = path.indexOf(prefix);
    if (idx == -1) {
      return Optional.empty();
    }

    String after = path.substring(idx + prefix.length());
    int slash = after.indexOf('/');
    String segment = slash == -1 ? after : after.substring(0, slash);

    if (!segment.isBlank()) {
      return Optional.of(segment);
    }
    return Optional.empty();
  }
}
