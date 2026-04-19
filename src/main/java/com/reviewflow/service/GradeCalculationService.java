package com.reviewflow.service;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.GradeOverviewUnavailableException;
import com.reviewflow.exception.NotEnrolledException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.dto.response.AssignmentGradeDto;
import com.reviewflow.model.dto.response.ClassRosterDto;
import com.reviewflow.model.dto.response.ClassStatsDto;
import com.reviewflow.model.dto.response.GradeOverviewDto;
import com.reviewflow.model.dto.response.GroupGradeDto;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.AssignmentGroup;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.CourseEnrollment;
import com.reviewflow.model.entity.Evaluation;
import com.reviewflow.model.entity.InstructorScore;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.entity.Team;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GradeCalculationService {

    private static final String CACHE_CLASS_STATISTICS = "classStatistics";

    private final AssignmentGroupRepository assignmentGroupRepository;
    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final SubmissionRepository submissionRepository;
    private final EvaluationRepository evaluationRepository;
    private final InstructorScoreRepository instructorScoreRepository;
    private final TeamRepository teamRepository;
    private final HashidService hashidService;
    private final AuditService auditService;
    private final CacheManager cacheManager;

    @Value("${grade.at-risk-threshold:70}")
    private BigDecimal atRiskThreshold;

    @Value("${grade.letter.a-plus-min:97}")
    private BigDecimal aPlusMin;
    @Value("${grade.letter.a-min:93}")
    private BigDecimal aMin;
    @Value("${grade.letter.a-minus-min:90}")
    private BigDecimal aMinusMin;
    @Value("${grade.letter.b-plus-min:87}")
    private BigDecimal bPlusMin;
    @Value("${grade.letter.b-min:83}")
    private BigDecimal bMin;
    @Value("${grade.letter.b-minus-min:80}")
    private BigDecimal bMinusMin;
    @Value("${grade.letter.c-plus-min:77}")
    private BigDecimal cPlusMin;
    @Value("${grade.letter.c-min:73}")
    private BigDecimal cMin;
    @Value("${grade.letter.c-minus-min:70}")
    private BigDecimal cMinusMin;
    @Value("${grade.letter.d-plus-min:67}")
    private BigDecimal dPlusMin;
    @Value("${grade.letter.d-min:60}")
    private BigDecimal dMin;

    @Transactional(readOnly = true)
    public GradeOverviewDto calculateMyOverview(Long courseId, Long actorId, UserRole actorRole) {
        ensureCourseExists(courseId);

        if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, actorId)) {
            throw new NotEnrolledException("You are not enrolled in this course");
        }
        return calculateOverviewCached(courseId, actorId);
    }

    @Transactional(readOnly = true)
    public GradeOverviewDto calculateStudentOverview(Long courseId, Long studentId, Long actorId, UserRole actorRole) {
        ensureCourseExists(courseId);

        if (actorRole == UserRole.STUDENT) {
            if (!actorId.equals(studentId)) {
                throw new AccessDeniedException("Students can only view their own grade overview");
            }
            if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, actorId)) {
                throw new NotEnrolledException("You are not enrolled in this course");
            }
        } else if (actorRole == UserRole.INSTRUCTOR) {
            if (!courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorId)) {
                throw new AccessDeniedException("Not authorized to view this student's grade overview");
            }
        } else if (actorRole != UserRole.ADMIN && actorRole != UserRole.SYSTEM_ADMIN) {
            throw new AccessDeniedException("Not authorized to view grade overview");
        }

        if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, studentId)) {
            throw new NotEnrolledException("Student is not enrolled in this course");
        }

        return calculateOverviewCached(courseId, studentId);
    }

    @Transactional(readOnly = true)
    public ClassRosterDto calculateRoster(Long courseId, Long actorId, UserRole actorRole, String sortBy, String direction, boolean atRiskOnly) {
        ensureCourseExists(courseId);
        verifyRosterAccess(courseId, actorId, actorRole);

        if (actorRole == UserRole.ADMIN) {
            auditService.log(actorId, "GRADE_OVERVIEW_ROSTER_VIEWED", "Course", courseId,
                    "ADMIN viewed class roster", null);
        }

        ClassRosterDto base = getCachedOrBuildRoster(courseId);
        List<ClassRosterDto.StudentStandingDto> students = new ArrayList<>(base.getStudents());

        if (atRiskOnly) {
            students = students.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getAtRisk()))
                    .toList();
        }

        Comparator<ClassRosterDto.StudentStandingDto> comparator = buildComparator(sortBy);
        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }
        students = students.stream().sorted(comparator).toList();

        return ClassRosterDto.builder()
                .courseCode(base.getCourseCode())
                .classStats(base.getClassStats())
                .students(students)
                .build();
    }

    @Cacheable(value = CacheConfig.CACHE_GRADE_OVERVIEW, key = "#courseId + ':' + #studentId")
    @Transactional(readOnly = true)
    public GradeOverviewDto calculateOverviewCached(Long courseId, Long studentId) {
        try {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

            List<AssignmentGroup> groups = assignmentGroupRepository.findDetailedByCourseId(courseId);
            List<Assignment> allAssignments = groups.stream()
                    .flatMap(group -> group.getAssignments().stream())
                    .toList();
            List<Long> assignmentIds = allAssignments.stream().map(Assignment::getId).toList();

            Map<Long, Team> teamByAssignmentId = teamRepository
                    .findByAssignmentIdsAndMemberUserId(assignmentIds, studentId)
                    .stream()
                    .collect(Collectors.toMap(team -> team.getAssignment().getId(), team -> team, (a, b) -> a));

            Map<Long, Submission> latestIndividualByAssignmentId = submissionRepository
                    .findLatestByAssignmentIdsAndStudentId(assignmentIds, studentId)
                    .stream()
                    .collect(Collectors.toMap(submission -> submission.getAssignment().getId(), submission -> submission));

            Map<Long, Submission> latestTeamByAssignmentId = new HashMap<>();
            for (Team team : new HashSet<>(teamByAssignmentId.values())) {
                List<Submission> latestByTeam = submissionRepository.findLatestByAssignmentIdsAndTeamId(assignmentIds, team.getId());
                latestByTeam.forEach(submission -> latestTeamByAssignmentId.put(submission.getAssignment().getId(), submission));
            }

            Set<Long> submissionIds = new HashSet<>();
            latestIndividualByAssignmentId.values().forEach(submission -> submissionIds.add(submission.getId()));
            latestTeamByAssignmentId.values().forEach(submission -> submissionIds.add(submission.getId()));

            Map<Long, Evaluation> publishedEvaluationBySubmissionId = submissionIds.isEmpty()
                    ? Map.of()
                    : evaluationRepository.findPublishedFinalBySubmissionIds(new ArrayList<>(submissionIds)).stream()
                            .collect(Collectors.toMap(evaluation -> evaluation.getSubmission().getId(), evaluation -> evaluation));

            Map<Long, InstructorScore> instructorScoreByAssignmentId = instructorScoreRepository
                    .findByAssignment_IdInAndStudent_IdAndIsPublishedTrue(assignmentIds, studentId)
                    .stream()
                    .collect(Collectors.toMap(score -> score.getAssignment().getId(), score -> score));

            List<GroupGradeDto> groupDtos = new ArrayList<>();
            for (AssignmentGroup group : groups) {
                groupDtos.add(calculateGroup(group, latestIndividualByAssignmentId, latestTeamByAssignmentId,
                        publishedEvaluationBySubmissionId, instructorScoreByAssignmentId));
            }

            BigDecimal configuredWeight = groups.stream()
                    .map(assignmentGroup -> assignmentGroup.getWeight() == null ? BigDecimal.ZERO : assignmentGroup.getWeight())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<GroupGradeDto> completedGroups = groupDtos.stream()
                    .filter(group -> group.getGroupScorePercent() != null)
                    .toList();

            BigDecimal completedWeight = completedGroups.stream()
                    .map(group -> group.getWeight() == null ? BigDecimal.ZERO : group.getWeight())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal standing = null;
            String standingLetter = null;
            String statusMessage = null;

            if (completedWeight.compareTo(BigDecimal.ZERO) == 0) {
                statusMessage = "No grades have been published for this course yet";
            } else {
                BigDecimal weightedSum = completedGroups.stream()
                        .map(group -> group.getWeight().multiply(group.getGroupScorePercent()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                standing = weightedSum.divide(completedWeight, 2, RoundingMode.HALF_UP);
                standingLetter = toLetter(standing);
            }

            String weightWarning = configuredWeight.compareTo(BigDecimal.valueOf(100)) == 0
                    ? null
                    : "Grade weights total " + configuredWeight.stripTrailingZeros().toPlainString()
                    + "%. Standing reflects completed work only.";

            return GradeOverviewDto.builder()
                    .courseCode(course.getCode())
                    .courseName(course.getName())
                    .currentStanding(standing)
                    .currentStandingLetter(standingLetter)
                    .weightWarning(weightWarning)
                    .statusMessage(statusMessage)
                    .lastUpdated(Instant.now())
                    .groups(groupDtos)
                    .build();
        } catch (NotEnrolledException | AccessDeniedException | ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GradeOverviewUnavailableException("Grade overview is temporarily unavailable");
        }
    }

    private GroupGradeDto calculateGroup(
            AssignmentGroup group,
            Map<Long, Submission> latestIndividualByAssignmentId,
            Map<Long, Submission> latestTeamByAssignmentId,
            Map<Long, Evaluation> publishedEvaluationBySubmissionId,
            Map<Long, InstructorScore> instructorScoreByAssignmentId
    ) {
        List<AssignmentGradeDto> assignmentGrades = new ArrayList<>();

        List<Assignment> sortedAssignments = group.getAssignments().stream()
                .sorted(Comparator.comparing(Assignment::getDueAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        for (Assignment assignment : sortedAssignments) {
            assignmentGrades.add(buildAssignmentGrade(
                    assignment,
                    latestIndividualByAssignmentId.get(assignment.getId()),
                    latestTeamByAssignmentId.get(assignment.getId()),
                    instructorScoreByAssignmentId.get(assignment.getId()),
                    publishedEvaluationBySubmissionId));
        }

        List<AssignmentGradeDto> published = assignmentGrades.stream()
                .filter(row -> row.getScore() != null && row.getMaxScore() != null && row.getMaxScore().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toCollection(ArrayList::new));

        int dropLowestN = group.getDropLowestN() == null ? 0 : group.getDropLowestN();
        if (dropLowestN > 0 && dropLowestN < published.size()) {
            List<AssignmentGradeDto> sortedForDrop = published.stream()
                    .sorted(Comparator.comparing(AssignmentGradeDto::getPercent, Comparator.nullsLast(BigDecimal::compareTo)))
                    .toList();

            Set<String> droppedIds = sortedForDrop.stream()
                    .limit(dropLowestN)
                    .map(AssignmentGradeDto::getId)
                    .collect(Collectors.toSet());

            assignmentGrades = assignmentGrades.stream()
                    .map(row -> {
                        if (droppedIds.contains(row.getId())) {
                            return AssignmentGradeDto.builder()
                                    .id(row.getId())
                                    .title(row.getTitle())
                                    .moduleId(row.getModuleId())
                                    .moduleName(row.getModuleName())
                                    .score(row.getScore())
                                    .maxScore(row.getMaxScore())
                                    .percent(row.getPercent())
                                    .isDropped(true)
                                    .dropReason("Lowest score in " + group.getName() + " group — " + dropLowestN + " dropped")
                                    .isPublished(row.getIsPublished())
                                    .status(row.getStatus())
                                    .isLate(row.getIsLate())
                                    .submittedAt(row.getSubmittedAt())
                                    .evaluatedAt(row.getEvaluatedAt())
                                    .submissionType(row.getSubmissionType())
                                    .build();
                        }
                        return row;
                    })
                    .toList();
        }

        List<AssignmentGradeDto> effective = assignmentGrades.stream()
                .filter(row -> row.getScore() != null && row.getMaxScore() != null && row.getMaxScore().compareTo(BigDecimal.ZERO) > 0)
                .filter(row -> !Boolean.TRUE.equals(row.getIsDropped()))
                .toList();

        BigDecimal groupScorePercent = null;
        String status;

        if (published.isEmpty()) {
            status = "NOT_STARTED";
        } else {
            BigDecimal totalScore = effective.stream().map(AssignmentGradeDto::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalMax = effective.stream().map(AssignmentGradeDto::getMaxScore).reduce(BigDecimal.ZERO, BigDecimal::add);
            groupScorePercent = totalMax.compareTo(BigDecimal.ZERO) == 0
                    ? null
                    : totalScore.multiply(BigDecimal.valueOf(100)).divide(totalMax, 2, RoundingMode.HALF_UP);
            status = published.size() < assignmentGrades.size() ? "IN_PROGRESS" : "COMPLETED";
        }

        return GroupGradeDto.builder()
                .id(hashidService.encode(group.getId()))
                .name(group.getName())
                .weight(group.getWeight())
                .dropLowestN(group.getDropLowestN())
                .groupScorePercent(groupScorePercent)
                .status(status)
                .assignments(assignmentGrades)
                .build();
    }

    private AssignmentGradeDto buildAssignmentGrade(
            Assignment assignment,
            Submission latestIndividualSubmission,
            Submission latestTeamSubmission,
            InstructorScore instructorScore,
            Map<Long, Evaluation> publishedEvaluationBySubmissionId
    ) {
        BigDecimal maxScore = resolveMaxScore(assignment);
        BigDecimal score = null;
        BigDecimal percent = null;
        Boolean isPublished = false;
        String status = "NOT_GRADED";
        Boolean isLate = null;
        Instant submittedAt = null;
        Instant evaluatedAt = null;

        if (assignment.getSubmissionType() == SubmissionType.INSTRUCTOR_GRADED) {
            if (instructorScore != null) {
                score = instructorScore.getScore();
                maxScore = instructorScore.getMaxScore() != null ? instructorScore.getMaxScore() : maxScore;
                isPublished = true;
                evaluatedAt = instructorScore.getPublishedAt();
            }
        } else {
            Submission latestSubmission = assignment.getSubmissionType() == SubmissionType.TEAM
                    ? latestTeamSubmission
                    : latestIndividualSubmission;
            if (latestSubmission != null) {
                submittedAt = latestSubmission.getUploadedAt();
                isLate = latestSubmission.getIsLate();
                Evaluation evaluation = publishedEvaluationBySubmissionId.get(latestSubmission.getId());
                if (evaluation != null) {
                    score = evaluation.getTotalScore();
                    isPublished = true;
                    evaluatedAt = evaluation.getPublishedAt();
                }
            } else {
                status = "NOT_SUBMITTED";
            }
        }

        if (score != null && maxScore != null && maxScore.compareTo(BigDecimal.ZERO) > 0) {
            percent = score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 2, RoundingMode.HALF_UP);
            status = "PUBLISHED";
        }

        return AssignmentGradeDto.builder()
                .id(hashidService.encode(assignment.getId()))
                .title(assignment.getTitle())
                .moduleId(hashidService.encode(assignment.getAssignmentModule() != null ? assignment.getAssignmentModule().getId() : null))
                .moduleName(assignment.getAssignmentModule() != null ? assignment.getAssignmentModule().getName() : null)
                .score(score)
                .maxScore(maxScore)
                .percent(percent)
                .isDropped(false)
                .dropReason(null)
                .isPublished(isPublished)
                .status(status)
                .isLate(isLate)
                .submittedAt(submittedAt)
                .evaluatedAt(evaluatedAt)
                .submissionType(assignment.getSubmissionType())
                .build();
    }

    private BigDecimal resolveMaxScore(Assignment assignment) {
        if (assignment.getSubmissionType() == SubmissionType.INSTRUCTOR_GRADED) {
            return assignment.getMaxScore();
        }

        int rubricMax = assignment.getRubricCriteria() == null
                ? 0
                : assignment.getRubricCriteria().stream().mapToInt(criterion -> criterion.getMaxScore() == null ? 0 : criterion.getMaxScore()).sum();

        if (rubricMax > 0) {
            return BigDecimal.valueOf(rubricMax);
        }
        return assignment.getMaxScore();
    }

    private ClassRosterDto getCachedOrBuildRoster(Long courseId) {
        Cache cache = cacheManager.getCache(CACHE_CLASS_STATISTICS);
        if (cache == null) {
            return buildRoster(courseId);
        }

        ClassRosterDto cached = cache.get(courseId, ClassRosterDto.class);
        if (cached != null) {
            return cached;
        }

        ClassRosterDto built = buildRoster(courseId);
        cache.put(courseId, built);
        return built;
    }

    private ClassRosterDto buildRoster(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

        List<CourseEnrollment> enrollments = courseEnrollmentRepository.findWithUserByCourseId(courseId);
        List<ClassRosterDto.StudentStandingDto> students = new ArrayList<>();
        List<BigDecimal> standings = new ArrayList<>();

        for (CourseEnrollment enrollment : enrollments) {
            Long studentId = enrollment.getUser().getId();
            GradeOverviewDto overview = calculateOverviewCached(courseId, studentId);
            BigDecimal standing = overview.getCurrentStanding();
            if (standing != null) {
                standings.add(standing);
            }
            students.add(ClassRosterDto.StudentStandingDto.builder()
                    .studentId(hashidService.encode(studentId))
                    .name((enrollment.getUser().getFirstName() + " " + enrollment.getUser().getLastName()).trim())
                    .email(enrollment.getUser().getEmail())
                    .currentStanding(standing)
                    .atRisk(standing != null && standing.compareTo(atRiskThreshold) < 0)
                    .build());
        }

        List<BigDecimal> sortedStandings = standings.stream().sorted().toList();
        BigDecimal average = sortedStandings.isEmpty()
                ? null
                : sortedStandings.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(sortedStandings.size()), 2, RoundingMode.HALF_UP);
        BigDecimal highest = sortedStandings.isEmpty() ? null : sortedStandings.get(sortedStandings.size() - 1);
        BigDecimal lowest = sortedStandings.isEmpty() ? null : sortedStandings.get(0);
        BigDecimal median = sortedStandings.isEmpty() ? null : median(sortedStandings);

        int atRiskCount = (int) students.stream().filter(s -> Boolean.TRUE.equals(s.getAtRisk())).count();

        return ClassRosterDto.builder()
                .courseCode(course.getCode())
                .classStats(ClassStatsDto.builder()
                        .enrolledCount(enrollments.size())
                        .withGrades(sortedStandings.size())
                        .average(average)
                        .highest(highest)
                        .lowest(lowest)
                        .median(median)
                        .atRiskCount(atRiskCount)
                        .atRiskThreshold(atRiskThreshold)
                        .build())
                .students(students)
                .build();
    }

    private BigDecimal median(List<BigDecimal> values) {
        int size = values.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return values.get(middle);
        }
        return values.get(middle - 1)
                .add(values.get(middle))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private Comparator<ClassRosterDto.StudentStandingDto> buildComparator(String sortBy) {
        String value = sortBy == null ? "standing" : sortBy.toLowerCase();
        return switch (value) {
            case "name" ->
                Comparator.comparing(ClassRosterDto.StudentStandingDto::getName,
                Comparator.nullsLast(String::compareToIgnoreCase));
            case "email" ->
                Comparator.comparing(ClassRosterDto.StudentStandingDto::getEmail,
                Comparator.nullsLast(String::compareToIgnoreCase));
            default ->
                Comparator.comparing(
                ClassRosterDto.StudentStandingDto::getCurrentStanding,
                Comparator.nullsLast(BigDecimal::compareTo)
                );
        };
    }

    private String toLetter(BigDecimal standing) {
        if (standing == null) {
            return null;
        }
        if (standing.compareTo(aPlusMin) >= 0) {
            return "A+";
        }
        if (standing.compareTo(aMin) >= 0) {
            return "A";
        }
        if (standing.compareTo(aMinusMin) >= 0) {
            return "A-";
        }
        if (standing.compareTo(bPlusMin) >= 0) {
            return "B+";
        }
        if (standing.compareTo(bMin) >= 0) {
            return "B";
        }
        if (standing.compareTo(bMinusMin) >= 0) {
            return "B-";
        }
        if (standing.compareTo(cPlusMin) >= 0) {
            return "C+";
        }
        if (standing.compareTo(cMin) >= 0) {
            return "C";
        }
        if (standing.compareTo(cMinusMin) >= 0) {
            return "C-";
        }
        if (standing.compareTo(dPlusMin) >= 0) {
            return "D+";
        }
        if (standing.compareTo(dMin) >= 0) {
            return "D";
        }
        return "F";
    }

    private void verifyRosterAccess(Long courseId, Long actorId, UserRole role) {
        if (role == UserRole.ADMIN || role == UserRole.SYSTEM_ADMIN) {
            return;
        }
        if (role == UserRole.INSTRUCTOR && courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorId)) {
            return;
        }
        throw new AccessDeniedException("Not authorized to view grade roster for this course");
    }

    private void ensureCourseExists(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", courseId);
        }
    }

    public void evictCourseGradeCaches(Long courseId) {
        Cache gradeOverviewCache = cacheManager.getCache(CacheConfig.CACHE_GRADE_OVERVIEW);
        if (gradeOverviewCache != null) {
            gradeOverviewCache.clear();
        }

        Cache classStatsCache = cacheManager.getCache(CACHE_CLASS_STATISTICS);
        if (classStatsCache != null) {
            classStatsCache.evict(courseId);
        }
    }
}
