package com.reviewflow.controller;

import com.reviewflow.model.dto.request.BulkEnrollRequest;
import com.reviewflow.model.dto.request.CreateCourseRequest;
import com.reviewflow.model.dto.request.UpdateCourseRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.CourseResponse;
import com.reviewflow.model.dto.response.StudentResponse;
import com.reviewflow.model.entity.Course;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

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

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> create(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Course course = courseService.createCourse(
                request.getCode(), request.getName(), request.getTerm(), request.getDescription(),
                user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toResponse(course)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> get(
            @PathVariable Long id, 
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Course course = courseService.getCourseByIdWithAccessCheck(id, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseRequest request) {
        Course course = courseService.updateCourse(id, request.getCode(), request.getName(), request.getTerm(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<CourseResponse>> archive(@PathVariable Long id) {
        Course course = courseService.archiveCourse(id);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/instructors")
    public ResponseEntity<ApiResponse<Map<String, String>>> assignInstructor(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long instructorId = body.get("instructorId");
        if (instructorId == null) throw new IllegalArgumentException("instructorId required");
        courseService.assignInstructor(id, instructorId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Instructor assigned")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/instructors/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> removeInstructor(
            @PathVariable Long id,
            @PathVariable Long userId) {
        courseService.removeInstructor(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Instructor removed")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/enroll")
    public ResponseEntity<ApiResponse<Map<String, String>>> enroll(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long studentId = body.get("studentId");
        if (studentId == null) throw new IllegalArgumentException("studentId required");
        courseService.enrollStudent(id, studentId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Student enrolled")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/enroll/bulk")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> bulkEnroll(
            @PathVariable Long id,
            @Valid @RequestBody BulkEnrollRequest request) {
        List<Map<String, String>> results = courseService.bulkEnroll(id, request.getEmails());
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/enroll/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> unenroll(
            @PathVariable Long id,
            @PathVariable Long userId) {
        courseService.unenrollStudent(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Student removed")));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('INSTRUCTOR')")
    @GetMapping("/{id}/students")
    public ResponseEntity<ApiResponse<List<StudentResponse>>> getStudents(
            @PathVariable Long id,
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        // Check access for instructors
        courseService.checkInstructorAccess(id, user.getUserId(), user.getRole());
        
        List<com.reviewflow.model.entity.CourseEnrollment> enrollments = courseService.getEnrollmentsForCourse(id);
        List<StudentResponse> data = enrollments.stream()
                .map(e -> StudentResponse.builder()
                        .userId(e.getUser().getId())
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
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .term(c.getTerm())
                .description(c.getDescription())
                .isArchived(c.getIsArchived())
                .createdById(c.getCreatedBy() != null ? c.getCreatedBy().getId() : null)
                .instructorCount(courseService.getInstructorCount(c.getId()))
                .enrollmentCount(courseService.getEnrollmentCount(c.getId()))
                .assignmentCount(courseService.getAssignmentCount(c.getId()))
                .build();
    }
}
