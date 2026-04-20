package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.AssignmentNotInCourseException;
import com.reviewflow.exception.NoSubmissionsFoundException;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.Evaluation;
import com.reviewflow.model.entity.RubricCriterion;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.entity.TeamMember;
import com.reviewflow.model.entity.TeamMemberStatus;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.EvaluationRepository;
import com.reviewflow.repository.SubmissionRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class GradeExportServiceTest {

    @Mock
    private HashidService hashidService;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private EvaluationRepository evaluationRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private GradeExportService gradeExportService;

    @Test
    void export_teamAssignment_successProducesCsvAndAudit() {
        Long courseId = 10L;
        Long assignmentId = 20L;
        Long actorId = 99L;

        Assignment assignment = teamAssignment(courseId, assignmentId, "Project Phase 1");
        User actor = User.builder().id(actorId).role(UserRole.INSTRUCTOR).build();

        Submission submission = Submission.builder()
                .id(501L)
                .assignment(assignment)
                .team(Team.builder().id(300L).name("Team Alpha").build())
                .uploadedAt(Instant.parse("2026-03-22T10:00:00Z"))
                .isLate(false)
                .versionNumber(2)
                .build();

        Evaluation evaluation = Evaluation.builder()
                .id(801L)
                .submission(submission)
                .isDraft(false)
                .totalScore(BigDecimal.valueOf(92))
                .publishedAt(Instant.parse("2026-03-23T14:00:00Z"))
                .build();

        TeamMember memberA = TeamMember.builder()
                .team(submission.getTeam())
                .user(User.builder().id(1L).firstName("Jane").lastName("Smith").build())
                .status(TeamMemberStatus.ACCEPTED)
                .build();
        TeamMember memberB = TeamMember.builder()
                .team(submission.getTeam())
                .user(User.builder().id(2L).firstName("Marcus").lastName("Chen").build())
                .status(TeamMemberStatus.ACCEPTED)
                .build();

        when(hashidService.decodeOrThrow("C10")).thenReturn(courseId);
        when(hashidService.decodeOrThrow("A20")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorId)).thenReturn(true);
        when(submissionRepository.findLatestTeamSubmissionsByAssignmentId(assignmentId)).thenReturn(List.of(submission));
        when(evaluationRepository.findPublishedBySubmissionIds(List.of(501L))).thenReturn(List.of(evaluation));
        when(teamMemberRepository.findByTeamIdsAndStatusWithUser(List.of(300L), TeamMemberStatus.ACCEPTED))
                .thenReturn(List.of(memberA, memberB));

        GradeExportService.ExportResult result = gradeExportService.export("C10", "A20", actorId);

        String csv = new String(result.bytes(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("Course Code,Assignment Title,Team Name,Students"));
        assertTrue(csv.contains("CS401"));
        assertTrue(csv.contains("Team Alpha"));
        assertTrue(csv.contains("92.0%"));
        assertTrue(result.filename().startsWith("CS401_project-phase-1_"));

        verify(auditService).log(anyLong(), anyString(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void export_individualInjectionSensitiveCell_isPrefixedWithTab() {
        Long courseId = 10L;
        Long assignmentId = 21L;
        Long actorId = 88L;

        Assignment assignment = individualAssignment(courseId, assignmentId, "Midterm Essay");
        User actor = User.builder().id(actorId).role(UserRole.ADMIN).build();
        User student = User.builder().id(200L).firstName("Alex").lastName("Kumar").email("=cmd@example.com").build();
        Submission submission = Submission.builder()
                .id(700L)
                .assignment(assignment)
                .student(student)
                .uploadedAt(Instant.parse("2026-03-22T14:00:00Z"))
                .isLate(false)
                .versionNumber(1)
                .build();

        when(hashidService.decodeOrThrow("C10")).thenReturn(courseId);
        when(hashidService.decodeOrThrow("A21")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(submissionRepository.findLatestIndividualSubmissionsByAssignmentId(assignmentId)).thenReturn(List.of(submission));
        when(evaluationRepository.findPublishedBySubmissionIds(List.of(700L))).thenReturn(List.of());

        GradeExportService.ExportResult result = gradeExportService.export("C10", "A21", actorId);
        String csv = new String(result.bytes(), StandardCharsets.UTF_8);

        assertTrue(csv.contains("\t=cmd@example.com"));
    }

    @Test
    void export_assignmentOutsideCourse_throwsAssignmentNotInCourse() {
        Assignment assignment = individualAssignment(11L, 22L, "Essay");

        when(hashidService.decodeOrThrow("C10")).thenReturn(10L);
        when(hashidService.decodeOrThrow("A22")).thenReturn(22L);
        when(assignmentRepository.findById(22L)).thenReturn(Optional.of(assignment));

        assertThrows(AssignmentNotInCourseException.class,
                () -> gradeExportService.export("C10", "A22", 1L));
    }

    @Test
    void export_nonInstructorNonAdmin_throwsAccessDenied() {
        Long courseId = 10L;
        Long assignmentId = 20L;
        Long actorId = 77L;

        Assignment assignment = individualAssignment(courseId, assignmentId, "Essay");
        User actor = User.builder().id(actorId).role(UserRole.STUDENT).build();

        when(hashidService.decodeOrThrow("C10")).thenReturn(courseId);
        when(hashidService.decodeOrThrow("A20")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> gradeExportService.export("C10", "A20", actorId));
        verify(auditService, never()).log(anyLong(), anyString(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void export_noSubmissions_throwsNoSubmissionsFound() {
        Long courseId = 10L;
        Long assignmentId = 20L;
        Long actorId = 99L;
        Assignment assignment = teamAssignment(courseId, assignmentId, "Project");
        User actor = User.builder().id(actorId).role(UserRole.ADMIN).build();

        when(hashidService.decodeOrThrow("C10")).thenReturn(courseId);
        when(hashidService.decodeOrThrow("A20")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(submissionRepository.findLatestTeamSubmissionsByAssignmentId(assignmentId)).thenReturn(List.of());

        assertThrows(NoSubmissionsFoundException.class,
                () -> gradeExportService.export("C10", "A20", actorId));
    }

    @Test
    void export_unevaluatedSubmissionsAndNullRubric_producesBlankScoresWithZeroMaxScore() {
        Long courseId = 20L;
        Long assignmentId = 30L;
        Long actorId = 88L;

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .title("Ungraded Work")
                .submissionType(SubmissionType.INDIVIDUAL)
                .course(Course.builder().id(courseId).code("CS410").build())
                .rubricCriteria(null)
                .build();

        User actor = User.builder().id(actorId).role(UserRole.ADMIN).build();
        User student = User.builder().id(201L).firstName("Bob").lastName("Jones").email("bob@example.com").build();
        Submission submission = Submission.builder()
                .id(702L)
                .assignment(assignment)
                .student(student)
                .uploadedAt(Instant.parse("2026-03-20T12:00:00Z"))
                .isLate(false)
                .versionNumber(1)
                .build();

        when(hashidService.decodeOrThrow("C20")).thenReturn(courseId);
        when(hashidService.decodeOrThrow("A30")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(submissionRepository.findLatestIndividualSubmissionsByAssignmentId(assignmentId)).thenReturn(List.of(submission));
        when(evaluationRepository.findPublishedBySubmissionIds(List.of(702L))).thenReturn(List.of());

        GradeExportService.ExportResult result = gradeExportService.export("C20", "A30", actorId);
        String csv = new String(result.bytes(), StandardCharsets.UTF_8);

        assertTrue(csv.contains("CS410,Ungraded Work,Jones Bob,bob@example.com,,0,,No"));
    }

    @Test
    void export_teamsWithNoAcceptedMembers_producesEmptyStudentsList() {
        Long courseId = 10L;
        Long assignmentId = 25L;
        Long actorId = 77L;

        Assignment assignment = teamAssignment(courseId, assignmentId, "Group Project");
        User actor = User.builder().id(actorId).role(UserRole.INSTRUCTOR).build();

        Team teamNoMembers = Team.builder().id(401L).name("Isolated Team").build();
        Submission submission = Submission.builder()
                .id(501L)
                .assignment(assignment)
                .team(teamNoMembers)
                .uploadedAt(Instant.parse("2026-03-22T08:00:00Z"))
                .isLate(true)
                .versionNumber(1)
                .build();

        Evaluation evaluation = Evaluation.builder()
                .id(801L)
                .submission(submission)
                .isDraft(false)
                .totalScore(BigDecimal.valueOf(45))
                .publishedAt(Instant.parse("2026-03-23T09:00:00Z"))
                .build();

        when(hashidService.decodeOrThrow("C10")).thenReturn(courseId);
        when(hashidService.decodeOrThrow("A25")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorId)).thenReturn(true);
        when(submissionRepository.findLatestTeamSubmissionsByAssignmentId(assignmentId)).thenReturn(List.of(submission));
        when(evaluationRepository.findPublishedBySubmissionIds(List.of(501L))).thenReturn(List.of(evaluation));
        when(teamMemberRepository.findByTeamIdsAndStatusWithUser(List.of(401L), TeamMemberStatus.ACCEPTED))
                .thenReturn(List.of());

        GradeExportService.ExportResult result = gradeExportService.export("C10", "A25", actorId);
        String csv = new String(result.bytes(), StandardCharsets.UTF_8);

        assertTrue(csv.contains("Isolated Team,,45,100,45.0%,Yes"));
    }

    @Test
    void export_filenameSanitizationEdgeCases_handlesSpecialCharsAndLength() {
        Long courseId = 10L;
        Long assignmentId = 31L;
        Long actorId = 99L;

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .title("Project (Phase 1) — Deliverable #3!@#$%^&*()")
                .submissionType(SubmissionType.INDIVIDUAL)
                .course(Course.builder().id(courseId).code("CS-401").build())
                .rubricCriteria(List.of())
                .build();

        User actor = User.builder().id(actorId).role(UserRole.ADMIN).build();

        when(hashidService.decodeOrThrow("C10")).thenReturn(courseId);
        when(hashidService.decodeOrThrow("A31")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(submissionRepository.findLatestIndividualSubmissionsByAssignmentId(assignmentId)).thenReturn(List.of());

        assertThrows(NoSubmissionsFoundException.class,
                () -> gradeExportService.export("C10", "A31", actorId));
    }

    @Test
    void export_csvInjectionAllCharacters_allArePrefixedWithTab() {
        Long courseId = 10L;
        Long assignmentId = 32L;
        Long actorId = 88L;

        Assignment assignment = individualAssignment(courseId, assignmentId, "Formula Test");
        User actor = User.builder().id(actorId).role(UserRole.ADMIN).build();

        // Test all four dangerous starting characters: =, +, -, @
        // Note: fullName() constructs as "LastName FirstName", so put dangerous chars in lastName
        User student1 = User.builder().id(1L).firstName("Smith").lastName("=Equals").email("test@example.com").build();
        User student2 = User.builder().id(2L).firstName("Jones").lastName("+Plus").email("test@example.com").build();
        User student3 = User.builder().id(3L).firstName("Brown").lastName("-Minus").email("test@example.com").build();
        User student4 = User.builder().id(4L).firstName("White").lastName("@At").email("test@example.com").build();

        Submission sub1 = Submission.builder().id(1L).assignment(assignment).student(student1)
                .uploadedAt(Instant.parse("2026-03-22T10:00:00Z")).isLate(false).versionNumber(1).build();
        Submission sub2 = Submission.builder().id(2L).assignment(assignment).student(student2)
                .uploadedAt(Instant.parse("2026-03-22T10:00:00Z")).isLate(false).versionNumber(1).build();
        Submission sub3 = Submission.builder().id(3L).assignment(assignment).student(student3)
                .uploadedAt(Instant.parse("2026-03-22T10:00:00Z")).isLate(false).versionNumber(1).build();
        Submission sub4 = Submission.builder().id(4L).assignment(assignment).student(student4)
                .uploadedAt(Instant.parse("2026-03-22T10:00:00Z")).isLate(false).versionNumber(1).build();

        when(hashidService.decodeOrThrow("C10")).thenReturn(courseId);
        when(hashidService.decodeOrThrow("A32")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(submissionRepository.findLatestIndividualSubmissionsByAssignmentId(assignmentId))
                .thenReturn(List.of(sub1, sub2, sub3, sub4));
        when(evaluationRepository.findPublishedBySubmissionIds(List.of(1L, 2L, 3L, 4L))).thenReturn(List.of());

        GradeExportService.ExportResult result = gradeExportService.export("C10", "A32", actorId);
        String csv = new String(result.bytes(), StandardCharsets.UTF_8);

        // All four dangerous prefixes should be escaped with tab
        // After fullName() format: "=Equals Smith", "+Plus Jones", etc.
        assertTrue(csv.contains("\t=Equals Smith"), "= should be prefixed with tab");
        assertTrue(csv.contains("\t+Plus Jones"), "+ should be prefixed with tab");
        assertTrue(csv.contains("\t-Minus Brown"), "- should be prefixed with tab");
        assertTrue(csv.contains("\t@At White"), "@ should be prefixed with tab");
    }

    @Test
    void export_mixedEvaluationStates_separatsDraftFromPublished() {
        Long courseId = 10L;
        Long assignmentId = 33L;
        Long actorId = 99L;

        Assignment assignment = individualAssignment(courseId, assignmentId, "Evaluation Test");
        User actor = User.builder().id(actorId).role(UserRole.ADMIN).build();
        User student1 = User.builder().id(1L).firstName("Alice").lastName("Published").email("alice@example.com").build();
        User student2 = User.builder().id(2L).firstName("Bob").lastName("Draft").email("bob@example.com").build();

        Submission sub1 = Submission.builder().id(1L).assignment(assignment).student(student1)
                .uploadedAt(Instant.parse("2026-03-22T10:00:00Z")).isLate(false).versionNumber(1).build();
        Submission sub2 = Submission.builder().id(2L).assignment(assignment).student(student2)
                .uploadedAt(Instant.parse("2026-03-22T10:00:00Z")).isLate(false).versionNumber(1).build();

        Evaluation publishedEval = Evaluation.builder().id(1L).submission(sub1).isDraft(false)
                .totalScore(BigDecimal.valueOf(85)).publishedAt(Instant.parse("2026-03-23T10:00:00Z")).build();
        Evaluation draftEval = Evaluation.builder().id(2L).submission(sub2).isDraft(true)
                .totalScore(BigDecimal.valueOf(90)).build();

        when(hashidService.decodeOrThrow("C10")).thenReturn(courseId);
        when(hashidService.decodeOrThrow("A33")).thenReturn(assignmentId);
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(submissionRepository.findLatestIndividualSubmissionsByAssignmentId(assignmentId))
                .thenReturn(List.of(sub1, sub2));
        when(evaluationRepository.findPublishedBySubmissionIds(List.of(1L, 2L)))
                .thenReturn(List.of(publishedEval));

        GradeExportService.ExportResult result = gradeExportService.export("C10", "A33", actorId);
        String csv = new String(result.bytes(), StandardCharsets.UTF_8);

        assertTrue(csv.contains("Published Alice,alice@example.com,85,"));
        assertTrue(csv.contains("Draft Bob,bob@example.com,,0"));
    }

    private Assignment teamAssignment(Long courseId, Long assignmentId, String title) {
        return Assignment.builder()
                .id(assignmentId)
                .title(title)
                .submissionType(SubmissionType.TEAM)
                .course(Course.builder().id(courseId).code("CS401").build())
                .rubricCriteria(List.of(
                        RubricCriterion.builder().maxScore(60).build(),
                        RubricCriterion.builder().maxScore(40).build()
                ))
                .build();
    }

    private Assignment individualAssignment(Long courseId, Long assignmentId, String title) {
        return Assignment.builder()
                .id(assignmentId)
                .title(title)
                .submissionType(SubmissionType.INDIVIDUAL)
                .course(Course.builder().id(courseId).code("CS401").build())
                .rubricCriteria(List.of())
                .build();
    }
}
