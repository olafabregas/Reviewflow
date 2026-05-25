package com.reviewflow.assignment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.assignment.exception.CannotDeleteUncategorizedException;
import com.reviewflow.assignment.exception.GroupNotInCourseException;
import com.reviewflow.assignment.dto.response.AssignmentGroupListResponse;
import com.reviewflow.assignment.dto.response.AssignmentGroupResponse;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.AssignmentGroup;
import com.reviewflow.shared.domain.Course;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.assignment.repository.AssignmentGroupRepository;
import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.course.repository.CourseRepository;
import com.reviewflow.admin.service.AuditService;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.shared.util.HashidService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused")
class AssignmentGroupServiceTest {

  @Mock private AssignmentGroupRepository assignmentGroupRepository;
  @Mock private AssignmentRepository assignmentRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private CourseEnrollmentRepository courseEnrollmentRepository;
  @Mock private CourseInstructorRepository courseInstructorRepository;
  @Mock private UserRepository userRepository;
  @Mock private AuditService auditService;
  @Mock private HashidService hashidService;
  @Mock private AssignmentGroupCacheEviction assignmentGroupCacheEviction;

  @InjectMocks private AssignmentGroupService assignmentGroupService;

  @Test
  void create_whenWeightsNotHundred_returnsWarning() {
    Long courseId = 10L;
    Long actorId = 77L;

    Course course = Course.builder().id(courseId).build();
    User actor = User.builder().id(actorId).role(UserRole.INSTRUCTOR).build();
    AssignmentGroup saved =
        AssignmentGroup.builder()
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
    when(courseInstructorRepository.existsByCourseIdAndUserId(courseId, actorId))
        .thenReturn(true);
    when(assignmentGroupRepository.save(any(AssignmentGroup.class))).thenReturn(saved);
    when(assignmentRepository.countByAssignmentGroupId(501L)).thenReturn(0L);
    when(assignmentGroupRepository.findByCourseIdOrderByDisplayOrderAsc(courseId))
        .thenReturn(List.of(saved));

    AssignmentGroupResponse response =
        assignmentGroupService.create(courseId, actorId, "Projects", new BigDecimal("40.00"), 0, 1);

    assertEquals("Projects", response.getName());
    assertEquals(Long.valueOf(0L), response.getAssignmentCount());
    assertEquals(
        "Group weights total 40%. Grades will be normalised to completed work.",
        response.getWeightWarning());
  }

  @Test
  void delete_uncategorized_throwsConflict() {
    Long groupId = 90L;
    Long actorId = 77L;
    Long courseId = 10L;

    AssignmentGroup uncategorized =
        AssignmentGroup.builder()
            .id(groupId)
            .course(Course.builder().id(courseId).build())
            .name("Uncategorized")
            .isUncategorized(true)
            .build();
    User actor = User.builder().id(actorId).role(UserRole.INSTRUCTOR).build();

    when(assignmentGroupRepository.findById(groupId)).thenReturn(Optional.of(uncategorized));
    when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
    when(courseInstructorRepository.existsByCourseIdAndUserId(courseId, actorId))
        .thenReturn(true);

    CannotDeleteUncategorizedException thrown =
        assertThrows(
            CannotDeleteUncategorizedException.class,
            () -> assignmentGroupService.delete(groupId, actorId));
    assertEquals("Cannot delete Uncategorized group", thrown.getMessage());

    verify(assignmentGroupRepository, never()).delete(any(AssignmentGroup.class));
  }

  @Test
  void moveAssignment_groupFromDifferentCourse_throwsGroupNotInCourse() {
    Long assignmentId = 100L;
    Long newGroupId = 200L;
    Long actorId = 77L;

    Assignment assignment =
        Assignment.builder()
            .id(assignmentId)
            .course(Course.builder().id(10L).build())
            .assignmentGroup(
                AssignmentGroup.builder().id(150L).course(Course.builder().id(10L).build()).build())
            .build();

    AssignmentGroup otherCourseGroup =
        AssignmentGroup.builder()
            .id(newGroupId)
            .course(Course.builder().id(11L).build())
            .name("Other Course Group")
            .build();

    User actor = User.builder().id(actorId).role(UserRole.INSTRUCTOR).build();

    when(assignmentRepository.findWithDetailsById(assignmentId))
        .thenReturn(Optional.of(assignment));
    when(assignmentGroupRepository.findById(newGroupId)).thenReturn(Optional.of(otherCourseGroup));
    when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
    when(courseInstructorRepository.existsByCourseIdAndUserId(10L, actorId)).thenReturn(true);

    GroupNotInCourseException thrown =
        assertThrows(
            GroupNotInCourseException.class,
            () -> assignmentGroupService.moveAssignment(assignmentId, newGroupId, actorId));
    assertEquals("Assignment group does not belong to this course", thrown.getMessage());
  }

  @Test
  void listByCourse_totalWeight100_hasNoWarning() {
    Long courseId = 10L;

    Course course = Course.builder().id(courseId).build();
    AssignmentGroup projects =
        AssignmentGroup.builder()
            .id(1L)
            .course(course)
            .name("Projects")
            .weight(new BigDecimal("40.00"))
            .dropLowestN(0)
            .displayOrder(1)
            .isUncategorized(false)
            .assignments(List.of())
            .build();
    AssignmentGroup exams =
        AssignmentGroup.builder()
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
    when(assignmentGroupRepository.findByCourseIdOrderByDisplayOrderAsc(courseId))
        .thenReturn(List.of(projects, exams));
    when(assignmentRepository.countByAssignmentGroupId(1L)).thenReturn(0L);
    when(assignmentRepository.countByAssignmentGroupId(2L)).thenReturn(0L);

    AssignmentGroupListResponse response = assignmentGroupService.listByCourse(courseId);

    assertEquals(new BigDecimal("100.00"), response.getTotalConfiguredWeight());
    assertNull(response.getWeightWarning());
  }

  @Test
  void verifyCanView_enrolledStudent_allowsAccess() {
    Long courseId = 10L;
    Long actorId = 88L;

    Course course = Course.builder().id(courseId).build();
    User actor = User.builder().id(actorId).role(UserRole.STUDENT).build();

    when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
    when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
    when(courseInstructorRepository.existsByCourseIdAndUserId(courseId, actorId))
        .thenReturn(false);
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(courseId, actorId))
        .thenReturn(true);

    assignmentGroupService.verifyCanView(courseId, actorId);
  }

  @Test
  void verifyCanView_nonEnrolledStudent_throwsForbidden() {
    Long courseId = 10L;
    Long actorId = 99L;

    Course course = Course.builder().id(courseId).build();
    User actor = User.builder().id(actorId).role(UserRole.STUDENT).build();

    when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
    when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
    when(courseInstructorRepository.existsByCourseIdAndUserId(courseId, actorId))
        .thenReturn(false);
    when(courseEnrollmentRepository.existsByCourseIdAndUserId(courseId, actorId))
        .thenReturn(false);

    AccessDeniedException thrown =
        assertThrows(
            AccessDeniedException.class,
            () -> assignmentGroupService.verifyCanView(courseId, actorId));
    assertEquals("Not authorized to view assignment groups for this course", thrown.getMessage());
  }
}
