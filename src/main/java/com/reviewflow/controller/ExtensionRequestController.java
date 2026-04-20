package com.reviewflow.controller;

import com.reviewflow.model.dto.request.CreateExtensionRequest;
import com.reviewflow.model.dto.request.RespondExtensionRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.ExtensionRequestListItemResponse;
import com.reviewflow.model.dto.response.ExtensionRequestResponse;
import com.reviewflow.model.entity.ExtensionRequest;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.ExtensionRequestService;
import com.reviewflow.service.HashidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Extension Requests", description = "Assignment deadline extension request workflow")
public class ExtensionRequestController {

    private final ExtensionRequestService extensionRequestService;
    private final HashidService hashidService;

    @Operation(
        summary = "Request deadline extension",
        description = "Submit a deadline extension request for an assignment. Student-only. " +
                    "Request enters PENDING state and awaits instructor response."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Extension request created successfully",
            content = @Content(schema = @Schema(implementation = ExtensionRequestResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - invalid extension request data",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - STUDENT role required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - assignment does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PostMapping("/assignments/{id}/extension-requests")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<ExtensionRequestResponse>> create(
            @PathVariable("id") String assignmentId,
            @Valid @RequestBody CreateExtensionRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {

        ExtensionRequest created = extensionRequestService.create(
                assignmentId,
                user.getUserId(),
                request.getReason(),
                request.getRequestedDueAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toResponse(created)));
    }

    @Operation(
        summary = "Respond to extension request",
        description = "Approve or reject a pending extension request. Instructor-only. " +
                    "Approved requests update assignment due date and extend all team members' deadlines."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Extension request processed successfully",
            content = @Content(schema = @Schema(implementation = ExtensionRequestResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Bad Request - invalid response data or request already responded",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - INSTRUCTOR or ADMIN role required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - request does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/extension-requests/{id}/respond")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExtensionRequestResponse>> respond(
            @PathVariable("id") String id,
            @Valid @RequestBody RespondExtensionRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {

        Long extensionRequestId = hashidService.decodeOrThrow(id);
        ExtensionRequest updated = extensionRequestService.respond(
                extensionRequestId,
                user.getUserId(),
                request.getApprove(),
                request.getInstructorNote()
        );

        return ResponseEntity.ok(ApiResponse.ok(toResponse(updated)));
    }

    @Operation(
        summary = "List extension requests for assignment",
        description = "Get paginated list of all extension requests for an assignment. " +
                    "Includes all statuses (PENDING, APPROVED, REJECTED). Instructor-only."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Extension requests retrieved successfully",
            content = @Content(schema = @Schema(implementation = Page.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - INSTRUCTOR or ADMIN role required for course",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Not Found - assignment does not exist",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/assignments/{id}/extension-requests")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<ExtensionRequestListItemResponse>>> listByAssignment(
            @PathVariable("id") String assignmentId,
            Pageable pageable,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {

        Long decodedAssignmentId = hashidService.decodeOrThrow(assignmentId);
        Page<ExtensionRequest> page = extensionRequestService.getByAssignment(decodedAssignmentId, user.getUserId(), pageable);

        Page<ExtensionRequestListItemResponse> mapped = page.map(this::toListItemResponse);
        return ResponseEntity.ok(ApiResponse.ok(mapped));
    }

    @Operation(
        summary = "List my extension requests",
        description = "Get paginated list of authenticated student's extension requests. " +
                    "Shows all requests (PENDING, APPROVED, REJECTED) across all assignments."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "My extension requests retrieved successfully",
            content = @Content(schema = @Schema(implementation = Page.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden - STUDENT or ADMIN role required",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/students/me/extension-requests")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<ExtensionRequestListItemResponse>>> listMine(
            Pageable pageable,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {

        Page<ExtensionRequest> page = extensionRequestService.getMine(user.getUserId(), pageable);
        Page<ExtensionRequestListItemResponse> mapped = page.map(this::toListItemResponse);

        return ResponseEntity.ok(ApiResponse.ok(mapped));
    }

    private ExtensionRequestResponse toResponse(ExtensionRequest request) {
        return ExtensionRequestResponse.builder()
                .id(hashidService.encode(request.getId()))
                .assignmentId(hashidService.encode(request.getAssignment().getId()))
                .teamId(request.getTeam() != null ? hashidService.encode(request.getTeam().getId()) : null)
                .studentId(request.getStudent() != null ? hashidService.encode(request.getStudent().getId()) : null)
                .requestedById(request.getRequestedBy() != null ? hashidService.encode(request.getRequestedBy().getId()) : null)
                .respondedById(request.getRespondedBy() != null ? hashidService.encode(request.getRespondedBy().getId()) : null)
                .status(request.getStatus())
                .reason(request.getReason())
                .requestedDueAt(request.getRequestedDueAt())
                .instructorNote(request.getInstructorNote())
                .respondedAt(request.getRespondedAt())
                .createdAt(request.getCreatedAt())
                .build();
    }

    private ExtensionRequestListItemResponse toListItemResponse(ExtensionRequest request) {
        return ExtensionRequestListItemResponse.builder()
                .id(hashidService.encode(request.getId()))
                .studentName(request.getStudent() != null ? request.getStudent().getFullNameOrEmail() : null)
                .teamName(request.getTeam() != null ? request.getTeam().getName() : null)
                .reason(request.getReason())
                .requestedDueAt(request.getRequestedDueAt())
                .status(request.getStatus())
                .instructorNote(request.getInstructorNote())
                .respondedAt(request.getRespondedAt())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
