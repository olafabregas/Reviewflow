package com.reviewflow.integration;

import com.reviewflow.controller.TeamController;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.TeamResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Team;
import com.reviewflow.exception.TeamNotAllowedException;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.util.HashidService;
import com.reviewflow.service.SubmissionService;
import com.reviewflow.service.TeamService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamControllerIntegrationTest {

    @Mock
    private TeamService teamService;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private HashidService hashidService;

    private TeamController controller() {
        return new TeamController(teamService, submissionService, hashidService);
    }

    private ReviewFlowUserDetails studentPrincipal() {
        User user = User.builder()
                .id(66L)
                .email("student2@test.local")
                .passwordHash("x")
                .role(UserRole.STUDENT)
                .isActive(true)
                .build();
        return new ReviewFlowUserDetails(user);
    }

    @Test
    void createTeam_onIndividualAssignment_throwsTeamNotAllowed() {
        when(hashidService.decodeOrThrow("ASSIGN3")).thenReturn(404L);
        when(teamService.createTeam(404L, "Team Should Fail", 66L))
                .thenThrow(new TeamNotAllowedException("This assignment does not allow team formation"));

        assertThrows(TeamNotAllowedException.class,
                () -> controller().create("ASSIGN3", Map.of("name", "Team Should Fail"), studentPrincipal()));
    }

    @Test
    void createTeam_happyPath_returnsCreatedWithEncodedTeamId() {
        Team team = Team.builder()
                .id(900L)
                .name("Alpha Team")
                .isLocked(false)
                .assignment(Assignment.builder().id(404L).title("PRD Assignment").build())
                .createdBy(User.builder().id(66L).build())
                .build();

        when(hashidService.decodeOrThrow("ASSIGN3")).thenReturn(404L);
        when(teamService.createTeam(404L, "Alpha Team", 66L)).thenReturn(team);
        when(hashidService.encode(900L)).thenReturn("TEAM_HASH_900");
        when(hashidService.encode(404L)).thenReturn("ASSIGN_HASH_404");
        when(hashidService.encode(66L)).thenReturn("USER_HASH_66");

        ResponseEntity<ApiResponse<TeamResponse>> response = controller().create(
                "ASSIGN3",
                Map.of("name", "Alpha Team"),
                studentPrincipal());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(true, response.getBody().isSuccess());
        assertEquals("TEAM_HASH_900", response.getBody().getData().getId());
        assertEquals("ASSIGN_HASH_404", response.getBody().getData().getAssignmentId());
        assertEquals("USER_HASH_66", response.getBody().getData().getCreatedById());
    }
}
