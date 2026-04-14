package com.reviewflow.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reviewflow.model.dto.request.BulkEnrollRequest;
import com.reviewflow.model.dto.request.CreateCourseRequest;
import com.reviewflow.model.dto.request.UpdateCourseRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.CourseResponse;
import com.reviewflow.model.dto.response.StudentResponse;
import com.reviewflow.model.entity.Course;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.CourseService;
import com.reviewflow.service.HashidService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(
        name = "Courses",
        description = "Course management endpoints. Supports creating, updating, archiving courses, +"
        + "and managing course instructors and student enrollments. Most endpoints require "
        + "ADMIN role except viewing endpoints which allow INSTRUCTOR access."
)
public class CourseController {

    private final CourseService courseService;
    private final HashidService hashidService;

    @Operation(
            summary = "List courses",
            description = "Retrieve paginated list of courses accessible to the authenticated user. "
            + "Admins see all courses, instructors see courses they teach, students see enrolled courses."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Courses retrieved successfully",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CourseResponse>>> list(
            @AuthenticationPrincipal ReviewFlowUserDetails user,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort) {

        Sort.Direction direction = sort.length > 1 && sort[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));

        Page<Course> courses = courseService.listCoursesForUserPaged(user.getUserId(), user.getRole(), archived, pageable);
        Page<CourseResponse> data = courses.map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @Operation(
            summary = "Create course",
            description = "Create new course. Requires ADMIN role. "
            + "Initializes course with code, name, term, and optional description."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Course created successfully",
                content = @Content(schema = @Schema(implementation = CourseResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid course data or duplicate code",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN or SYSTEM_ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> create(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Course course = courseService.createCourse(
                request.getCode(), request.getName(), request.getTerm(), request.getDescription(),
                user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toResponse(course)));
    }

    @Operation(
            summary = "Get course details",
            description = "Retrieve specific course details by ID. User must have access (admin, instructor, or enrolled student)."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Course details retrieved",
                content = @Content(schema = @Schema(implementation = CourseResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - no access to this course",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - course does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> get(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long courseId = hashidService.decodeOrThrow(id);
        Course course = courseService.getCourseByIdWithAccessCheck(courseId, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @Operation(
            summary = "Update course",
            description = "Update course details (code, name, term, description). Requires ADMIN role. Cannot update archived courses."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Course updated successfully",
                content = @Content(schema = @Schema(implementation = CourseResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid update data",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN or SYSTEM_ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - course does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCourseRequest request) {
        Long courseId = hashidService.decodeOrThrow(id);
        Course course = courseService.updateCourse(courseId, request.getCode(), request.getName(), request.getTerm(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @Operation(
            summary = "Archive course",
            description = "Archive (soft delete) a course. Archived courses remain in system but hidden from listings. Requires ADMIN or SYSTEM_ADMIN role."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Course archived successfully",
                content = @Content(schema = @Schema(implementation = CourseResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN or SYSTEM_ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - course does not exist",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @PatchMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<CourseResponse>> archive(@PathVariable String id) {
        Long courseId = hashidService.decodeOrThrow(id);
        Course course = courseService.archiveCourse(courseId);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @Operation(
            summary = "Assign instructor to course",
            description = "Assign an instructor (user with INSTRUCTOR role) to teach a course. Requires ADMIN role. "
            + "An instructor can be assigned to multiple courses."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Instructor assigned successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - instructorId missing or invalid",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @PostMapping("/{id}/instructors")
    public ResponseEntity<ApiResponse<Map<String, String>>> assignInstructor(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        Long courseId = hashidService.decodeOrThrow(id);
        String instructorIdHash = body.get("instructorId");
        if (instructorIdHash == null) {
            throw new IllegalArgumentException("instructorId required");
        }
        Long instructorId = hashidService.decodeOrThrow(instructorIdHash);
        courseService.assignInstructor(courseId, instructorId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Instructor assigned")));
    }

    @Operation(
            summary = "Remove instructor from course",
            description = "Remove an instructor from a course. The instructor will no longer be able to access the course. Requires ADMIN role."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Instructor removed successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - course or instructor not found",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @DeleteMapping("/{id}/instructors/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> removeInstructor(
            @PathVariable String id,
            @PathVariable String userId) {
        Long courseId = hashidService.decodeOrThrow(id);
        Long userIdLong = hashidService.decodeOrThrow(userId);
        courseService.removeInstructor(courseId, userIdLong);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Instructor removed")));
    }

    @Operation(
            summary = "Enroll student in course",
            description = "Enroll a single student in a course. If student is already enrolled, operation succeeds silently. Requires ADMIN or SYSTEM_ADMIN role."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Student enrolled successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - studentId missing or invalid",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/enroll")
    public ResponseEntity<ApiResponse<Map<String, String>>> enroll(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        Long courseId = hashidService.decodeOrThrow(id);
        String studentIdHash = body.get("studentId");
        if (studentIdHash == null) {
            throw new IllegalArgumentException("studentId required");
        }
        Long studentId = hashidService.decodeOrThrow(studentIdHash);
        courseService.enrollStudent(courseId, studentId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Student enrolled")));
    }

    @Operation(
            summary = "Bulk enroll students in course",
            description = "Enroll multiple students (by email) in a course in a single operation. Returns success/failure status for each email. Requires ADMIN role."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Bulk enrollment completed, returns status for each email",
                content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Bad Request - invalid request body",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/enroll/bulk")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> bulkEnroll(
            @PathVariable String id,
            @Valid @RequestBody BulkEnrollRequest request) {
        Long courseId = hashidService.decodeOrThrow(id);
        List<Map<String, String>> results = courseService.bulkEnroll(courseId, request.getEmails());
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    @Operation(
            summary = "Unenroll student from course",
            description = "Remove a student from course enrollment. Student will no longer see course or assignments. Requires ADMIN role."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Student unenrolled successfully",
                content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Not Found - course or student not found",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/enroll/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> unenroll(
            @PathVariable String id,
            @PathVariable String userId) {
        Long courseId = hashidService.decodeOrThrow(id);
        Long userIdLong = hashidService.decodeOrThrow(userId);
        courseService.unenrollStudent(courseId, userIdLong);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Student removed")));
    }

    @Operation(
            summary = "Get enrolledstudents in course",
            description = "Retrieve list of all students enrolled in the course with enrollment details. Requires ADMIN or INSTRUCTOR role."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Students retrieved successfully",
                content = @Content(schema = @Schema(implementation = List.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Forbidden - ADMIN or INSTRUCTOR role required",
                content = @Content(schema = @Schema(ref = "#/components/schemas/ApiErrorResponse"))
        )
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    @GetMapping("/{id}/students")
    public ResponseEntity<ApiResponse<List<StudentResponse>>> getStudents(
            @PathVariable String id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long courseId = hashidService.decodeOrThrow(id);
        // Check access for instructors
        courseService.checkInstructorAccess(courseId, user.getUserId(), user.getRole());

        List<com.reviewflow.model.entity.CourseEnrollment> enrollments = courseService.getEnrollmentsForCourse(courseId);
        List<StudentResponse> data = enrollments.stream()
                .map(e -> StudentResponse.builder()
                .userId(hashidService.encode(e.getUser().getId()))
                .email(e.getUser().getEmail())
                .firstName(e.getUser().getFirstName())
                .lastName(e.getUser().getLastName())
                .enrolledAt(e.getEnrolledAt())
                .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private CourseResponse toResponse(Course c) {
        return CourseResponse.builder()
                .id(hashidService.encode(c.getId()))
                .code(c.getCode())
                .name(c.getName())
                .term(c.getTerm())
                .description(c.getDescription())
                .isArchived(c.getIsArchived())
                .createdById(hashidService.encode(c.getCreatedBy() != null ? c.getCreatedBy().getId() : null))
                .instructorCount(courseService.getInstructorCount(c.getId()))
                .enrollmentCount(courseService.getEnrollmentCount(c.getId()))
                .assignmentCount(courseService.getAssignmentCount(c.getId()))
                .build();
    }
}
