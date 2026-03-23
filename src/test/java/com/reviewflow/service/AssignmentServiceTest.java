package com.reviewflow.service;

import com.reviewflow.exception.SubmissionTypeLockedException;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.CourseRepository;
import com.reviewflow.repository.EvaluationRepository;
import com.reviewflow.repository.RubricCriterionRepository;
import com.reviewflow.repository.RubricScoreRepository;
import com.reviewflow.repository.SubmissionRepository;
import com.reviewflow.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private RubricCriterionRepository rubricCriterionRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private EvaluationRepository evaluationRepository;
    @Mock
    private RubricScoreRepository rubricScoreRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private HashidService hashidService;

    @InjectMocks
    private AssignmentService assignmentService;

    @Test
    void createAssignment_individualType_forcesNullMaxTeamSize() {
        Long courseId = 10L;
        Long creatorId = 20L;
        Course course = Course.builder().id(courseId).build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, creatorId)).thenReturn(true);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Assignment result = assignmentService.createAssignment(
                courseId,
                "Essay",
                "desc",
                Instant.now().plusSeconds(3600),
                5,
                SubmissionType.INDIVIDUAL,
                null,
                true,
                creatorId
        );

        assertEquals(SubmissionType.INDIVIDUAL, result.getSubmissionType());
        assertNull(result.getMaxTeamSize());
    }

    @Test
    void createAssignment_teamType_requiresMaxTeamSize() {
        Long courseId = 10L;
        Long creatorId = 20L;
        Course course = Course.builder().id(courseId).build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, creatorId)).thenReturn(true);

        assertThrows(ValidationException.class, () -> assignmentService.createAssignment(
                courseId,
                "Project",
                "desc",
                Instant.now().plusSeconds(3600),
                null,
                SubmissionType.TEAM,
                null,
                true,
                creatorId
        ));
    }

    @Test
    void updateAssignment_submissionTypeChangeBlockedWhenSubmissionsExist() {
        Long assignmentId = 101L;
        Long updaterId = 202L;
        Course course = Course.builder().id(303L).build();
        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .course(course)
                .title("A1")
                .submissionType(SubmissionType.TEAM)
                .maxTeamSize(4)
                .build();

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(course.getId(), updaterId)).thenReturn(true);
        when(teamRepository.findByAssignment_Id(assignmentId)).thenReturn(List.of());
        when(submissionRepository.findByAssignment_Id(assignmentId)).thenReturn(List.of(com.reviewflow.model.entity.Submission.builder().id(1L).build()));

        assertThrows(SubmissionTypeLockedException.class, () -> assignmentService.updateAssignment(
                assignmentId,
                null,
                null,
                null,
                2,
                SubmissionType.INDIVIDUAL,
                null,
                updaterId
        ));
    }

    @Test
    void updateAssignment_submissionTypeChangeBlockedWhenTeamsExist() {
        Long assignmentId = 111L;
        Long updaterId = 222L;
        Course course = Course.builder().id(333L).build();
        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .course(course)
                .title("A2")
                .submissionType(SubmissionType.INDIVIDUAL)
                .build();

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(course.getId(), updaterId)).thenReturn(true);
        when(teamRepository.findByAssignment_Id(assignmentId)).thenReturn(List.of(Team.builder().id(9L).build()));

        assertThrows(SubmissionTypeLockedException.class, () -> assignmentService.updateAssignment(
                assignmentId,
                null,
                null,
                null,
                3,
                SubmissionType.TEAM,
                null,
                updaterId
        ));
    }
}
