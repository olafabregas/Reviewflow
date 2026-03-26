package com.reviewflow.controller;

import com.reviewflow.model.dto.request.CreateExtensionRequest;
import com.reviewflow.model.dto.request.RespondExtensionRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.ExtensionRequestListItemResponse;
import com.reviewflow.model.dto.response.ExtensionRequestResponse;
import com.reviewflow.model.entity.ExtensionRequest;
import com.reviewflow.model.entity.User;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.ExtensionRequestService;
import com.reviewflow.service.HashidService;
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
public class ExtensionRequestController {

    private final ExtensionRequestService extensionRequestService;
    private final HashidService hashidService;

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
                .studentName(request.getStudent() != null ? fullNameOrEmail(request.getStudent()) : null)
                .teamName(request.getTeam() != null ? request.getTeam().getName() : null)
                .reason(request.getReason())
                .requestedDueAt(request.getRequestedDueAt())
                .status(request.getStatus())
                .instructorNote(request.getInstructorNote())
                .respondedAt(request.getRespondedAt())
                .createdAt(request.getCreatedAt())
                .build();
    }

    private String fullNameOrEmail(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }
}
