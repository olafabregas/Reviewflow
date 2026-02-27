package com.reviewflow.controller;

import com.reviewflow.model.dto.request.CreateCourseRequest;
import com.reviewflow.model.dto.response.ApiResponse;
import com.reviewflow.model.dto.response.CourseResponse;
import com.reviewflow.model.entity.Course;
import com.reviewflow.security.ReviewFlowUserDetails;
import com.reviewflow.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseResponse>>> list(@AuthenticationPrincipal ReviewFlowUserDetails user) {
        List<Course> courses = courseService.listCoursesForUser(user.getUserId(), user.getRole());
        List<CourseResponse> data = courses.stream().map(this::toResponse).collect(Collectors.toList());
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
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseResponse>> get(@PathVariable Long id, @AuthenticationPrincipal ReviewFlowUserDetails user) {
        Course course = courseService.getCourseById(id);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(course)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<Map<String, String>>> archive(@PathVariable Long id) {
        courseService.archiveCourse(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Course archived")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/instructors")
    public ResponseEntity<ApiResponse<Map<String, String>>> assignInstructor(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long userId = body.get("userId");
        if (userId == null) throw new IllegalArgumentException("userId required");
        courseService.assignInstructor(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Instructor assigned")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/enroll")
    public ResponseEntity<ApiResponse<Map<String, String>>> enroll(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long userId = body.get("userId");
        if (userId == null) throw new IllegalArgumentException("userId required");
        courseService.enrollStudent(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Student enrolled")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/enroll/bulk")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkEnroll(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        List<String> errors = courseService.bulkEnrollFromCsv(id, file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("errors", errors)));
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
                .build();
    }
}
