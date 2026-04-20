package com.reviewflow.integration;

import com.reviewflow.controller.GradeOverviewController;
import com.reviewflow.model.dto.response.ClassRosterDto;
import com.reviewflow.model.dto.response.GradeOverviewDto;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.GradeCalculationService;
import com.reviewflow.util.HashidService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeOverviewControllerIntegrationTest {

    @Mock
    private GradeCalculationService gradeCalculationService;
    @Mock
    private HashidService hashidService;

    private GradeOverviewController controller() {
        return new GradeOverviewController(gradeCalculationService, hashidService);
    }

    private ReviewFlowUserDetails principal(UserRole role, Long userId) {
        User user = User.builder()
                .id(userId)
                .email(role.name().toLowerCase() + "@test.local")
                .passwordHash("x")
                .role(role)
                .isActive(true)
                .build();
        return new ReviewFlowUserDetails(user);
    }

    @Test
    void getMyOverview_decodesCourseAndDelegatesToService() {
        when(hashidService.decodeOrThrow("COURSE_1")).thenReturn(11L);

        GradeOverviewDto dto = GradeOverviewDto.builder()
                .courseCode("CS401")
                .courseName("Advanced SE")
                .currentStanding(BigDecimal.valueOf(88.5))
                .groups(List.of())
                .build();
        when(gradeCalculationService.calculateMyOverview(11L, 77L, UserRole.STUDENT)).thenReturn(dto);

        var response = controller().getMyOverview("COURSE_1", principal(UserRole.STUDENT, 77L));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("CS401", response.getBody().getData().getCourseCode());
        verify(gradeCalculationService).calculateMyOverview(11L, 77L, UserRole.STUDENT);
    }

    @Test
    void getStudentOverview_decodesBothIdsAndDelegates() {
        when(hashidService.decodeOrThrow("COURSE_1")).thenReturn(11L);
        when(hashidService.decodeOrThrow("STUDENT_2")).thenReturn(102L);

        GradeOverviewDto dto = GradeOverviewDto.builder()
                .courseCode("CS401")
                .courseName("Advanced SE")
                .groups(List.of())
                .build();
        when(gradeCalculationService.calculateStudentOverview(11L, 102L, 300L, UserRole.INSTRUCTOR)).thenReturn(dto);

        var response = controller().getStudentOverview(
                "COURSE_1",
                "STUDENT_2",
                principal(UserRole.INSTRUCTOR, 300L));

        assertEquals(200, response.getStatusCode().value());
        verify(gradeCalculationService).calculateStudentOverview(11L, 102L, 300L, UserRole.INSTRUCTOR);
    }

    @Test
    void getRoster_forwardsSortingAndFilterParams() {
        when(hashidService.decodeOrThrow("COURSE_1")).thenReturn(11L);

        ClassRosterDto roster = ClassRosterDto.builder()
                .courseCode("CS401")
                .students(List.of())
                .build();
        when(gradeCalculationService.calculateRoster(11L, 501L, UserRole.ADMIN, "name", "desc", true))
                .thenReturn(roster);

        var response = controller().getRoster(
                "COURSE_1",
                "name",
                "desc",
                true,
                principal(UserRole.ADMIN, 501L));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("CS401", response.getBody().getData().getCourseCode());
        verify(gradeCalculationService).calculateRoster(11L, 501L, UserRole.ADMIN, "name", "desc", true);
    }
}
