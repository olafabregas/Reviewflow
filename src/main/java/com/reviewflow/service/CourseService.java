package com.reviewflow.service;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.exception.DuplicateResourceException;
import com.reviewflow.exception.InvalidRoleException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.entity.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.reviewflow.model.entity.Course;
import com.reviewflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final UserRepository userRepository;
    private final AssignmentGroupRepository assignmentGroupRepository;
    private final AssignmentRepository assignmentRepository;
    private final AuditService auditService;
    private final AdminStatsService adminStatsService;

    private static final int UNCATEGORIZED_DISPLAY_ORDER = 999;

    @Transactional
    public Course createCourse(String code, String name, String term, String description, Long createdById) {
        // Check for duplicate course code
        if (courseRepository.existsByCode(code)) {
            throw new DuplicateResourceException(
                "A course with code " + code + " already exists",
                "COURSE_CODE_EXISTS"
            );
        }
        
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

            AssignmentGroup uncategorized = AssignmentGroup.builder()
                .course(course)
                .name("Uncategorized")
                .weight(BigDecimal.ZERO)
                .dropLowestN(0)
                .displayOrder(UNCATEGORIZED_DISPLAY_ORDER)
                .isUncategorized(true)
                .createdBy(creator)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            uncategorized = assignmentGroupRepository.save(uncategorized);
        
        auditService.log(createdById, "COURSE_CREATED", "Course", course.getId(), 
            "Created course: " + code, null);
            auditService.log(createdById, "ASSIGNMENT_GROUP_CREATED", "AssignmentGroup", uncategorized.getId(),
                "Created group: Uncategorized", null);
        
        adminStatsService.evictStats();
        return course;
    }

    public Course getCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    @Cacheable(value = CacheConfig.CACHE_USER_COURSES, key = "#userId")
    @Transactional(readOnly = true)
    public List<Course> listCoursesForUser(Long userId, UserRole role) {
        if (role == UserRole.ADMIN) {
            return courseRepository.findAll();
        }
        if (role == UserRole.INSTRUCTOR) {
            return courseRepository.findByInstructorId(userId);
        }
        return courseRepository.findByEnrolledUserId(userId);
    }

    public Page<Course> listCoursesForUserPaged(Long userId, UserRole role, Boolean archived, Pageable pageable) {
        if (role == UserRole.ADMIN) {
            return courseRepository.findAllFiltered(archived, pageable);
        }
        if (role == UserRole.INSTRUCTOR) {
            return courseRepository.findByInstructorIdPaged(userId, archived, pageable);
        }
        return courseRepository.findByEnrolledUserIdPaged(userId, archived, pageable);
    }

    @CacheEvict(value = CacheConfig.CACHE_USER_COURSES, allEntries = true)
    @Transactional
    public Course archiveCourse(Long courseId) {
        Course course = getCourseById(courseId);
        // Toggle archive status
        course.setIsArchived(!Boolean.TRUE.equals(course.getIsArchived()));
        course = courseRepository.save(course);
        adminStatsService.evictStats();
        return course;
    }

    @CacheEvict(value = CacheConfig.CACHE_USER_COURSES, allEntries = true)
    @Transactional
    public void assignInstructor(Long courseId, Long userId) {
        Course course = getCourseById(courseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        
        // Check if user is an instructor
        if (user.getRole() != UserRole.INSTRUCTOR) {
            throw new InvalidRoleException("User is not an instructor", "NOT_AN_INSTRUCTOR");
        }
        
        // Check if already assigned
        if (courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
            throw new DuplicateResourceException("Instructor already assigned to this course", "ALREADY_ASSIGNED");
        }
        
        CourseInstructor ci = CourseInstructor.builder()
                .course(course)
                .user(user)
                .assignedAt(Instant.now())
                .build();
        courseInstructorRepository.save(ci);
    }

    @CacheEvict(value = CacheConfig.CACHE_USER_COURSES, key = "#userId")
    @Transactional
    public void enrollStudent(Long courseId, Long userId) {
        Course course = getCourseById(courseId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        
        // Check if user is a student
        if (user.getRole() != UserRole.STUDENT) {
            throw new InvalidRoleException("User is not a student", "NOT_A_STUDENT");
        }
        
        // Check if already enrolled
        if (courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
            throw new DuplicateResourceException("Student already enrolled in this course", "ALREADY_ENROLLED");
        }
        
        CourseEnrollment en = CourseEnrollment.builder()
                .course(course)
                .user(user)
                .enrolledAt(Instant.now())
                .build();
        courseEnrollmentRepository.save(en);
    }

    @Transactional
    public List<Map<String, String>> bulkEnroll(Long courseId, List<String> emails) {
        getCourseById(courseId); // validates course exists
        List<Map<String, String>> results = new ArrayList<>();
        Pattern emailPattern = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
        for (String email : emails) {
            if (email == null || email.isBlank() || !emailPattern.matcher(email.trim()).matches()) {
                results.add(Map.of("email", email != null ? email : "", "status", "INVALID_EMAIL"));
                continue;
            }
            String trimmed = email.trim();
            User user = userRepository.findByEmail(trimmed).orElse(null);
            if (user == null) {
                results.add(Map.of("email", trimmed, "status", "NOT_FOUND"));
                continue;
            }
            if (user.getRole() != UserRole.STUDENT) {
                results.add(Map.of("email", trimmed, "status", "NOT_A_STUDENT"));
                continue;
            }
            if (courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, user.getId())) {
                results.add(Map.of("email", trimmed, "status", "ALREADY_ENROLLED"));
                continue;
            }
            Course course = courseRepository.findById(courseId).orElseThrow();
            CourseEnrollment en = CourseEnrollment.builder()
                    .course(course)
                    .user(user)
                    .enrolledAt(Instant.now())
                    .build();
            courseEnrollmentRepository.save(en);
            results.add(Map.of("email", trimmed, "status", "ENROLLED"));
        }
        return results;
    }

    @Transactional
    public Course updateCourse(Long courseId, String code, String name, String term, String description) {
        Course course = getCourseById(courseId);
        
        // Check for duplicate code if changing code
        if (code != null && !code.isBlank() && !code.equals(course.getCode())) {
            if (courseRepository.existsByCodeAndIdNot(code, courseId)) {
                throw new DuplicateResourceException(
                    "A course with code " + code + " already exists",
                    "COURSE_CODE_EXISTS"
                );
            }
            course.setCode(code);
        }
        
        if (name != null && !name.isBlank()) course.setName(name);
        if (term != null) course.setTerm(term);
        if (description != null) course.setDescription(description);
        return courseRepository.save(course);
    }

    @CacheEvict(value = CacheConfig.CACHE_USER_COURSES, allEntries = true)
    @Transactional
    public void removeInstructor(Long courseId, Long userId) {
        getCourseById(courseId);
        courseInstructorRepository.deleteByCourse_IdAndUser_Id(courseId, userId);
    }

    @CacheEvict(value = CacheConfig.CACHE_USER_COURSES, key = "#userId")
    @Transactional
    public void unenrollStudent(Long courseId, Long userId) {
        getCourseById(courseId);
        courseEnrollmentRepository.deleteByCourse_IdAndUser_Id(courseId, userId);
    }

    public List<CourseEnrollment> getEnrollmentsForCourse(Long courseId) {
        getCourseById(courseId);
        return courseEnrollmentRepository.findByCourse_Id(courseId);
    }

    public List<User> getStudentsForCourse(Long courseId) {
        getCourseById(courseId);
        return courseEnrollmentRepository.findByCourse_Id(courseId)
                .stream()
                .map(CourseEnrollment::getUser)
                .toList();
    }

    public Course getCourseByIdWithAccessCheck(Long courseId, Long userId, UserRole role) {
        Course course = getCourseById(courseId);
        
        // ADMIN can access any course
        if (role == UserRole.ADMIN) {
            return course;
        }
        
        // INSTRUCTOR can only access courses they're assigned to
        if (role == UserRole.INSTRUCTOR) {
            boolean isAssigned = courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, userId);
            if (!isAssigned) {
                throw new AccessDeniedException("Access denied");
            }
            return course;
        }
        
        // STUDENT can only access courses they're enrolled in
        if (role == UserRole.STUDENT) {
            boolean isEnrolled = courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, userId);
            if (!isEnrolled) {
                throw new AccessDeniedException("Access denied");
            }
            return course;
        }
        
        throw new AccessDeniedException("Access denied");
    }

    public void checkInstructorAccess(Long courseId, Long userId, UserRole role) {
        if (role == UserRole.ADMIN) {
            return; // Admin has access to all courses
        }
        
        if (role == UserRole.INSTRUCTOR) {
            boolean isAssigned = courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, userId);
            if (!isAssigned) {
                throw new AccessDeniedException("Access denied");
            }
            return;
        }
        
        throw new AccessDeniedException("Access denied");
    }

    public int getInstructorCount(Long courseId) {
        return (int) courseInstructorRepository.countByCourse_Id(courseId);
    }

    public int getEnrollmentCount(Long courseId) {
        return (int) courseEnrollmentRepository.countByCourse_Id(courseId);
    }

    public int getAssignmentCount(Long courseId) {
        return assignmentRepository.findByCourse_Id(courseId).size();
    }
}
