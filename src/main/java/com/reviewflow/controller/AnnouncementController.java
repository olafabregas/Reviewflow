package com.reviewflow.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reviewflow.model.dto.request.CreateAnnouncementRequest;
import com.reviewflow.model.dto.response.AnnouncementResponse;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.PaginatedAnnouncementResponse;
import com.reviewflow.model.entity.Announcement;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.AnnouncementService;
import com.reviewflow.service.HashidService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AnnouncementController — handles all announcement endpoints per PRD-04. Thin
 * translators: all business logic delegated to AnnouncementService.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Announcements", description = "Course and platform-wide announcement management")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final HashidService hashidService;

    /**
     * POST /courses/{id}/announcements Create a course announcement (draft).
     * Only instructors of the course can create.
     *
     * @param courseId encoded course hashid
     * @param request title and body (recipientType ignored for course
     * announcements)
     * @param user authenticated user (must be instructor of course)
     * @return 201 Created with AnnouncementResponse
     */
    @Operation(
            summary = "Create course announcement",
            description = "Create a draft announcement for a course. Only instructors of the course can create. "
            + "Announcement starts in draft state and must be published to be visible."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Course announcement created successfully",
                content = @Content(schema = @Schema(implementation = AnnouncementResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid announcement data",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
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
                description = "Not Found - course does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PostMapping("/courses/{id}/announcements")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> createCourseAnnouncement(
            @PathVariable("id") String courseId,
            @Valid @RequestBody CreateAnnouncementRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {

        Announcement announcement = announcementService.createCourseAnnouncement(
                courseId,
                user.getUserId(),
                request.getTitle(),
                request.getBody()
        );

        AnnouncementResponse response = AnnouncementResponse.builder()
                .id(hashidService.encode(announcement.getId()))
                .courseId(announcement.getCourse() != null ? hashidService.encode(announcement.getCourse().getId()) : null)
                .title(announcement.getTitle())
                .body(announcement.getBody())
                .target(announcement.getTarget())
                .recipientType(announcement.getRecipientType())
                .isDraft(!announcement.getIsPublished())
                .publishedAt(announcement.getPublishedAt())
                .createdAt(announcement.getCreatedAt())
                .createdByName(getDisplayName(user))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }

    /**
     * POST /admin/announcements Create a platform-wide announcement (draft).
     * Only admins can create.
     *
     * @param request title, body, recipientType (required for platform
     * announcements)
     * @param user authenticated user (must be ADMIN)
     * @return 201 Created with AnnouncementResponse
     */
    @Operation(
            summary = "Create platform announcement",
            description = "Create a platform-wide announcement visible across all courses. Admin-only. "
            + "Announcement starts in draft state and must be published to be visible."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Platform announcement created successfully",
                content = @Content(schema = @Schema(implementation = AnnouncementResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid announcement data or missing recipientType",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PostMapping("/admin/announcements")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> createPlatformAnnouncement(
            @Valid @RequestBody CreateAnnouncementRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {

        Announcement announcement = announcementService.createPlatformAnnouncement(
                user.getUserId(),
                request.getTitle(),
                request.getBody(),
                request.getRecipientType()
        );

        AnnouncementResponse response = AnnouncementResponse.builder()
                .id(hashidService.encode(announcement.getId()))
                .courseId(null) // Platform announcements have no course
                .title(announcement.getTitle())
                .body(announcement.getBody())
                .target(announcement.getTarget())
                .recipientType(announcement.getRecipientType())
                .isDraft(!announcement.getIsPublished())
                .publishedAt(announcement.getPublishedAt())
                .createdAt(announcement.getCreatedAt())
                .createdByName(getDisplayName(user))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }

    /**
     * PATCH /announcements/{id}/publish Publish a draft announcement. Only the
     * creator or an admin can publish.
     *
     * @param id encoded announcement hashid
     * @param user authenticated user (must be creator or admin)
     * @return 200 OK with updated AnnouncementResponse (isDraft=false,
     * publishedAt set)
     */
    @Operation(
            summary = "Publish announcement",
            description = "Publish a draft announcement making it visible to users. Only creator or admin can publish. "
            + "Sets publishedAt timestamp and sends notifications."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Announcement published successfully",
                content = @Content(schema = @Schema(implementation = AnnouncementResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - only creator or admin can publish",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - announcement does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PatchMapping("/announcements/{id}/publish")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> publish(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {

        Long announcementId = hashidService.decodeOrThrow(id);
        Announcement announcement = announcementService.publish(announcementId, user.getUserId());

        AnnouncementResponse response = AnnouncementResponse.builder()
                .id(hashidService.encode(announcement.getId()))
                .courseId(announcement.getCourse() != null ? hashidService.encode(announcement.getCourse().getId()) : null)
                .title(announcement.getTitle())
                .body(announcement.getBody())
                .target(announcement.getTarget())
                .recipientType(announcement.getRecipientType())
                .isDraft(!announcement.getIsPublished())
                .publishedAt(announcement.getPublishedAt())
                .createdAt(announcement.getCreatedAt())
                .createdByName(getDisplayName(user))
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * DELETE /announcements/{id} Delete an announcement (hard delete). Only the
     * creator or an admin can delete.
     *
     * @param id encoded announcement hashid
     * @param user authenticated user (must be creator or admin)
     * @return 200 OK (no body)
     */
    @Operation(
            summary = "Delete announcement",
            description = "Delete an announcement permanently. Only creator or admin can delete. "
            + "Hard delete - announcement cannot be recovered."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Announcement deleted successfully"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - only creator or admin can delete",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - announcement does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {

        Long announcementId = hashidService.decodeOrThrow(id);
        announcementService.delete(announcementId, user.getUserId());

        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/{id}/announcements?page=0&size=20 List published
     * announcements for a course (paginated, by publishedAt DESC). Only
     * enrolled students or course instructors can view.
     *
     * @param courseId encoded course hashid
     * @param pageable pagination parameters
     * @param user authenticated user
     * @return 200 OK with PaginatedAnnouncementResponse
     */
    @Operation(
            summary = "List course announcements",
            description = "Get paginated list of published announcements for a course. "
            + "Only enrolled students and instructors can view. Sorted by publishedAt descending."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Course announcements retrieved successfully",
                content = @Content(schema = @Schema(implementation = PaginatedAnnouncementResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - only enrolled students and instructors can view",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - course does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/courses/{id}/announcements")
    public ResponseEntity<ApiResponse<PaginatedAnnouncementResponse>> listCourseAnnouncements(
            @PathVariable("id") String courseId,
            Pageable pageable,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {

        Long courseIdLong = hashidService.decodeOrThrow(courseId);
        Page<Announcement> page = announcementService.getByCourse(courseIdLong, user.getUserId(), pageable);

        PaginatedAnnouncementResponse response = PaginatedAnnouncementResponse.builder()
                .content(page.getContent().stream()
                        .map(a -> PaginatedAnnouncementResponse.AnnouncementListItem.builder()
                        .id(hashidService.encode(a.getId()))
                        .title(a.getTitle())
                        .body(a.getBody())
                        .publishedAt(a.getPublishedAt())
                        .createdByName(getCreatedByName(a))
                        .build())
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Helper to extract user display name from authenticated principal.
     */
    private String getDisplayName(ReviewFlowUserDetails user) {
        if (user.getFirstName() != null && user.getLastName() != null) {
            return user.getFirstName() + " " + user.getLastName();
        }
        if (user.getFirstName() != null) {
            return user.getFirstName();
        }
        return user.getUsername();  // username is typically email
    }

    /**
     * Helper to extract creator name from announcement entity.
     */
    private String getCreatedByName(Announcement announcement) {
        if (announcement.getCreatedBy().getFirstName() != null && announcement.getCreatedBy().getLastName() != null) {
            return announcement.getCreatedBy().getFirstName() + " " + announcement.getCreatedBy().getLastName();
        }
        if (announcement.getCreatedBy().getFirstName() != null) {
            return announcement.getCreatedBy().getFirstName();
        }
        return announcement.getCreatedBy().getEmail();
    }
}
