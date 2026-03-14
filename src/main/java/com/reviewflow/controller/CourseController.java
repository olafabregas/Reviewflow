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
import com.reviewflow.service.HashidService;
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
    private final HashidService hashidService;

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
            @PathVariable String id, 
            @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Long courseId = hashidService.decodeOrThrow(id);
        Course course = courseService.getCourseByIdWithAccessCheck(courseId, user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCourseRequest request) {
        Long courseId = hashidService.decodeOrThrow(id);
        Course course = courseService.updateCourse(courseId, request.getCode(), request.getName(), request.getTerm(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<CourseResponse>> archive(@PathVariable String id) {
        Long courseId = hashidService.decodeOrThrow(id);
        Course course = courseService.archiveCourse(courseId);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/instructors")
    public ResponseEntity<ApiResponse<Map<String, String>>> assignInstructor(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        Long courseId = hashidService.decodeOrThrow(id);
        String instructorIdHash = body.get("instructorId");
        if (instructorIdHash == null) throw new IllegalArgumentException("instructorId required");
        Long instructorId = hashidService.decodeOrThrow(instructorIdHash);
        courseService.assignInstructor(courseId, instructorId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Instructor assigned")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/instructors/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> removeInstructor(
            @PathVariable String id,
            @PathVariable String userId) {
        Long courseId = hashidService.decodeOrThrow(id);
        Long userIdLong = hashidService.decodeOrThrow(userId);
        courseService.removeInstructor(courseId, userIdLong);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Instructor removed")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/enroll")
    public ResponseEntity<ApiResponse<Map<String, String>>> enroll(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        Long courseId = hashidService.decodeOrThrow(id);
        String studentIdHash = body.get("studentId");
        if (studentIdHash == null) throw new IllegalArgumentException("studentId required");
        Long studentId = hashidService.decodeOrThrow(studentIdHash);
        courseService.enrollStudent(courseId, studentId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Student enrolled")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/enroll/bulk")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> bulkEnroll(
            @PathVariable String id,
            @Valid @RequestBody BulkEnrollRequest request) {
        Long courseId = hashidService.decodeOrThrow(id);
        List<Map<String, String>> results = courseService.bulkEnroll(courseId, request.getEmails());
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

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
