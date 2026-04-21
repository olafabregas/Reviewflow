package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import com.reviewflow.exception.CourseArchivedReadOnlyException;
import com.reviewflow.exception.ModuleNotInCourseException;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.AssignmentGroup;
import com.reviewflow.model.entity.AssignmentModule;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentModuleRepository;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.CourseRepository;
import com.reviewflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleServiceTest {

    @Mock
    private AssignmentModuleRepository assignmentModuleRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private HashidService hashidService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache courseModulesCache;
    @Mock
    private Cache assignmentCache;

    @InjectMocks
    private ModuleService moduleService;

    @Test
    void create_archivedCourse_throwsCourseArchivedReadOnly() {
        Long courseId = 10L;
        Long actorId = 77L;

        Course archivedCourse = Course.builder().id(courseId).isArchived(true).build();
        User actor = User.builder().id(actorId).role(UserRole.ADMIN).build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(archivedCourse));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));

        CourseArchivedReadOnlyException thrown = assertThrows(
                CourseArchivedReadOnlyException.class,
                () -> moduleService.create(courseId, actorId, "Week 1", 1)
        );

        assertEquals("COURSE_ARCHIVED_READ_ONLY", thrown.getCode());
        verify(assignmentModuleRepository, never()).save(any(AssignmentModule.class));
    }

    @Test
    void assignToModule_moduleFromDifferentCourse_throwsModuleNotInCourse() {
        Long assignmentId = 100L;
        Long moduleId = 200L;
        Long actorId = 77L;

        Course assignmentCourse = Course.builder().id(10L).isArchived(false).build();
        Course otherCourse = Course.builder().id(11L).isArchived(false).build();

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .course(assignmentCourse)
                .assignmentGroup(AssignmentGroup.builder().id(300L).name("Projects").build())
                .submissionType(SubmissionType.INDIVIDUAL)
                .build();

        AssignmentModule otherCourseModule = AssignmentModule.builder()
                .id(moduleId)
                .course(otherCourse)
                .name("Week X")
                .displayOrder(1)
                .build();

        User actor = User.builder().id(actorId).role(UserRole.INSTRUCTOR).build();

        when(assignmentRepository.findWithDetailsById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentModuleRepository.findById(moduleId)).thenReturn(Optional.of(otherCourseModule));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(10L, actorId)).thenReturn(true);

        ModuleNotInCourseException thrown = assertThrows(
                ModuleNotInCourseException.class,
                () -> moduleService.assignToModule(assignmentId, moduleId, actorId)
        );

        assertEquals("MODULE_NOT_IN_COURSE", thrown.getCode());
    }

    @Test
    void reorder_duplicateIds_throwsValidationError() {
        Long courseId = 10L;
        Long actorId = 77L;

        Course course = Course.builder().id(courseId).isArchived(false).build();
        User actor = User.builder().id(actorId).role(UserRole.ADMIN).build();

        AssignmentModule m1 = AssignmentModule.builder().id(1L).course(course).name("W1").displayOrder(0).updatedAt(Instant.now()).build();
        AssignmentModule m2 = AssignmentModule.builder().id(2L).course(course).name("W2").displayOrder(1).updatedAt(Instant.now()).build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(assignmentModuleRepository.findByCourse_IdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of(m1, m2));

        ValidationException thrown = assertThrows(
                ValidationException.class,
                () -> moduleService.reorder(courseId, List.of(1L, 1L), actorId)
        );

        assertEquals("VALIDATION_ERROR", thrown.getCode());
        verify(assignmentModuleRepository, never()).saveAll(any());
    }
}
