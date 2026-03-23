package com.reviewflow.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reviewflow.model.dto.request.RenameTeamRequest;
import com.reviewflow.model.dto.request.TeamInviteRequest;
import com.reviewflow.model.dto.request.TeamRespondRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.SubmissionResponse;
import com.reviewflow.model.dto.response.TeamInviteResponse;
import com.reviewflow.model.dto.response.TeamResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.entity.TeamMember;
import com.reviewflow.model.entity.User;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.SubmissionService;
import com.reviewflow.service.TeamService;
import com.reviewflow.service.HashidService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final SubmissionService submissionService;
    private final HashidService hashidService;

    @GetMapping("/assignments/{assignmentId}/teams")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> list(
            @PathVariable String assignmentId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentIdLong = hashidService.decodeOrThrow(assignmentId);
        List<Team> teams = teamService.listTeamsForAssignment(assignmentIdLong, user.getUserId(), user.getRole());
        List<TeamResponse> data = teams.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/assignments/{assignmentId}/teams")
    public ResponseEntity<ApiResponse<TeamResponse>> create(
            @PathVariable String assignmentId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentIdLong = hashidService.decodeOrThrow(assignmentId);
        String name = body != null ? body.get("name") : null;
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Team team = teamService.createTeam(assignmentIdLong, name, user.getUserId());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.ok(toResponse(team)));
    }

    @PostMapping("/assignments/{assignmentId}/teams/assign")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> autoAssign(
            @PathVariable String assignmentId,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentIdLong = hashidService.decodeOrThrow(assignmentId);
        int maxSize = body != null && body.get("maxTeamSize") instanceof Number n ? n.intValue() : 3;
        List<Team> created = teamService.autoAssignTeams(assignmentIdLong, user.getUserId(), maxSize);
        return ResponseEntity.ok(ApiResponse.ok(created.stream().map(this::toResponse).collect(Collectors.toList())));
    }

    @GetMapping("/teams/{id}")
    public ResponseEntity<ApiResponse<TeamResponse>> get(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamId = hashidService.decodeOrThrow(id);
        Team team = teamService.getTeamByIdWithAccessControl(teamId, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(team)));
    }

    @PutMapping("/teams/{id}")
    public ResponseEntity<ApiResponse<TeamResponse>> rename(
            @PathVariable String id,
            @Valid @RequestBody RenameTeamRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamId = hashidService.decodeOrThrow(id);
        Team team = teamService.renameTeam(teamId, request.getName(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(team)));
    }

    @PostMapping("/teams/{id}/invite")
    public ResponseEntity<ApiResponse<Map<String, Object>>> invite(
            @PathVariable String id,
            @Valid @RequestBody TeamInviteRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamId = hashidService.decodeOrThrow(id);
        TeamMember member = teamService.inviteMember(teamId, request.getInviteeEmail(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("teamMemberId", hashidService.encode(member.getId()), "status", member.getStatus().name())));
    }

    @PatchMapping("/team-members/{id}/respond")
    public ResponseEntity<ApiResponse<Map<String, String>>> respond(
            @PathVariable String id,
            @Valid @RequestBody TeamRespondRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamMemberId = hashidService.decodeOrThrow(id);
        TeamMember member = teamService.respondToInvite(teamMemberId, request.getAccept(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", member.getStatus().name())));
    }

    @DeleteMapping("/teams/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> removeMember(
            @PathVariable String id,
            @PathVariable String userId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamId = hashidService.decodeOrThrow(id);
        Long userIdLong = hashidService.decodeOrThrow(userId);
        teamService.removeMember(teamId, userIdLong, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Member removed")));
    }

    @PostMapping("/teams/{id}/lock")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TeamResponse>> lock(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamId = hashidService.decodeOrThrow(id);
        Team team = teamService.lockTeam(teamId);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(team)));
    }
    
    @GetMapping("/teams/{id}/submissions")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> getTeamSubmissions(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamId = hashidService.decodeOrThrow(id);
        List<Submission> submissions = submissionService.getTeamSubmissions(teamId, user.getUserId(), user.getRole());
        List<SubmissionResponse> data = submissions.stream()
                .map(this::toSubmissionResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private TeamResponse toResponse(Team t) {
        List<TeamResponse.TeamMemberResponse> members = t.getMembers() != null
                ? t.getMembers().stream()
                .map(m -> TeamResponse.TeamMemberResponse.builder()
                        .teamMemberId(hashidService.encode(m.getId()))
                        .userId(hashidService.encode(m.getUser() != null ? m.getUser().getId() : null))
                        .firstName(m.getUser() != null ? m.getUser().getFirstName() : null)
                        .lastName(m.getUser() != null ? m.getUser().getLastName() : null)
                        .email(m.getUser() != null ? m.getUser().getEmail() : null)
                        .status(m.getStatus() != null ? m.getStatus().name() : null)
                        .invitedById(hashidService.encode(m.getInvitedBy() != null ? m.getInvitedBy().getId() : null))
                        .joinedAt(m.getJoinedAt())
                        .build())
                .collect(Collectors.toList())
                : List.of();
        return TeamResponse.builder()
                .id(hashidService.encode(t.getId()))
                .assignmentId(hashidService.encode(t.getAssignment() != null ? t.getAssignment().getId() : null))
                .assignmentTitle(t.getAssignment() != null ? t.getAssignment().getTitle() : null)
                .name(t.getName())
                .isLocked(t.getIsLocked())
                .createdById(hashidService.encode(t.getCreatedBy() != null ? t.getCreatedBy().getId() : null))
                .memberCount(members != null ? members.size() : 0)
                .members(members)
                .build();
    }
    
    private TeamInviteResponse toInviteResponse(TeamMember tm) {
        Team team = tm.getTeam();
        Assignment assignment = team != null ? team.getAssignment() : null;
        User invitedBy = tm.getInvitedBy();
        
        return TeamInviteResponse.builder()
                .teamMemberId(hashidService.encode(tm.getId()))
                .teamId(hashidService.encode(team != null ? team.getId() : null))
                .teamName(team != null ? team.getName() : null)
                .assignmentId(hashidService.encode(assignment != null ? assignment.getId() : null))
                .assignmentTitle(assignment != null ? assignment.getTitle() : null)
                .courseCode(assignment != null && assignment.getCourse() != null ? assignment.getCourse().getCode() : null)
                .invitedByName(invitedBy != null ? invitedBy.getFirstName() + " " + invitedBy.getLastName() : null)
                .createdAt(tm.getJoinedAt())
                .build();
    }
    
    private SubmissionResponse toSubmissionResponse(Submission s) {
        return SubmissionResponse.builder()
                .id(hashidService.encode(s.getId()))
                .submissionType(s.getAssignment() != null ? s.getAssignment().getSubmissionType() : null)
                .studentId(hashidService.encode(s.getStudent() != null ? s.getStudent().getId() : null))
                .teamId(hashidService.encode(s.getTeam() != null ? s.getTeam().getId() : null))
                .teamName(s.getTeam() != null ? s.getTeam().getName() : null)
                .assignmentId(hashidService.encode(s.getAssignment() != null ? s.getAssignment().getId() : null))
                .assignmentTitle(s.getAssignment() != null ? s.getAssignment().getTitle() : null)
                .courseCode(s.getAssignment() != null && s.getAssignment().getCourse() != null
                        ? s.getAssignment().getCourse().getCode() : null)
                .versionNumber(s.getVersionNumber())
                .fileName(s.getFileName())
                .fileSizeBytes(s.getFileSizeBytes())
                .isLate(s.getIsLate())
                .uploadedAt(s.getUploadedAt())
                .changeNote(s.getChangeNote())
                .uploadedById(hashidService.encode(s.getUploadedBy() != null ? s.getUploadedBy().getId() : null))
                .uploadedByName(s.getUploadedBy() != null
                        ? s.getUploadedBy().getFirstName() + " " + s.getUploadedBy().getLastName() : null)
                .build();
    }
}
