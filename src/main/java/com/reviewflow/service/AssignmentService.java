package com.reviewflow.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.exception.GroupNotInCourseException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reviewflow.exception.BusinessRuleException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.exception.SubmissionTypeLockedException;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.model.dto.response.AssignmentResponse;
import com.reviewflow.model.dto.response.GradebookEntryResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.AssignmentGroup;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.Evaluation;
import com.reviewflow.model.entity.RubricCriterion;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.AssignmentGroupRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.CourseRepository;
import com.reviewflow.repository.EvaluationRepository;
import com.reviewflow.repository.RubricCriterionRepository;
import com.reviewflow.repository.RubricScoreRepository;
import com.reviewflow.repository.SubmissionRepository;
import com.reviewflow.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final SubmissionRepository submissionRepository;
    private final EvaluationRepository evaluationRepository;
    private final RubricScoreRepository rubricScoreRepository;
    private final TeamRepository teamRepository;
    private final AssignmentGroupRepository assignmentGroupRepository;
    private final HashidService hashidService;

    @Transactional
    public Assignment createAssignment(Long courseId, String title, String description, Instant dueAt,
            Integer maxTeamSize, SubmissionType submissionType,
            Instant teamLockAt, Boolean isPublished, Long creatorId) {
        return createAssignment(courseId, title, description, dueAt, maxTeamSize, submissionType, teamLockAt, isPublished, creatorId, null);
    }

    @Transactional
    public Assignment createAssignment(Long courseId, String title, String description, Instant dueAt,
            Integer maxTeamSize, SubmissionType submissionType,
            Instant teamLockAt, Boolean isPublished, Long creatorId, Long groupId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        ensureInstructor(courseId, creatorId);

        SubmissionType resolvedSubmissionType = submissionType != null ? submissionType : SubmissionType.INDIVIDUAL;

        // Validate dueAt must be in the future
        if (dueAt != null && dueAt.isBefore(Instant.now())) {
            throw new ValidationException("Due date must be in the future", "INVALID_DUE_DATE");
        }

        if (resolvedSubmissionType == SubmissionType.TEAM) {
            // Validate maxTeamSize between 1 and 10 for team assignments
            if (maxTeamSize == null || maxTeamSize < 1 || maxTeamSize > 10) {
                throw new ValidationException("Max team size must be between 1 and 10", "VALIDATION_ERROR");
            }
        } else {
            // Individual assignments do not use team size.
            maxTeamSize = null;
        }

        // Validate teamLockAt must be before dueAt
        if (teamLockAt != null && dueAt != null && !teamLockAt.isBefore(dueAt)) {
            throw new ValidationException("Team lock date must be before due date", "INVALID_LOCK_DATE");
        }

        AssignmentGroup assignmentGroup = resolveAssignmentGroup(courseId, groupId, true);

        Assignment a = Assignment.builder()
                .course(course)
                .assignmentGroup(assignmentGroup)
                .title(title)
                .description(description)
                .dueAt(dueAt)
                .maxTeamSize(maxTeamSize)
                .submissionType(resolvedSubmissionType)
                .teamLockAt(teamLockAt)
                .isPublished(isPublished != null ? isPublished : false)
                .createdAt(Instant.now())
                .build();
        return assignmentRepository.save(a);
    }

    @Transactional
    public Assignment createAssignment(Long courseId, String title, String description, Instant dueAt,
            Integer maxTeamSize, Instant teamLockAt, Boolean isPublished, Long creatorId) {
        return createAssignment(courseId, title, description, dueAt, maxTeamSize, SubmissionType.INDIVIDUAL,
                teamLockAt, isPublished, creatorId);
    }

    @CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
    @Transactional
    public Assignment updateAssignment(Long assignmentId, String title, String description, Instant dueAt,
            Integer maxTeamSize, SubmissionType submissionType,
            Instant teamLockAt, Long updaterId) {
        return updateAssignment(assignmentId, title, description, dueAt, maxTeamSize, submissionType, teamLockAt, updaterId, null);
    }

    @CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
    @Transactional
    public Assignment updateAssignment(Long assignmentId, String title, String description, Instant dueAt,
            Integer maxTeamSize, SubmissionType submissionType,
            Instant teamLockAt, Long updaterId, Long groupId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), updaterId);

        // Validate dueAt must be in the future if provided
        if (dueAt != null && dueAt.isBefore(Instant.now())) {
            throw new ValidationException("Due date must be in the future", "INVALID_DUE_DATE");
        }

        // Validate maxTeamSize between 1 and 10 if provided
        if (maxTeamSize != null && (maxTeamSize < 1 || maxTeamSize > 10)) {
            throw new ValidationException("Max team size must be between 1 and 10", "VALIDATION_ERROR");
        }

        // Validate teamLockAt must be before dueAt
        Instant finalDueAt = dueAt != null ? dueAt : a.getDueAt();
        if (teamLockAt != null && finalDueAt != null && !teamLockAt.isBefore(finalDueAt)) {
            throw new ValidationException("Team lock date must be before due date", "INVALID_LOCK_DATE");
        }

        if (groupId != null) {
            a.setAssignmentGroup(resolveAssignmentGroup(a.getCourse().getId(), groupId, false));
        }

        if (title != null) {
            a.setTitle(title);
        }
        if (description != null) {
            a.setDescription(description);
        }
        if (dueAt != null) {
            a.setDueAt(dueAt);
        }

        if (submissionType != null && submissionType != a.getSubmissionType()) {
            boolean hasTeams = !teamRepository.findByAssignment_Id(assignmentId).isEmpty();
            boolean hasSubmissions = !submissionRepository.findByAssignment_Id(assignmentId).isEmpty();
            if (hasTeams || hasSubmissions) {
                throw new SubmissionTypeLockedException(
                        "Assignment submission type cannot be changed after teams or submissions exist");
            }
            a.setSubmissionType(submissionType);
            if (submissionType == SubmissionType.INDIVIDUAL) {
                a.setMaxTeamSize(null);
            }
        }

        if (a.getSubmissionType() == SubmissionType.INDIVIDUAL) {
            a.setMaxTeamSize(null);
        } else if (maxTeamSize != null) {
            a.setMaxTeamSize(maxTeamSize);
        }

        if (teamLockAt != null) {
            a.setTeamLockAt(teamLockAt);
        }
        return assignmentRepository.save(a);
    }

    @CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
    @Transactional
    public Assignment updateAssignment(Long assignmentId, String title, String description, Instant dueAt,
            Integer maxTeamSize, Instant teamLockAt, Long updaterId) {
        return updateAssignment(assignmentId, title, description, dueAt, maxTeamSize, null, teamLockAt, updaterId);
    }

    @CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
    @Transactional
    public Assignment publishAssignment(Long assignmentId, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);

        // Toggle publish state
        boolean newState = !Boolean.TRUE.equals(a.getIsPublished());

        // If trying to unpublish, check for submissions
        if (!newState) {
            List<Submission> submissions = submissionRepository.findByAssignment_Id(assignmentId);
            if (!submissions.isEmpty()) {
                throw new BusinessRuleException("Cannot unpublish assignment with existing submissions", "HAS_SUBMISSIONS");
            }
        }

        a.setIsPublished(newState);
        return assignmentRepository.save(a);
    }

    @Cacheable(value = CacheConfig.CACHE_ASSIGNMENT, key = "#id")
    @Transactional(readOnly = true)
    public Assignment getAssignmentById(Long id) {
        return assignmentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
    }

    public List<Assignment> listAssignmentsForCourse(Long courseId, Long userId, UserRole role) {
        // First check if course exists
        courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

        if (role == UserRole.ADMIN || courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
            return assignmentRepository.findByCourse_Id(courseId);
        }
        if (courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
            return assignmentRepository.findByCourse_IdAndIsPublishedTrue(courseId);
        }
        throw new com.reviewflow.exception.AccessDeniedException("You do not have access to this course");
    }

    @CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
    @Transactional
    public RubricCriterion addRubricCriteria(Long assignmentId, String name, String description, int maxScore, int displayOrder, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);

        // Validate maxScore
        if (maxScore <= 0) {
            throw new ValidationException("Max score must be greater than 0", "VALIDATION_ERROR");
        }

        RubricCriterion c = RubricCriterion.builder()
                .assignment(a)
                .name(name)
                .description(description)
                .maxScore(maxScore)
                .displayOrder(displayOrder)
                .build();
        return rubricCriterionRepository.save(c);
    }

    @CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
    @Transactional
    public RubricCriterion updateRubricCriteria(Long assignmentId, Long criterionId, String name, String description, Integer maxScore, Integer displayOrder, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);
        RubricCriterion c = rubricCriterionRepository.findById(criterionId)
                .orElseThrow(() -> new ResourceNotFoundException("RubricCriterion", criterionId));
        if (!c.getAssignment().getId().equals(assignmentId)) {
            throw new ResourceNotFoundException("RubricCriterion", criterionId);
        }

        // Validate maxScore if provided
        if (maxScore != null && maxScore <= 0) {
            throw new ValidationException("Max score must be greater than 0", "VALIDATION_ERROR");
        }

        if (name != null) {
            c.setName(name);
        }
        if (description != null) {
            c.setDescription(description);
        }
        if (maxScore != null) {
            c.setMaxScore(maxScore);
        }
        if (displayOrder != null) {
            c.setDisplayOrder(displayOrder);
        }
        return rubricCriterionRepository.save(c);
    }

    @CacheEvict(value = CacheConfig.CACHE_ASSIGNMENT, key = "#assignmentId")
    @Transactional
    public void deleteRubricCriterion(Long assignmentId, Long criterionId, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);
        RubricCriterion c = rubricCriterionRepository.findById(criterionId)
                .orElseThrow(() -> new ResourceNotFoundException("RubricCriterion", criterionId));
        if (!c.getAssignment().getId().equals(assignmentId)) {
            throw new ResourceNotFoundException("RubricCriterion", criterionId);
        }

        // Check if there are evaluation scores for this criterion
        boolean hasScores = rubricScoreRepository.existsByCriterion_Id(criterionId);
        if (hasScores) {
            throw new BusinessRuleException("Cannot delete criterion with existing evaluation scores", "HAS_SCORES");
        }

        rubricCriterionRepository.delete(c);
    }

    public List<Assignment> listAssignmentsForUser(Long userId, UserRole role) {
        if (role == UserRole.ADMIN || role == UserRole.INSTRUCTOR) {
            return assignmentRepository.findByCourseInstructorId(userId);
        }
        return assignmentRepository.findByCourseEnrollmentUserId(userId);
    }

    public List<AssignmentResponse> listAssignmentsForUserWithDetails(Long userId, UserRole role, String status, Long courseId) {
        List<Assignment> assignments = listAssignmentsForUser(userId, role);

        // Filter by courseId if provided
        if (courseId != null) {
            assignments = assignments.stream()
                    .filter(a -> a.getCourse() != null && courseId.equals(a.getCourse().getId()))
                    .collect(Collectors.toList());
        }

        // Filter by status (UPCOMING, PAST_DUE, ALL)
        Instant now = Instant.now();
        if (status != null) {
            switch (status.toUpperCase()) {
                case "UPCOMING" ->
                    assignments = assignments.stream()
                            .filter(a -> a.getDueAt() != null && a.getDueAt().isAfter(now))
                            .collect(Collectors.toList());
                case "PAST_DUE" ->
                    assignments = assignments.stream()
                            .filter(a -> a.getDueAt() != null && a.getDueAt().isBefore(now))
                            .collect(Collectors.toList());
                default -> {
                    // "ALL" or any unrecognised value - no filtering
                }
            }
        }

        // Sort by dueAt ASC (most urgent first)
        assignments.sort(Comparator.comparing(Assignment::getDueAt, Comparator.nullsLast(Comparator.naturalOrder())));

        // Convert to response with student-specific fields
        boolean isStudent = role == UserRole.STUDENT;
        return assignments.stream()
                .map(a -> toAssignmentResponseWithDetails(a, userId, isStudent))
                .collect(Collectors.toList());
    }

    private AssignmentResponse toAssignmentResponseWithDetails(Assignment a, Long userId, boolean isStudent) {
        List<AssignmentResponse.RubricCriterionResponse> criteria = a.getRubricCriteria() != null
                ? a.getRubricCriteria().stream()
                        .map(c -> AssignmentResponse.RubricCriterionResponse.builder()
                        .id(hashidService.encode(c.getId()))
                        .name(c.getName())
                        .description(c.getDescription())
                        .maxScore(c.getMaxScore())
                        .displayOrder(c.getDisplayOrder())
                        .build())
                        .toList()
                : List.of();

        String teamStatus = null;
        String submissionStatus = null;
        Boolean isLate = null;

        if (isStudent) {
            // Compute team status
            Optional<Team> teamOpt = teamRepository.findByAssignmentIdAndMembersUserId(a.getId(), userId);
            Instant now = Instant.now();
            boolean isLocked = a.getTeamLockAt() != null && now.isAfter(a.getTeamLockAt());

            if (isLocked && teamOpt.isEmpty()) {
                teamStatus = "LOCKED";
            } else if (teamOpt.isPresent()) {
                teamStatus = "HAS_TEAM";
            } else {
                teamStatus = "NO_TEAM";
            }

            // Compute submission status
            if (teamOpt.isPresent()) {
                Team team = teamOpt.get();
                List<Submission> submissions = submissionRepository.findByTeam_IdOrderByVersionNumberDesc(team.getId());
                if (!submissions.isEmpty()) {
                    // Get the latest submission (first in the list since ordered by version desc)
                    Submission latest = submissions.get(0);
                    isLate = latest.getIsLate();
                    submissionStatus = Boolean.TRUE.equals(latest.getIsLate()) ? "LATE" : "SUBMITTED";
                } else {
                    submissionStatus = "NOT_SUBMITTED";
                }
            } else {
                submissionStatus = "NOT_SUBMITTED";
            }
        }

        return AssignmentResponse.builder()
                .id(hashidService.encode(a.getId()))
                .courseId(a.getCourse() != null ? hashidService.encode(a.getCourse().getId()) : null)
                .courseCode(a.getCourse() != null ? a.getCourse().getCode() : null)
                .courseName(a.getCourse() != null ? a.getCourse().getName() : null)
                .title(a.getTitle())
                .description(a.getDescription())
                .dueAt(a.getDueAt())
                .submissionType(a.getSubmissionType())
                .maxTeamSize(a.getMaxTeamSize())
                .isPublished(a.getIsPublished())
                .teamLockAt(a.getTeamLockAt())
                .rubricCriteria(criteria)
                .teamStatus(teamStatus)
                .submissionStatus(submissionStatus)
                .isLate(isLate)
                .build();
    }

    @Transactional
    public void deleteAssignment(Long assignmentId, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);

        // Check if published
        if (Boolean.TRUE.equals(a.getIsPublished())) {
            throw new BusinessRuleException("Unpublish the assignment before deleting", "ASSIGNMENT_PUBLISHED");
        }

        // Check for submissions
        List<Submission> submissions = submissionRepository.findByAssignment_Id(assignmentId);
        if (!submissions.isEmpty()) {
            throw new BusinessRuleException("Cannot delete an assignment with existing submissions", "HAS_SUBMISSIONS");
        }

        assignmentRepository.delete(a);
    }

    public List<Submission> getSubmissionsForAssignment(Long assignmentId, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);
        List<Submission> allSubmissions = submissionRepository.findByAssignment_IdOrderByTeam_IdAscVersionNumberDesc(assignmentId);

        // Return only the latest submission per team
        return allSubmissions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getTeam().getId(),
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(Submission::getVersionNumber)),
                                opt -> opt.orElse(null)
                        )
                ))
                .values().stream()
                .filter(s -> s != null)
                .sorted(Comparator.comparing(s -> s.getTeam().getName()))
                .collect(Collectors.toList());
    }

    public List<GradebookEntryResponse> getGradebookForAssignment(Long assignmentId, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);

        List<Team> teams = teamRepository.findByAssignment_Id(assignmentId);

        return teams.stream()
                .map(team -> {
                    // Get member names
                    List<String> memberNames = team.getMembers().stream()
                            .map(m -> m.getUser().getFirstName() + " " + m.getUser().getLastName())
                            .sorted()
                            .collect(Collectors.toList());

                    // Get latest submission for this team
                    List<Submission> submissions = submissionRepository.findByTeam_IdOrderByVersionNumberDesc(team.getId());
                    Submission latestSubmission = submissions.isEmpty() ? null : submissions.get(0);

                    // Get evaluation status
                    String evaluationStatus = "NOT_STARTED";
                    java.math.BigDecimal totalScore = null;

                    if (latestSubmission != null) {
                        Optional<Evaluation> evalOpt = evaluationRepository.findBySubmission_Id(latestSubmission.getId());
                        if (evalOpt.isPresent()) {
                            Evaluation eval = evalOpt.get();
                            if (eval.getPublishedAt() != null) {
                                evaluationStatus = "PUBLISHED";
                            } else if (Boolean.TRUE.equals(eval.getIsDraft())) {
                                evaluationStatus = "DRAFT";
                            }
                            totalScore = eval.getTotalScore();
                        }
                    }

                    return GradebookEntryResponse.builder()
                            .teamId(hashidService.encode(team.getId()))
                            .teamName(team.getName())
                            .memberNames(memberNames)
                            .latestVersion(latestSubmission != null ? latestSubmission.getVersionNumber() : null)
                            .submittedAt(latestSubmission != null ? latestSubmission.getUploadedAt() : null)
                            .isLate(latestSubmission != null ? latestSubmission.getIsLate() : null)
                            .totalScore(totalScore)
                            .evaluationStatus(evaluationStatus)
                            .build();
                })
                .sorted(Comparator.comparing(GradebookEntryResponse::getTeamName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public Assignment getAssignmentByIdWithAccessControl(Long assignmentId, Long userId, UserRole role) {
        Assignment a = getAssignmentById(assignmentId);

        // ADMIN can access any assignment
        if (role == UserRole.ADMIN) {
            return a;
        }

        // INSTRUCTOR can access if assigned to the course
        if (role == UserRole.INSTRUCTOR) {
            if (!courseInstructorRepository.existsByCourse_IdAndUser_Id(a.getCourse().getId(), userId)) {
                throw new ResourceNotFoundException("Assignment", assignmentId);
            }
            return a;
        }

        // STUDENT can only access published assignments in courses they're enrolled in
        if (role == UserRole.STUDENT) {
            // Check enrollment
            if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(a.getCourse().getId(), userId)) {
                throw new ResourceNotFoundException("Assignment", assignmentId);
            }

            // Check if published - return 404 for unpublished (never reveal it exists)
            if (!Boolean.TRUE.equals(a.getIsPublished())) {
                throw new ResourceNotFoundException("Assignment", assignmentId);
            }

            return a;
        }

        throw new ResourceNotFoundException("Assignment", assignmentId);
    }

    private void ensureInstructor(Long courseId, Long userId) {
        if (!courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Not instructor of this course");
        }
    }

    private AssignmentGroup resolveAssignmentGroup(Long courseId, Long groupId, boolean defaultToUncategorized) {
        if (groupId == null) {
            if (!defaultToUncategorized) {
                return null;
            }
            return assignmentGroupRepository.findByCourse_IdAndIsUncategorizedTrue(courseId)
                    .orElseThrow(() -> new ResourceNotFoundException("AssignmentGroup", courseId));
        }

        AssignmentGroup assignmentGroup = assignmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("AssignmentGroup", groupId));
        if (!assignmentGroup.getCourse().getId().equals(courseId)) {
            throw new GroupNotInCourseException("Assignment group does not belong to this course");
        }
        return assignmentGroup;
    }
}
