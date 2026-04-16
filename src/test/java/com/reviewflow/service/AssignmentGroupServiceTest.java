package com.reviewflow.service;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.exception.CannotDeleteUncategorizedException;
import com.reviewflow.exception.GroupNotInCourseException;
import com.reviewflow.model.dto.response.AssignmentGroupListResponse;
import com.reviewflow.model.dto.response.AssignmentGroupResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.AssignmentGroup;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.repository.AssignmentGroupRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentGroupServiceTest {

    @Mock
    private AssignmentGroupRepository assignmentGroupRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    @SuppressWarnings("unused")
    private AuditService auditService;
    @Mock
    @SuppressWarnings("unused")
    private HashidService hashidService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache assignmentGroupsCache;
    @Mock
    @SuppressWarnings("unused")
    private Cache assignmentCache;
    @Mock
    @SuppressWarnings("unused")
    private Cache gradeOverviewCache;

    @InjectMocks
    private AssignmentGroupService assignmentGroupService;

    @Test
    void create_whenWeightsNotHundred_returnsWarning() {
        Long courseId = 10L;
        Long actorId = 77L;

        Course course = Course.builder().id(courseId).build();
        User actor = User.builder().id(actorId).role(UserRole.INSTRUCTOR).build();
        AssignmentGroup saved = AssignmentGroup.builder()
                .id(501L)
                .course(course)
                .name("Projects")
                .weight(new BigDecimal("40.00"))
                .dropLowestN(0)
                .displayOrder(1)
                .isUncategorized(false)
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorId)).thenReturn(true);
        when(assignmentGroupRepository.save(any(AssignmentGroup.class))).thenReturn(saved);
        when(assignmentRepository.countByAssignmentGroup_Id(501L)).thenReturn(0L);
        when(assignmentGroupRepository.findByCourse_IdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of(saved));

        AssignmentGroupResponse response = assignmentGroupService.create(
                courseId,
                actorId,
                "Projects",
                new BigDecimal("40.00"),
                0,
                1
        );

        assertEquals("Projects", response.getName());
        assertEquals(Long.valueOf(0L), response.getAssignmentCount());
        assertEquals("Group weights total 40%. Grades will be normalised to completed work.", response.getWeightWarning());
    }

    @Test
    void delete_uncategorized_throwsConflict() {
        Long groupId = 90L;
        Long actorId = 77L;
        Long courseId = 10L;

        AssignmentGroup uncategorized = AssignmentGroup.builder()
                .id(groupId)
                .course(Course.builder().id(courseId).build())
                .name("Uncategorized")
                .isUncategorized(true)
                .build();
        User actor = User.builder().id(actorId).role(UserRole.INSTRUCTOR).build();

        when(assignmentGroupRepository.findById(groupId)).thenReturn(Optional.of(uncategorized));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorId)).thenReturn(true);

        CannotDeleteUncategorizedException thrown = assertThrows(CannotDeleteUncategorizedException.class,
                () -> assignmentGroupService.delete(groupId, actorId));
        assertEquals("Cannot delete Uncategorized group", thrown.getMessage());

        verify(assignmentGroupRepository, never()).delete(any(AssignmentGroup.class));
    }

    @Test
    void moveAssignment_groupFromDifferentCourse_throwsGroupNotInCourse() {
        Long assignmentId = 100L;
        Long newGroupId = 200L;
        Long actorId = 77L;

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .course(Course.builder().id(10L).build())
                .assignmentGroup(AssignmentGroup.builder().id(150L).course(Course.builder().id(10L).build()).build())
                .build();

        AssignmentGroup otherCourseGroup = AssignmentGroup.builder()
                .id(newGroupId)
                .course(Course.builder().id(11L).build())
                .name("Other Course Group")
                .build();

        User actor = User.builder().id(actorId).role(UserRole.INSTRUCTOR).build();

        when(assignmentRepository.findWithDetailsById(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentGroupRepository.findById(newGroupId)).thenReturn(Optional.of(otherCourseGroup));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(10L, actorId)).thenReturn(true);

        GroupNotInCourseException thrown = assertThrows(GroupNotInCourseException.class,
                () -> assignmentGroupService.moveAssignment(assignmentId, newGroupId, actorId));
        assertEquals("Assignment group does not belong to this course", thrown.getMessage());
    }

    @Test
    void listByCourse_totalWeight100_hasNoWarning() {
        Long courseId = 10L;

        Course course = Course.builder().id(courseId).build();
        AssignmentGroup projects = AssignmentGroup.builder()
                .id(1L)
                .course(course)
                .name("Projects")
                .weight(new BigDecimal("40.00"))
                .dropLowestN(0)
                .displayOrder(1)
                .isUncategorized(false)
                .assignments(List.of())
                .build();
        AssignmentGroup exams = AssignmentGroup.builder()
                .id(2L)
                .course(course)
                .name("Exams")
                .weight(new BigDecimal("60.00"))
                .dropLowestN(0)
                .displayOrder(2)
                .isUncategorized(false)
                .assignments(List.of())
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(assignmentGroupRepository.findByCourse_IdOrderByDisplayOrderAsc(courseId)).thenReturn(List.of(projects, exams));
        when(assignmentRepository.countByAssignmentGroup_Id(1L)).thenReturn(0L);
        when(assignmentRepository.countByAssignmentGroup_Id(2L)).thenReturn(0L);

        AssignmentGroupListResponse response = assignmentGroupService.listByCourse(courseId);

        assertEquals(new BigDecimal("100.00"), response.getTotalConfiguredWeight());
        assertNull(response.getWeightWarning());
    }
}
