package com.reviewflow.service;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.model.dto.response.ClassRosterDto;
import com.reviewflow.model.dto.response.GradeOverviewDto;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.AssignmentGroup;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.CourseEnrollment;
import com.reviewflow.model.entity.Evaluation;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentGroupRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.CourseRepository;
import com.reviewflow.repository.EvaluationRepository;
import com.reviewflow.repository.InstructorScoreRepository;
import com.reviewflow.repository.SubmissionRepository;
import com.reviewflow.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradeCalculationServiceTest {

    @Mock
    private AssignmentGroupRepository assignmentGroupRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private EvaluationRepository evaluationRepository;
    @Mock
    private InstructorScoreRepository instructorScoreRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private HashidService hashidService;
    @Mock
    private AuditService auditService;
    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private GradeCalculationService gradeCalculationService;

    @BeforeEach
    void setUp() {
        lenient().when(hashidService.encode(isNull())).thenReturn(null);
        lenient().when(hashidService.encode(notNull())).thenAnswer(invocation -> "h" + invocation.<Long>getArgument(0));

        ReflectionTestUtils.setField(gradeCalculationService, "atRiskThreshold", BigDecimal.valueOf(70));
        ReflectionTestUtils.setField(gradeCalculationService, "aPlusMin", BigDecimal.valueOf(97));
        ReflectionTestUtils.setField(gradeCalculationService, "aMin", BigDecimal.valueOf(93));
        ReflectionTestUtils.setField(gradeCalculationService, "aMinusMin", BigDecimal.valueOf(90));
        ReflectionTestUtils.setField(gradeCalculationService, "bPlusMin", BigDecimal.valueOf(87));
        ReflectionTestUtils.setField(gradeCalculationService, "bMin", BigDecimal.valueOf(83));
        ReflectionTestUtils.setField(gradeCalculationService, "bMinusMin", BigDecimal.valueOf(80));
        ReflectionTestUtils.setField(gradeCalculationService, "cPlusMin", BigDecimal.valueOf(77));
        ReflectionTestUtils.setField(gradeCalculationService, "cMin", BigDecimal.valueOf(73));
        ReflectionTestUtils.setField(gradeCalculationService, "cMinusMin", BigDecimal.valueOf(70));
        ReflectionTestUtils.setField(gradeCalculationService, "dPlusMin", BigDecimal.valueOf(67));
        ReflectionTestUtils.setField(gradeCalculationService, "dMin", BigDecimal.valueOf(60));
    }

    @Test
    void calculateOverviewCached_appliesDropLowestAndWeightNormalisation() {
        Long courseId = 10L;
        Long studentId = 101L;

        Course course = Course.builder().id(courseId).code("CS401").name("Advanced SE").build();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        Assignment a1 = assignment(1L, "A1", SubmissionType.INDIVIDUAL, 100);
        Assignment a2 = assignment(2L, "A2", SubmissionType.INDIVIDUAL, 100);
        Assignment a3 = assignment(3L, "A3", SubmissionType.INDIVIDUAL, 100);

        AssignmentGroup projects = group(1L, "Projects", 40, 1, List.of(a1, a2));
        AssignmentGroup exams = group(2L, "Exams", 60, 0, List.of(a3));
        when(assignmentGroupRepository.findDetailedByCourseId(courseId)).thenReturn(List.of(projects, exams));

        when(teamRepository.findByAssignmentIdsAndMemberUserId(anyList(), eq(studentId))).thenReturn(List.of());

        Submission s1 = submission(11L, a1, studentId);
        Submission s2 = submission(12L, a2, studentId);
        Submission s3 = submission(13L, a3, studentId);
        when(submissionRepository.findLatestByAssignmentIdsAndStudentId(anyList(), eq(studentId)))
                .thenReturn(List.of(s1, s2, s3));

        Evaluation e1 = evaluation(21L, s1, 50);
        Evaluation e2 = evaluation(22L, s2, 90);
        Evaluation e3 = evaluation(23L, s3, 80);
        when(evaluationRepository.findPublishedFinalBySubmissionIds(anyList())).thenReturn(List.of(e1, e2, e3));
        when(instructorScoreRepository.findByAssignment_IdInAndStudent_IdAndIsPublishedTrue(anyList(), eq(studentId)))
                .thenReturn(List.of());

        GradeOverviewDto dto = gradeCalculationService.calculateOverviewCached(courseId, studentId);

        assertEquals(0, dto.getCurrentStanding().compareTo(BigDecimal.valueOf(84).setScale(2)));
        assertEquals("B", dto.getCurrentStandingLetter());
        assertNull(dto.getStatusMessage());

        var projectsGroup = dto.getGroups().stream().filter(g -> "Projects".equals(g.getName())).findFirst().orElseThrow();
        var droppedRow = projectsGroup.getAssignments().stream().filter(a -> "A1".equals(a.getTitle())).findFirst().orElseThrow();
        assertTrue(Boolean.TRUE.equals(droppedRow.getIsDropped()));
        assertEquals(0, projectsGroup.getGroupScorePercent().compareTo(BigDecimal.valueOf(90).setScale(2)));
    }

    @Test
    void calculateOverviewCached_whenNoPublishedGrades_setsStatusMessage() {
        Long courseId = 12L;
        Long studentId = 102L;

        Course course = Course.builder().id(courseId).code("CS402").name("Testing").build();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        Assignment a1 = assignment(4L, "Lab 1", SubmissionType.INDIVIDUAL, 100);
        AssignmentGroup labs = group(3L, "Labs", 100, 0, List.of(a1));
        when(assignmentGroupRepository.findDetailedByCourseId(courseId)).thenReturn(List.of(labs));

        when(teamRepository.findByAssignmentIdsAndMemberUserId(anyList(), eq(studentId))).thenReturn(List.of());
        when(submissionRepository.findLatestByAssignmentIdsAndStudentId(anyList(), eq(studentId))).thenReturn(List.of());
        when(instructorScoreRepository.findByAssignment_IdInAndStudent_IdAndIsPublishedTrue(anyList(), eq(studentId)))
                .thenReturn(List.of());

        GradeOverviewDto dto = gradeCalculationService.calculateOverviewCached(courseId, studentId);

        assertNull(dto.getCurrentStanding());
        assertNull(dto.getCurrentStandingLetter());
        assertEquals("No grades have been published for this course yet", dto.getStatusMessage());
    }

    @Test
    void calculateRoster_sortsAndFlagsAtRiskStudents() {
        Long courseId = 20L;
        Long actorId = 999L;

        Course course = Course.builder().id(courseId).code("CS500").name("Roster").build();
        when(courseRepository.existsById(courseId)).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorId)).thenReturn(true);
        when(cacheManager.getCache("classStatistics")).thenReturn(null);

        User studentLow = User.builder().id(101L).firstName("Low").lastName("Student").email("low@test.local").build();
        User studentHigh = User.builder().id(102L).firstName("High").lastName("Student").email("high@test.local").build();
        CourseEnrollment e1 = CourseEnrollment.builder().course(course).user(studentLow).build();
        CourseEnrollment e2 = CourseEnrollment.builder().course(course).user(studentHigh).build();
        when(courseEnrollmentRepository.findWithUserByCourseId(courseId)).thenReturn(List.of(e1, e2));

        Assignment assignment = assignment(1L, "Exam", SubmissionType.INDIVIDUAL, 100);
        AssignmentGroup group = group(10L, "Exams", 100, 0, List.of(assignment));
        when(assignmentGroupRepository.findDetailedByCourseId(courseId)).thenReturn(List.of(group));

        when(teamRepository.findByAssignmentIdsAndMemberUserId(anyList(), eq(101L))).thenReturn(List.of());
        when(teamRepository.findByAssignmentIdsAndMemberUserId(anyList(), eq(102L))).thenReturn(List.of());

        Submission lowSubmission = submission(1001L, assignment, 101L);
        Submission highSubmission = submission(1002L, assignment, 102L);
        when(submissionRepository.findLatestByAssignmentIdsAndStudentId(anyList(), eq(101L))).thenReturn(List.of(lowSubmission));
        when(submissionRepository.findLatestByAssignmentIdsAndStudentId(anyList(), eq(102L))).thenReturn(List.of(highSubmission));

        Evaluation lowEval = evaluation(2001L, lowSubmission, 60);
        Evaluation highEval = evaluation(2002L, highSubmission, 90);
        when(evaluationRepository.findPublishedFinalBySubmissionIds(anyList())).thenAnswer(invocation -> {
            List<Long> submissionIds = invocation.getArgument(0);
            if (submissionIds.contains(1001L)) {
                return List.of(lowEval);
            }
            if (submissionIds.contains(1002L)) {
                return List.of(highEval);
            }
            return List.of();
        });
        when(instructorScoreRepository.findByAssignment_IdInAndStudent_IdAndIsPublishedTrue(anyList(), eq(101L))).thenReturn(List.of());
        when(instructorScoreRepository.findByAssignment_IdInAndStudent_IdAndIsPublishedTrue(anyList(), eq(102L))).thenReturn(List.of());

        ClassRosterDto roster = gradeCalculationService.calculateRoster(courseId, actorId, UserRole.INSTRUCTOR, "standing", "desc", false);

        assertEquals(2, roster.getStudents().size());
        assertEquals("h102", roster.getStudents().get(0).getStudentId());
        assertEquals("h101", roster.getStudents().get(1).getStudentId());
        assertFalse(Boolean.TRUE.equals(roster.getStudents().get(0).getAtRisk()));
        assertTrue(Boolean.TRUE.equals(roster.getStudents().get(1).getAtRisk()));
        assertEquals(1, roster.getClassStats().getAtRiskCount());
    }

    @Test
    void evictCourseGradeCaches_clearsOverviewAndEvictsRoster() {
        Cache gradeOverviewCache = mock(Cache.class);
        Cache classStatisticsCache = mock(Cache.class);

        when(cacheManager.getCache(CacheConfig.CACHE_GRADE_OVERVIEW)).thenReturn(gradeOverviewCache);
        when(cacheManager.getCache("classStatistics")).thenReturn(classStatisticsCache);

        gradeCalculationService.evictCourseGradeCaches(55L);

        verify(gradeOverviewCache).clear();
        verify(classStatisticsCache).evict(55L);
    }

    private Assignment assignment(Long id, String title, SubmissionType type, int maxScore) {
        return Assignment.builder()
                .id(id)
                .title(title)
                .submissionType(type)
                .maxScore(BigDecimal.valueOf(maxScore))
                .dueAt(Instant.parse("2026-04-01T00:00:00Z"))
                .build();
    }

    private AssignmentGroup group(Long id, String name, int weight, int dropLowestN, List<Assignment> assignments) {
        return AssignmentGroup.builder()
                .id(id)
                .name(name)
                .weight(BigDecimal.valueOf(weight))
                .dropLowestN(dropLowestN)
                .assignments(assignments)
                .build();
    }

    private Submission submission(Long submissionId, Assignment assignment, Long studentId) {
        return Submission.builder()
                .id(submissionId)
                .assignment(assignment)
                .student(User.builder().id(studentId).build())
                .versionNumber(1)
                .uploadedAt(Instant.parse("2026-04-02T00:00:00Z"))
                .isLate(false)
                .build();
    }

    private Evaluation evaluation(Long evaluationId, Submission submission, int score) {
        return Evaluation.builder()
                .id(evaluationId)
                .submission(submission)
                .isDraft(false)
                .totalScore(BigDecimal.valueOf(score))
                .publishedAt(Instant.parse("2026-04-03T00:00:00Z"))
                .build();
    }
}
