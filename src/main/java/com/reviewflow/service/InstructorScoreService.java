package com.reviewflow.service;

import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.AlreadyPublishedException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.exception.ScoreExceedsMaxException;
import com.reviewflow.exception.ScoreNotPublishedException;
import com.reviewflow.model.dto.response.InstructorScoreListResponse;
import com.reviewflow.model.dto.response.InstructorScoreResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.InstructorScore;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.InstructorScoreRepository;
import com.reviewflow.repository.TeamRepository;
import com.reviewflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InstructorScoreService {

    private final InstructorScoreRepository instructorScoreRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final AuditService auditService;
    private final HashidService hashidService;
    private final GradeCalculationService gradeCalculationService;

    @Transactional
    public InstructorScoreResponse create(Long assignmentId, Long actorId, Long studentId, Long teamId, BigDecimal score, String comment) {
        Assignment assignment = getAssignment(assignmentId);
        validateInstructorGraded(assignment);
        ensureCanManage(assignment.getCourse().getId(), actorId);
        validateScore(score, assignment.getMaxScore());

        InstructorScore existing;
        User student = null;
        Team team = null;

        if (assignment.getSubmissionType() == SubmissionType.TEAM) {
            if (teamId == null) {
                throw new com.reviewflow.exception.ValidationException("teamId is required for team assignments", "VALIDATION_ERROR");
            }
            team = teamRepository.findById(teamId).orElseThrow(() -> new ResourceNotFoundException("Team", teamId));
            if (!team.getAssignment().getId().equals(assignmentId)) {
                throw new com.reviewflow.exception.ValidationException("Team does not belong to this assignment", "VALIDATION_ERROR");
            }
            existing = instructorScoreRepository.findByAssignment_IdAndTeam_Id(assignmentId, teamId).orElse(null);
        } else {
            if (studentId == null) {
                throw new com.reviewflow.exception.ValidationException("studentId is required", "VALIDATION_ERROR");
            }
            if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), studentId)) {
                throw new AccessDeniedException("Student is not enrolled in this course");
            }
            student = userRepository.findById(studentId).orElseThrow(() -> new ResourceNotFoundException("User", studentId));
            existing = instructorScoreRepository.findByAssignment_IdAndStudent_Id(assignmentId, studentId).orElse(null);
        }

        if (existing != null && Boolean.TRUE.equals(existing.getIsPublished())) {
            throw new AlreadyPublishedException("Use the reopen endpoint to modify a published score");
        }

        User actor = getUser(actorId);
        InstructorScore target = existing != null ? existing : InstructorScore.builder()
                .assignment(assignment)
                .student(student)
                .team(team)
                .enteredBy(actor)
                .enteredAt(Instant.now())
                .isPublished(false)
                .build();

        target.setScore(score);
        target.setMaxScore(assignment.getMaxScore());
        target.setComment(comment);
        target.setUpdatedAt(Instant.now());

        InstructorScore saved = instructorScoreRepository.save(target);
        auditService.log(actorId, "INSTRUCTOR_SCORE_SAVED", "InstructorScore", saved.getId(), "Saved instructor score draft", null);
        gradeCalculationService.evictCourseGradeCaches(assignment.getCourse().getId());
        return toResponse(saved);
    }

    @Transactional
    public InstructorScoreResponse update(Long scoreId, Long actorId, BigDecimal score, String comment) {
        InstructorScore existing = getScore(scoreId);
        ensureCanManage(existing.getAssignment().getCourse().getId(), actorId);
        if (Boolean.TRUE.equals(existing.getIsPublished())) {
            throw new AlreadyPublishedException("Use the reopen endpoint to modify a published score");
        }
        validateScore(score, existing.getMaxScore());

        existing.setScore(score);
        existing.setComment(comment);
        existing.setUpdatedAt(Instant.now());
        InstructorScore saved = instructorScoreRepository.save(existing);
        auditService.log(actorId, "INSTRUCTOR_SCORE_UPDATED", "InstructorScore", saved.getId(), "Updated instructor score draft", null);
        gradeCalculationService.evictCourseGradeCaches(existing.getAssignment().getCourse().getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InstructorScoreListResponse listByAssignment(Long assignmentId, Long actorId, UserRole actorRole, int page, int size) {
        Assignment assignment = getAssignment(assignmentId);
        if (actorRole == UserRole.ADMIN || actorRole == UserRole.SYSTEM_ADMIN) {
            // allowed for list/view
        } else if (!courseInstructorRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), actorId)) {
            throw new AccessDeniedException("Not authorized to view instructor scores for this course");
        }

        Page<InstructorScore> scorePage = instructorScoreRepository.findByAssignment_Id(assignmentId, PageRequest.of(page, size));
        List<InstructorScoreListResponse.InstructorScoreItem> items = scorePage.getContent().stream()
                .map(score -> InstructorScoreListResponse.InstructorScoreItem.builder()
                .id(hashidService.encode(score.getId()))
                .studentId(hashidService.encode(score.getStudent() != null ? score.getStudent().getId() : null))
                .studentName(score.getStudent() != null ? fullName(score.getStudent()) : null)
                .teamId(hashidService.encode(score.getTeam() != null ? score.getTeam().getId() : null))
                .teamName(score.getTeam() != null ? score.getTeam().getName() : null)
                .score(score.getScore())
                .maxScore(score.getMaxScore())
                .isPublished(score.getIsPublished())
                .build())
                .toList();

        long total = instructorScoreRepository.countByAssignment_Id(assignmentId);
        long published = instructorScoreRepository.countByAssignment_IdAndIsPublishedTrue(assignmentId);
        long draft = instructorScoreRepository.countByAssignment_IdAndIsPublishedFalse(assignmentId);
        long notEntered = Math.max(0, expectedTargetCount(assignment) - total);

        return InstructorScoreListResponse.builder()
                .scores(items)
                .summary(InstructorScoreListResponse.Summary.builder()
                        .total(total)
                        .published(published)
                        .draft(draft)
                        .notEntered(notEntered)
                        .build())
                .build();
    }

    @Transactional
    public InstructorScoreResponse publish(Long scoreId, Long actorId) {
        InstructorScore score = getScore(scoreId);
        ensureCanManage(score.getAssignment().getCourse().getId(), actorId);
        if (Boolean.TRUE.equals(score.getIsPublished())) {
            throw new AlreadyPublishedException("Score is already published");
        }
        score.setIsPublished(true);
        score.setPublishedAt(Instant.now());
        score.setUpdatedAt(Instant.now());
        InstructorScore saved = instructorScoreRepository.save(score);
        auditService.log(actorId, "INSTRUCTOR_SCORE_PUBLISHED", "InstructorScore", saved.getId(), "Published instructor score", null);
        gradeCalculationService.evictCourseGradeCaches(score.getAssignment().getCourse().getId());
        return toResponse(saved);
    }

    @Transactional
    public int publishAll(Long assignmentId, Long actorId) {
        Assignment assignment = getAssignment(assignmentId);
        ensureCanManage(assignment.getCourse().getId(), actorId);
        List<InstructorScore> drafts = instructorScoreRepository.findByAssignment_IdAndIsPublishedFalse(assignmentId);
        drafts.forEach(score -> {
            score.setIsPublished(true);
            score.setPublishedAt(Instant.now());
            score.setUpdatedAt(Instant.now());
        });
        instructorScoreRepository.saveAll(drafts);
        auditService.log(actorId, "INSTRUCTOR_SCORES_BULK_PUBLISHED", "Assignment", assignmentId, "Published count=" + drafts.size(), null);
        gradeCalculationService.evictCourseGradeCaches(assignment.getCourse().getId());
        return drafts.size();
    }

    @Transactional
    public InstructorScoreResponse reopen(Long scoreId, Long actorId, String reason) {
        InstructorScore score = getScore(scoreId);
        ensureCanManage(score.getAssignment().getCourse().getId(), actorId);
        if (!Boolean.TRUE.equals(score.getIsPublished())) {
            throw new ScoreNotPublishedException("Score is not published");
        }
        score.setIsPublished(false);
        score.setPublishedAt(null);
        score.setUpdatedAt(Instant.now());
        InstructorScore saved = instructorScoreRepository.save(score);
        auditService.log(actorId, "INSTRUCTOR_SCORE_REOPENED", "InstructorScore", saved.getId(), reason, null);
        gradeCalculationService.evictCourseGradeCaches(score.getAssignment().getCourse().getId());
        return toResponse(saved);
    }

    private Assignment getAssignment(Long assignmentId) {
        return assignmentRepository.findWithDetailsById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
    }

    private InstructorScore getScore(Long scoreId) {
        return instructorScoreRepository.findById(scoreId)
                .orElseThrow(() -> new ResourceNotFoundException("InstructorScore", scoreId));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private void validateInstructorGraded(Assignment assignment) {
        if (assignment.getSubmissionType() != SubmissionType.INSTRUCTOR_GRADED) {
            throw new com.reviewflow.exception.ValidationException("Assignment is not instructor graded", "VALIDATION_ERROR");
        }
        if (assignment.getMaxScore() == null) {
            throw new com.reviewflow.exception.ValidationException("Instructor graded assignment must define maxScore", "VALIDATION_ERROR");
        }
    }

    private void validateScore(BigDecimal score, BigDecimal maxScore) {
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0) {
            throw new com.reviewflow.exception.ValidationException("Score must be greater than or equal to 0", "VALIDATION_ERROR");
        }
        if (maxScore != null && score.compareTo(maxScore) > 0) {
            throw new ScoreExceedsMaxException("Score exceeds assignment maximum");
        }
    }

    private void ensureCanManage(Long courseId, Long actorId) {
        User actor = getUser(actorId);
        if (actor.getRole() == UserRole.SYSTEM_ADMIN) {
            return;
        }
        if (actor.getRole() == UserRole.ADMIN) {
            throw new AccessDeniedException("ADMIN cannot modify instructor scores");
        }
        if (!courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorId)) {
            throw new AccessDeniedException("Not authorized to manage instructor scores for this course");
        }
    }

    private long expectedTargetCount(Assignment assignment) {
        if (assignment.getSubmissionType() == SubmissionType.TEAM) {
            return teamRepository.findByAssignment_Id(assignment.getId()).size();
        }
        return courseEnrollmentRepository.countByCourse_Id(assignment.getCourse().getId());
    }

    private InstructorScoreResponse toResponse(InstructorScore score) {
        BigDecimal percent = score.getMaxScore() == null || score.getMaxScore().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : score.getScore().multiply(BigDecimal.valueOf(100))
                        .divide(score.getMaxScore(), 2, RoundingMode.HALF_UP);

        return InstructorScoreResponse.builder()
                .id(hashidService.encode(score.getId()))
                .assignmentId(hashidService.encode(score.getAssignment().getId()))
                .studentId(hashidService.encode(score.getStudent() != null ? score.getStudent().getId() : null))
                .teamId(hashidService.encode(score.getTeam() != null ? score.getTeam().getId() : null))
                .score(score.getScore())
                .maxScore(score.getMaxScore())
                .percent(percent)
                .comment(score.getComment())
                .isPublished(score.getIsPublished())
                .enteredAt(score.getEnteredAt())
                .publishedAt(score.getPublishedAt())
                .build();
    }

    private String fullName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        String value = (first + " " + last).trim();
        return value.isBlank() ? user.getEmail() : value;
    }

}
