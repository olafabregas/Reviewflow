package com.reviewflow.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
    name = "Teams",
    description = "Team collaboration management for group assignments. Supports team creation, member management, " +
                "invitations, and team-based submission tracking."
)
public class TeamController {

    private final TeamService teamService;
    private final SubmissionService submissionService;
    private final HashidService hashidService;

    @Operation(
        summary = "List teams for assignment",
        description = "Retrieve all teams for an assignment. Instructors and admins see all teams; " +
                    "students see only their own team."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Teams retrieved successfully",
            content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - assignment does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/assignments/{assignmentId}/teams")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> list(
            @PathVariable String assignmentId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long assignmentIdLong = hashidService.decodeOrThrow(assignmentId);
        List<Team> teams = teamService.listTeamsForAssignment(assignmentIdLong, user.getUserId(), user.getRole());
        List<TeamResponse> data = teams.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
        summary = "Create team",
        description = "Create new team for assignment. Required: team name. Team creator becomes first member. " +
                    "Returns HTTP 201 with team details."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Team created successfully",
            content = @Content(schema = @Schema(implementation = TeamResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - team name required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Conflict - user already in team for this assignment",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
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

    @Operation(
        summary = "Auto-assign teams",
        description = "Automatically create and assign teams for all enrolled students. Optional: maxTeamSize (default 3). " +
                    "Requires INSTRUCTOR role. Distributes students evenly across teams."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Teams auto-assigned successfully",
            content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - INSTRUCTOR role required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
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

    @Operation(
        summary = "Get team details",
        description = "Retrieve team information including member list and join status. " +
                    "Users can only view teams they're members of or belong to their course."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Team details retrieved",
            content = @Content(schema = @Schema(implementation = TeamResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - no access to this team",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - team does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/teams/{id}")
    public ResponseEntity<ApiResponse<TeamResponse>> get(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamId = hashidService.decodeOrThrow(id);
        Team team = teamService.getTeamByIdWithAccessControl(teamId, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(team)));
    }

    @Operation(
        summary = "Rename team",
        description = "Change team name. Only team creator or instructors can rename. Required: new name."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Team renamed successfully",
            content = @Content(schema = @Schema(implementation = TeamResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - invalid team data",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - only team creator or instructor can rename",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - team does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PutMapping("/teams/{id}")
    public ResponseEntity<ApiResponse<TeamResponse>> rename(
            @PathVariable String id,
            @Valid @RequestBody RenameTeamRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamId = hashidService.decodeOrThrow(id);
        Team team = teamService.renameTeam(teamId, request.getName(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(team)));
    }

    @Operation(
        summary = "Invite member to team",
        description = "Send team invitation to user by email. User receives notification and can accept/decline. " +
                    "Required: inviteeEmail. Only team members can invite."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Invitation sent successfully",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - invalid email or team full",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - must be team member to invite",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - user or team does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PostMapping("/teams/{id}/invite")
    public ResponseEntity<ApiResponse<Map<String, Object>>> invite(
            @PathVariable String id,
            @Valid @RequestBody TeamInviteRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamId = hashidService.decodeOrThrow(id);
        TeamMember member = teamService.inviteMember(teamId, request.getInviteeEmail(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("teamMemberId", hashidService.encode(member.getId()), "status", member.getStatus().name())));
    }

    @Operation(
        summary = "Respond to team invitation",
        description = "Accept or decline team membership invitation. Required: accept (true/false). " +
                    "Only the invited user can respond."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Invitation response processed",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - invalid response data",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - can only respond to your own invitations",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - invitation does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/team-members/{id}/respond")
    public ResponseEntity<ApiResponse<Map<String, String>>> respond(
            @PathVariable String id,
            @Valid @RequestBody TeamRespondRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamMemberId = hashidService.decodeOrThrow(id);
        TeamMember member = teamService.respondToInvite(teamMemberId, request.getAccept(), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", member.getStatus().name())));
    }

    @Operation(
        summary = "Remove team member",
        description = "Remove a member from a team. Team members can only remove themselves; " +
                    "instructors can remove any member."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Member removed successfully",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - insufficient permission to remove this member",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - team or member does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
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

    @Operation(
        summary = "Lock team",
        description = "Lock team from further membership changes. Locked teams prevent new invitations and membership modifications. " +
                    "Requires INSTRUCTOR role."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Team locked successfully",
            content = @Content(schema = @Schema(implementation = TeamResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - INSTRUCTOR role required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - team does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PostMapping("/teams/{id}/lock")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TeamResponse>> lock(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long teamId = hashidService.decodeOrThrow(id);
        Team team = teamService.lockTeam(teamId);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(team)));
    }
    
    @Operation(
        summary = "Get team submissions",
        description = "Retrieve all submissions for a team including version history. " +
                    "Team members can view their own submissions; instructors can view any team's submissions."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Team submissions retrieved",
            content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - no access to this team",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - team does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
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
