package com.reviewflow.service;

import com.reviewflow.event.EvaluationPublishedEvent;
import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.BusinessRuleException;
import com.reviewflow.exception.DuplicateResourceException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.model.dto.request.UpdateScoresRequest;
import com.reviewflow.model.entity.*;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.pdf.PdfGenerationService;
import com.reviewflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final SubmissionRepository submissionRepository;
    private final RubricScoreRepository rubricScoreRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PdfGenerationService pdfGenerationService;
    private final HashidService hashidService;

    @Transactional
    public Evaluation createEvaluation(Long submissionId, Long instructorId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
        
        // Check if evaluation already exists
        evaluationRepository.findBySubmission_Id(submissionId).ifPresent(existing -> {
            throw new DuplicateResourceException("An evaluation already exists for this submission", "EVALUATION_EXISTS");
        });
        
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", instructorId));
        Evaluation evaluation = Evaluation.builder()
                .submission(submission)
                .instructor(instructor)
                .isDraft(true)
                .totalScore(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .lastEditedAt(Instant.now())
                .build();
        return evaluationRepository.save(evaluation);
    }

    public Evaluation getEvaluation(Long id) {
        return evaluationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation", id));
    }
    
    public Evaluation getEvaluationWithAccessControl(Long id, Long userId, UserRole role) {
        Evaluation evaluation = getEvaluation(id);
        
        // ADMIN can access any evaluation
        if (role == UserRole.ADMIN) {
            return evaluation;
        }
        
        // INSTRUCTOR can access evaluations they created
        if (role == UserRole.INSTRUCTOR) {
            if (!evaluation.getInstructor().getId().equals(userId)) {
                throw new AccessDeniedException("Not authorized to access this evaluation");
            }
            return evaluation;
        }
        
        // STUDENT can only access published evaluations for their team
        if (role == UserRole.STUDENT) {
            // If draft, return 404 (don't reveal it exists)
            if (Boolean.TRUE.equals(evaluation.getIsDraft())) {
                throw new ResourceNotFoundException("Evaluation", id);
            }

            Submission submission = evaluation.getSubmission();
            SubmissionType submissionType = submission.getAssignment().getSubmissionType();
            if (submissionType == SubmissionType.INDIVIDUAL) {
                if (submission.getStudent() == null || !submission.getStudent().getId().equals(userId)) {
                    throw new AccessDeniedException("Not authorized to access this evaluation");
                }
            } else {
                // Check if student is a member of the team
                Long teamId = submission.getTeam().getId();
                boolean isMember = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId).isPresent();
                if (!isMember) {
                    throw new AccessDeniedException("Not authorized to access this evaluation");
                }
            }

            return evaluation;
        }
        
        throw new AccessDeniedException("Not authorized to access this evaluation");
    }

    @Transactional
    public Evaluation setScores(Long evalId, List<UpdateScoresRequest.ScoreEntry> scores, Long instructorId) {
        Evaluation evaluation = getEvaluation(evalId);
        validateOwner(evaluation, instructorId);
        
        // Check if evaluation is published
        if (Boolean.FALSE.equals(evaluation.getIsDraft())) {
            throw new BusinessRuleException("Reopen the evaluation before editing scores", "EVALUATION_PUBLISHED");
        }
        
        // Get assignment ID for criterion validation
        Long assignmentId = evaluation.getSubmission().getAssignment().getId();
        
        for (UpdateScoresRequest.ScoreEntry entry : scores) {
            Long criterionId = hashidService.decodeOrThrow(entry.getCriterionId());
            RubricCriterion criterion = rubricCriterionRepository.findById(criterionId)
                    .orElseThrow(() -> new ResourceNotFoundException("RubricCriterion", criterionId));
            
            // Validate criterion belongs to this assignment
            if (!criterion.getAssignment().getId().equals(assignmentId)) {
                throw new ValidationException("Criterion does not belong to this assignment's rubric", "INVALID_CRITERION");
            }
            
            // Validate score range
            if (entry.getScore().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Score cannot be negative", "NEGATIVE_SCORE");
            }
            
            if (entry.getScore().compareTo(BigDecimal.valueOf(criterion.getMaxScore())) > 0) {
                throw new ValidationException(
                    String.format("Score %.2f exceeds maximum of %.0f for criterion '%s'", 
                        entry.getScore(), (double)criterion.getMaxScore(), criterion.getName()),
                    "SCORE_EXCEEDS_MAX");
            }
            
            RubricScore rubricScore = rubricScoreRepository
                    .findByEvaluation_IdAndCriterion_Id(evalId, criterionId)
                    .orElse(RubricScore.builder().evaluation(evaluation).criterion(criterion).build());
            rubricScore.setScore(entry.getScore());
            if (entry.getComment() != null) rubricScore.setComment(entry.getComment());
            rubricScoreRepository.save(rubricScore);
        }
        recalculateTotal(evaluation);
        evaluation.setLastEditedAt(Instant.now());
        return evaluationRepository.save(evaluation);
    }

    @Transactional
    public Evaluation patchScore(Long evalId, Long criterionId, BigDecimal score, String comment, Long instructorId) {
        Evaluation evaluation = getEvaluation(evalId);
        validateOwner(evaluation, instructorId);
        
        // Check if evaluation is published
        if (Boolean.FALSE.equals(evaluation.getIsDraft())) {
            throw new BusinessRuleException("Reopen the evaluation before editing scores", "EVALUATION_PUBLISHED");
        }
        
        RubricCriterion criterion = rubricCriterionRepository.findById(criterionId)
                .orElseThrow(() -> new ResourceNotFoundException("RubricCriterion", criterionId));
        
        // Validate criterion belongs to this assignment
        Long assignmentId = evaluation.getSubmission().getAssignment().getId();
        if (!criterion.getAssignment().getId().equals(assignmentId)) {
            throw new ValidationException("Criterion does not belong to this assignment's rubric", "INVALID_CRITERION");
        }
        
        // Validate score range
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Score cannot be negative", "NEGATIVE_SCORE");
        }
        
        if (score.compareTo(BigDecimal.valueOf(criterion.getMaxScore())) > 0) {
            throw new ValidationException(
                String.format("Score %.2f exceeds maximum of %.0f for criterion '%s'", 
                    score, (double)criterion.getMaxScore(), criterion.getName()),
                "SCORE_EXCEEDS_MAX");
        }
        
        RubricScore rubricScore = rubricScoreRepository
                .findByEvaluation_IdAndCriterion_Id(evalId, criterionId)
                .orElse(RubricScore.builder().evaluation(evaluation).criterion(criterion).build());
        rubricScore.setScore(score);
        if (comment != null) rubricScore.setComment(comment);
        rubricScoreRepository.save(rubricScore);
        recalculateTotal(evaluation);
        evaluation.setLastEditedAt(Instant.now());
        return evaluationRepository.save(evaluation);
    }

    @Transactional
    public Evaluation setComment(Long evalId, String comment, Long instructorId) {
        Evaluation evaluation = getEvaluation(evalId);
        validateOwner(evaluation, instructorId);
        
        // Check if evaluation is published
        if (Boolean.FALSE.equals(evaluation.getIsDraft())) {
            throw new BusinessRuleException("Reopen the evaluation before editing comments", "EVALUATION_PUBLISHED");
        }
        
        // Validate comment length (max 2000 chars)
        if (comment != null && comment.length() > 2000) {
            throw new ValidationException("Comment exceeds maximum length of 2000 characters", "VALIDATION_ERROR");
        }
        
        evaluation.setOverallComment(comment);
        evaluation.setLastEditedAt(Instant.now());
        return evaluationRepository.save(evaluation);
    }

    @Transactional
    public Evaluation publishEvaluation(Long evalId, Long instructorId) {
        Evaluation evaluation = getEvaluation(evalId);
        validateOwner(evaluation, instructorId);
        
        // Check if already published
        if (Boolean.FALSE.equals(evaluation.getIsDraft())) {
            throw new BusinessRuleException("Evaluation is already published", "ALREADY_PUBLISHED");
        }
        
        // Spec says partial evaluations are valid - no requirement to have scores
        evaluation.setIsDraft(false);
        evaluation.setPublishedAt(Instant.now());
        Evaluation saved = evaluationRepository.save(evaluation);
        
        Submission submission = evaluation.getSubmission();
        Assignment assignment = submission.getAssignment();
        SubmissionType submissionType = assignment.getSubmissionType();

        List<Long> memberIds = submissionType == SubmissionType.TEAM
            ? teamMemberRepository.findByTeam_Id(submission.getTeam().getId()).stream()
                .filter(m -> m.getStatus() == TeamMemberStatus.ACCEPTED)
                .map(m -> m.getUser().getId())
                .toList()
            : List.of();
        
        int maxPossibleScore = assignment.getRubricCriteria().stream()
                .mapToInt(RubricCriterion::getMaxScore)
                .sum();
        
        eventPublisher.publishEvent(new EvaluationPublishedEvent(
                memberIds,
            submissionType == SubmissionType.INDIVIDUAL && submission.getStudent() != null
                ? submission.getStudent().getId()
                : null,
                evaluation.getId(),
                assignment.getId(),
                assignment.getTitle(),
                evaluation.getTotalScore() != null ? evaluation.getTotalScore().intValue() : 0,
            maxPossibleScore,
            submissionType
        ));
        
        return saved;
    }

    @Transactional
    public Evaluation reopenEvaluation(Long evalId, Long instructorId) {
        Evaluation evaluation = getEvaluation(evalId);
        validateOwner(evaluation, instructorId);
        
        // Check if already a draft
        if (Boolean.TRUE.equals(evaluation.getIsDraft())) {
            throw new BusinessRuleException("Evaluation is already a draft", "ALREADY_DRAFT");
        }
        
        evaluation.setIsDraft(true);
        evaluation.setPublishedAt(null);
        return evaluationRepository.save(evaluation);
    }

    @Transactional
    public Evaluation generatePdf(Long evalId, Long instructorId) {
        Evaluation evaluation = getEvaluation(evalId);
        validateOwner(evaluation, instructorId);
        
        // Check if evaluation is published
        if (Boolean.TRUE.equals(evaluation.getIsDraft())) {
            throw new BusinessRuleException("Publish the evaluation before generating a PDF", "NOT_PUBLISHED");
        }
        
        List<RubricScore> scores = rubricScoreRepository.findByEvaluation_Id(evalId);
        String pdfPath = pdfGenerationService.generateEvaluationPdf(evaluation, scores);
        evaluation.setPdfPath(pdfPath);
        return evaluationRepository.save(evaluation);
    }

    public org.springframework.core.io.Resource downloadPdf(Long evalId, Long userId, UserRole role) {
        Evaluation evaluation = getEvaluation(evalId);
        
        // Check if PDF has been generated
        if (evaluation.getPdfPath() == null) {
            throw new ResourceNotFoundException("PDF has not been generated yet. Ask your instructor to generate it.");
        }
        
        // Access control
        if (role == UserRole.STUDENT) {
            // If draft, return 404 (don't reveal it exists)
            if (Boolean.TRUE.equals(evaluation.getIsDraft())) {
                throw new ResourceNotFoundException("Evaluation", evalId);
            }

            Submission submission = evaluation.getSubmission();
            SubmissionType submissionType = submission.getAssignment().getSubmissionType();
            if (submissionType == SubmissionType.INDIVIDUAL) {
                if (submission.getStudent() == null || !submission.getStudent().getId().equals(userId)) {
                    throw new AccessDeniedException("Not authorized to access this evaluation");
                }
            } else {
                Long teamId = submission.getTeam().getId();
                boolean isMember = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId).isPresent();
                if (!isMember) {
                    throw new AccessDeniedException("Not authorized to access this evaluation");
                }
            }
        } else if (role == UserRole.INSTRUCTOR) {
            // Instructor can only download their own evaluation PDFs
            if (!evaluation.getInstructor().getId().equals(userId)) {
                throw new AccessDeniedException("Not authorized to access this evaluation");
            }
        }
        // ADMIN can access any PDF
        
        return pdfGenerationService.loadPdf(evaluation.getPdfPath());
    }

    public Page<Evaluation> getMyEvaluations(Long userId, Pageable pageable) {
        return evaluationRepository.findPublishedByTeamMemberUserId(userId, pageable);
    }

    private void recalculateTotal(Evaluation evaluation) {
        List<RubricScore> scores = rubricScoreRepository.findByEvaluation_Id(evaluation.getId());
        BigDecimal total = scores.stream()
                .filter(s -> s.getScore() != null)
                .map(RubricScore::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        evaluation.setTotalScore(total);
    }

    private void validateOwner(Evaluation evaluation, Long instructorId) {
        if (!evaluation.getInstructor().getId().equals(instructorId)) {
            throw new AccessDeniedException("Not the evaluator for this evaluation");
        }
    }
}
