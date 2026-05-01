package com.reviewflow.assignment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.reviewflow.assignment.exception.SubmissionTypeLockedException;
import com.reviewflow.shared.exception.ValidationException;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.AssignmentGroup;
import com.reviewflow.shared.domain.Course;
import com.reviewflow.shared.domain.Team;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.assignment.repository.AssignmentGroupRepository;
import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.course.repository.CourseRepository;
import com.reviewflow.grading.repository.RubricCriterionRepository;
import com.reviewflow.grading.repository.RubricScoreRepository;
import com.reviewflow.submission.repository.SubmissionRepository;
import com.reviewflow.team.repository.TeamRepository;
import com.reviewflow.shared.util.HashidService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

  @Mock private AssignmentRepository assignmentRepository;

  @Mock
  @SuppressWarnings("unused")
  private RubricCriterionRepository rubricCriterionRepository;

  @Mock private CourseRepository courseRepository;
  @Mock private CourseInstructorRepository courseInstructorRepository;

  @Mock
  @SuppressWarnings("unused")
  private com.reviewflow.course.repository.CourseEnrollmentRepository courseEnrollmentRepository;

  @Mock private SubmissionRepository submissionRepository;

  @Mock
  @SuppressWarnings("unused")
  private com.reviewflow.evaluation.repository.EvaluationRepository evaluationRepository;

  @Mock
  @SuppressWarnings("unused")
  private RubricScoreRepository rubricScoreRepository;

  @Mock private TeamRepository teamRepository;
  @Mock private AssignmentGroupRepository assignmentGroupRepository;

  @Mock
  @SuppressWarnings("unused")
  private HashidService hashidService;

  @InjectMocks private AssignmentService assignmentService;

  @Test
  void createAssignment_individualType_forcesNullMaxTeamSize() {
    Long courseId = 10L;
    Long creatorId = 20L;
    Course course = Course.builder().id(courseId).build();
    AssignmentGroup uncategorized =
        AssignmentGroup.builder()
            .id(901L)
            .course(course)
            .name("Uncategorized")
            .isUncategorized(true)
            .build();

    when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
    when(courseInstructorRepository.existsByCourseIdAndUserId(courseId, creatorId))
        .thenReturn(true);
    when(assignmentGroupRepository.findByCourseIdAndIsUncategorizedTrue(courseId))
        .thenReturn(Optional.of(uncategorized));
    when(assignmentRepository.save(any(Assignment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Assignment result =
        assignmentService.createAssignment(
            courseId,
            "Essay",
            "desc",
            Instant.now().plusSeconds(3600),
            5,
            SubmissionType.INDIVIDUAL,
            null,
            true,
            creatorId);

    assertEquals(SubmissionType.INDIVIDUAL, result.getSubmissionType());
    assertNull(result.getMaxTeamSize());
  }

  @Test
  void createAssignment_teamType_requiresMaxTeamSize() {
    Long courseId = 10L;
    Long creatorId = 20L;
    Course course = Course.builder().id(courseId).build();

    when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
    when(courseInstructorRepository.existsByCourseIdAndUserId(courseId, creatorId))
        .thenReturn(true);

    ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () ->
                assignmentService.createAssignment(
                    courseId,
                    "Project",
                    "desc",
                    Instant.now().plusSeconds(3600),
                    null,
                    SubmissionType.TEAM,
                    null,
                    true,
                    creatorId));
    assertEquals("Max team size must be between 1 and 10", thrown.getMessage());
  }

  @Test
  void updateAssignment_submissionTypeChangeBlockedWhenSubmissionsExist() {
    Long assignmentId = 101L;
    Long updaterId = 202L;
    Course course = Course.builder().id(303L).build();
    Assignment assignment =
        Assignment.builder()
            .id(assignmentId)
            .course(course)
            .title("A1")
            .submissionType(SubmissionType.TEAM)
            .maxTeamSize(4)
            .build();

    when(assignmentRepository.findWithDetailsById(assignmentId))
        .thenReturn(Optional.of(assignment));
    when(courseInstructorRepository.existsByCourseIdAndUserId(course.getId(), updaterId))
        .thenReturn(true);
    when(teamRepository.findByAssignmentId(assignmentId)).thenReturn(List.of());
    when(submissionRepository.findByAssignmentId(assignmentId))
        .thenReturn(List.of(com.reviewflow.shared.domain.Submission.builder().id(1L).build()));

    SubmissionTypeLockedException thrown =
        assertThrows(
            SubmissionTypeLockedException.class,
            () ->
                assignmentService.updateAssignment(
                    assignmentId, null, null, null, 2, SubmissionType.INDIVIDUAL, null, updaterId));
    assertEquals(
        "Assignment submission type cannot be changed after teams or submissions exist",
        thrown.getMessage());
  }

  @Test
  void updateAssignment_submissionTypeChangeBlockedWhenTeamsExist() {
    Long assignmentId = 111L;
    Long updaterId = 222L;
    Course course = Course.builder().id(333L).build();
    Assignment assignment =
        Assignment.builder()
            .id(assignmentId)
            .course(course)
            .title("A2")
            .submissionType(SubmissionType.INDIVIDUAL)
            .build();

    when(assignmentRepository.findWithDetailsById(assignmentId))
        .thenReturn(Optional.of(assignment));
    when(courseInstructorRepository.existsByCourseIdAndUserId(course.getId(), updaterId))
        .thenReturn(true);
    when(teamRepository.findByAssignmentId(assignmentId))
        .thenReturn(List.of(Team.builder().id(9L).build()));

    SubmissionTypeLockedException thrown =
        assertThrows(
            SubmissionTypeLockedException.class,
            () ->
                assignmentService.updateAssignment(
                    assignmentId, null, null, null, 3, SubmissionType.TEAM, null, updaterId));
    assertEquals(
        "Assignment submission type cannot be changed after teams or submissions exist",
        thrown.getMessage());
  }
}
