package com.reviewflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.reviewflow.controller.GradeExportController;
import com.reviewflow.model.entity.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.infra.security.ReviewFlowUserDetails;
import com.reviewflow.service.GradeExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class GradeExportControllerIntegrationTest {

  @Mock private GradeExportService gradeExportService;

  private GradeExportController controller() {
    return new GradeExportController(gradeExportService);
  }

  private ReviewFlowUserDetails instructorPrincipal() {
    User user =
        User.builder()
            .id(77L)
            .email("instructor@test.local")
            .passwordHash("x")
            .role(UserRole.INSTRUCTOR)
            .isActive(true)
            .build();
    return new ReviewFlowUserDetails(user);
  }

  @Test
  void export_returnsCsvAttachmentHeadersAndBody() {
    byte[] body = "a,b\n1,2\n".getBytes();
    when(gradeExportService.export("COURSE1", "ASSIGN1", 77L))
        .thenReturn(new GradeExportService.ExportResult(body, "cs401_midterm_2026-03-26.csv"));

    ResponseEntity<byte[]> response =
        controller().export("COURSE1", "ASSIGN1", instructorPrincipal());

    assertEquals(200, response.getStatusCode().value());
    assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
    assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("attachment"));
    assertTrue(
        response
            .getHeaders()
            .getFirst("Content-Disposition")
            .contains("cs401_midterm_2026-03-26.csv"));
    assertEquals("a,b\n1,2\n", new String(response.getBody()));
  }
}
