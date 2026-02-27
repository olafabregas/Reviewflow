package com.reviewflow.service;

import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.entity.*;
import com.reviewflow.model.entity.Course;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.CourseRepository;
import com.reviewflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public Course createCourse(String code, String name, String term, String description, Long createdById) {
        User creator = userRepository.findById(createdById)
                .orElseThrow(() -> new ResourceNotFoundException("User", createdById));
        Course course = Course.builder()
                .code(code)
                .name(name)
                .term(term)
                .description(description)
                .isArchived(false)
                .createdBy(creator)
                .createdAt(Instant.now())
                .build();
        course = courseRepository.save(course);
        return course;
    }

    public Course getCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    public List<Course> listCoursesForUser(Long userId, UserRole role) {
        if (role == UserRole.ADMIN) {
            return courseRepository.findAll();
        }
        if (role == UserRole.INSTRUCTOR) {
            return courseRepository.findByInstructorId(userId);
        }
        return courseRepository.findByEnrolledUserId(userId);
    }

    @Transactional
    public void archiveCourse(Long courseId) {
        Course course = getCourseById(courseId);
        course.setIsArchived(true);
        courseRepository.save(course);
    }

    @Transactional
    public void assignInstructor(Long courseId, Long userId) {
        Course course = getCourseById(courseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
            return;
        }
        CourseInstructor ci = CourseInstructor.builder()
                .course(course)
                .user(user)
                .assignedAt(Instant.now())
                .build();
        courseInstructorRepository.save(ci);
    }

    @Transactional
    public void enrollStudent(Long courseId, Long userId) {
        Course course = getCourseById(courseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
            return;
        }
        CourseEnrollment en = CourseEnrollment.builder()
                .course(course)
                .user(user)
                .enrolledAt(Instant.now())
                .build();
        courseEnrollmentRepository.save(en);
    }

    @Transactional
    public List<String> bulkEnrollFromCsv(Long courseId, MultipartFile file) {
        Course course = getCourseById(courseId);
        List<String> errors = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int row = 0;
            while ((line = reader.readLine()) != null) {
                row++;
                String email = line.trim();
                if (email.isEmpty()) continue;
                User user = userRepository.findByEmail(email).orElse(null);
                if (user == null) {
                    errors.add("Row " + row + ": User not found: " + email);
                    continue;
                }
                if (courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, user.getId())) {
                    continue;
                }
                CourseEnrollment en = CourseEnrollment.builder()
                        .course(course)
                        .user(user)
                        .enrolledAt(Instant.now())
                        .build();
                courseEnrollmentRepository.save(en);
            }
        } catch (Exception e) {
            errors.add("Failed to read CSV: " + e.getMessage());
        }
        return errors;
    }
}
