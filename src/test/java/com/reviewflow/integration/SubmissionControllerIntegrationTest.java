package com.reviewflow.integration;

import com.reviewflow.controller.SubmissionController;
import com.reviewflow.exception.IndividualSubmissionOnlyException;
import com.reviewflow.exception.TeamSubmissionRequiredException;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.util.HashidService;
import com.reviewflow.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class SubmissionControllerIntegrationTest {

    @Mock
    private SubmissionService submissionService;

    @Mock
    private HashidService hashidService;

    private SubmissionController controller() {
        return new SubmissionController(submissionService, hashidService);
    }

    private ReviewFlowUserDetails studentPrincipal() {
        User user = User.builder()
                .id(55L)
                .email("student@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();
        return new ReviewFlowUserDetails(user);
    }

    @Test
    void upload_teamAssignmentWithoutTeamId_throwsTeamSubmissionRequired() {
        when(hashidService.decodeOrThrow("ASSIGN1")).thenReturn(101L);
        when(submissionService.upload(isNull(), eq(101L), isNull(), any(), eq(55L)))
                .thenThrow(new TeamSubmissionRequiredException("Team submission is required for this assignment"));

        MultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "abc".getBytes());

        assertThrows(TeamSubmissionRequiredException.class,
                () -> controller().upload(null, "ASSIGN1", null, file, studentPrincipal()));
    }

    @Test
    void upload_individualAssignmentWithTeamId_throwsIndividualSubmissionOnly() {
        when(hashidService.decodeOrThrow("ASSIGN2")).thenReturn(202L);
        when(hashidService.decodeOrThrow("TEAM1")).thenReturn(303L);
        when(submissionService.upload(eq(303L), eq(202L), isNull(), any(), eq(55L)))
                .thenThrow(new IndividualSubmissionOnlyException("Team submissions are not allowed for this assignment"));

        MultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "abc".getBytes());

        assertThrows(IndividualSubmissionOnlyException.class,
                () -> controller().upload("TEAM1", "ASSIGN2", null, file, studentPrincipal()));
    }
}
