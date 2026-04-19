package com.reviewflow.controller;

import com.reviewflow.model.dto.request.AssignModuleRequest;
import com.reviewflow.model.dto.request.CreateAssignmentModuleRequest;
import com.reviewflow.model.dto.request.ReorderModulesRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.AssignmentModuleMoveResponse;
import com.reviewflow.model.dto.response.AssignmentModuleResponse;
import com.reviewflow.model.dto.response.CourseModulesResponse;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.HashidService;
import com.reviewflow.service.ModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Assignment Modules", description = "Manage organizational modules within a course")
public class ModuleController {

    private final ModuleService moduleService;
    private final HashidService hashidService;

    @Operation(summary = "Create module", description = "Create a module for course organization.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Module created successfully", content = @Content(schema = @Schema(implementation = AssignmentModuleResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse")))
    })
    @PostMapping("/courses/{courseId}/modules")
    public ResponseEntity<ApiResponse<AssignmentModuleResponse>> create(
            @PathVariable String courseId,
            @Valid @RequestBody CreateAssignmentModuleRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        AssignmentModuleResponse response = moduleService.create(
                hashidService.decodeOrThrow(courseId),
                user.getUserId(),
                request.getName(),
                request.getDisplayOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Operation(summary = "List modules", description = "List modules and unmoduled assignments for a course.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Modules retrieved successfully", content = @Content(schema = @Schema(implementation = CourseModulesResponse.class)))
    })
    @GetMapping("/courses/{courseId}/modules")
    public ResponseEntity<ApiResponse<CourseModulesResponse>> listByCourse(
            @PathVariable String courseId,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        CourseModulesResponse response = moduleService.listByCourse(hashidService.decodeOrThrow(courseId));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Update module", description = "Update module metadata such as name and display order.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module updated successfully", content = @Content(schema = @Schema(implementation = AssignmentModuleResponse.class)))
    })
    @PutMapping("/modules/{id}")
    public ResponseEntity<ApiResponse<AssignmentModuleResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody CreateAssignmentModuleRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        AssignmentModuleResponse response = moduleService.update(
                hashidService.decodeOrThrow(id),
                user.getUserId(),
                request.getName(),
                request.getDisplayOrder());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Assign assignment to module", description = "Move assignment into a module or remove module assignment.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Assignment module updated successfully", content = @Content(schema = @Schema(implementation = AssignmentModuleMoveResponse.class)))
    })
    @PatchMapping("/assignments/{id}/module")
    public ResponseEntity<ApiResponse<AssignmentModuleMoveResponse>> assignAssignmentToModule(
            @PathVariable String id,
            @RequestBody AssignModuleRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long moduleId = request.getModuleId() != null ? hashidService.decodeOrThrow(request.getModuleId()) : null;
        AssignmentModuleMoveResponse response = moduleService.assignToModule(
                hashidService.decodeOrThrow(id),
                moduleId,
                user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Reorder modules", description = "Normalize and persist module display order for a course.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module order updated successfully", content = @Content(schema = @Schema(implementation = CourseModulesResponse.class)))
    })
    @PatchMapping("/courses/{courseId}/modules/reorder")
    public ResponseEntity<ApiResponse<CourseModulesResponse>> reorder(
            @PathVariable String courseId,
            @Valid @RequestBody ReorderModulesRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        CourseModulesResponse response = moduleService.reorder(
                hashidService.decodeOrThrow(courseId),
                request.getOrder().stream().map(hashidService::decodeOrThrow).toList(),
                user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Delete module", description = "Delete a module and unmodule linked assignments.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module deleted successfully", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @DeleteMapping("/modules/{id}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> delete(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        long affected = moduleService.delete(hashidService.decodeOrThrow(id), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("unmoduledAssignmentCount", affected)));
    }
}
