package com.reviewflow.controller;

import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.TeamResponse;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.entity.TeamMember;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping("/assignments/{assignmentId}/teams")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> list(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        List<Team> teams = teamService.listTeamsForAssignment(assignmentId, user.getUserId(), user.getRole());
        List<TeamResponse> data = teams.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/assignments/{assignmentId}/teams")
    public ResponseEntity<ApiResponse<TeamResponse>> create(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        String name = body != null ? body.get("name") : null;
        if (name == null || name.isBlank()) name = "Team";
        Team team = teamService.createTeam(assignmentId, name, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(team)));
    }

    @GetMapping("/teams/{id}")
    public ResponseEntity<ApiResponse<TeamResponse>> get(@PathVariable Long id, @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Team team = teamService.getTeamById(id);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(team)));
    }

    private TeamResponse toResponse(Team t) {
        List<TeamResponse.TeamMemberResponse> members = t.getMembers() != null
                ? t.getMembers().stream()
                .map(m -> TeamResponse.TeamMemberResponse.builder()
                        .userId(m.getUser() != null ? m.getUser().getId() : null)
                        .status(m.getStatus() != null ? m.getStatus().name() : null)
                        .build())
                .collect(Collectors.toList())
                : List.of();
        return TeamResponse.builder()
                .id(t.getId())
                .assignmentId(t.getAssignment() != null ? t.getAssignment().getId() : null)
                .name(t.getName())
                .isLocked(t.getIsLocked())
                .members(members)
                .build();
    }
}
