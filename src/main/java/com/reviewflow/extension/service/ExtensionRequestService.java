package com.reviewflow.extension.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reviewflow.extension.event.ExtensionDecidedEvent;
import com.reviewflow.extension.event.ExtensionRequestedEvent;
import com.reviewflow.shared.exception.AccessDeniedException;
import com.reviewflow.extension.exception.AlreadyRespondedException;
import com.reviewflow.extension.exception.ExtensionCutoffPassedException;
import com.reviewflow.extension.exception.ExtensionRequestExistsException;
import com.reviewflow.extension.exception.InvalidRequestedDateException;
import com.reviewflow.team.exception.NotInTeamException;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.ExtensionRequest;
import com.reviewflow.shared.domain.Submission;
import com.reviewflow.shared.domain.Team;
import com.reviewflow.shared.domain.TeamMember;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.extension.repository.ExtensionRequestRepository;
import com.reviewflow.submission.repository.SubmissionRepository;
import com.reviewflow.team.repository.TeamMemberRepository;
import com.reviewflow.admin.service.AuditService;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.shared.domain.ExtensionRequestStatus;
import com.reviewflow.shared.domain.TeamMemberStatus;
import com.reviewflow.shared.util.HashidService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExtensionRequestService {

  private final ExtensionRequestRepository extensionRequestRepository;
  private final AssignmentRepository assignmentRepository;
  private final UserRepository userRepository;
  private final TeamMemberRepository teamMemberRepository;
  private final CourseEnrollmentRepository courseEnrollmentRepository;
  private final CourseInstructorRepository courseInstructorRepository;
  private final SubmissionRepository submissionRepository;
  private final HashidService hashidService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  @Value("${extension.cutoff.hours-default:48}")
  private int extensionCutoffHoursDefault;

  @Transactional
  public ExtensionRequest create(
      String assignmentHashId, Long actorUserId, String reason, Instant requestedDueAt) {
    Long assignmentId = hashidService.decodeOrThrow(assignmentHashId);

    Assignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));

    User actor =
        userRepository
            .findById(actorUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", actorUserId));

    if (actor.getRole() != UserRole.STUDENT) {
      throw new AccessDeniedException("Only students can submit extension requests");
    }

    if (!courseEnrollmentRepository.existsByCourseIdAndUserId(
        assignment.getCourse().getId(), actorUserId)) {
      throw new AccessDeniedException("You are not enrolled in this course");
    }

    if (assignment.getDueAt() == null) {
      throw new InvalidRequestedDateException("Assignment does not have a due date configured");
    }

    Instant cutoff = assignment.getDueAt().minusSeconds(extensionCutoffHoursDefault * 3600L);
    if (!Instant.now().isBefore(cutoff)) {
      throw new ExtensionCutoffPassedException("Extension request cutoff window has passed");
    }

    if (!requestedDueAt.isAfter(assignment.getDueAt())) {
      throw new InvalidRequestedDateException(
          "Requested due date must be after assignment due date");
    }

    Team team = null;
    User student = null;

    SubmissionType submissionType =
        assignment.getSubmissionType() == null
            ? SubmissionType.INDIVIDUAL
            : assignment.getSubmissionType();

    if (submissionType == SubmissionType.INDIVIDUAL) {
      student = actor;
      assertNoExistingRequest(assignmentId, actorUserId, null);
    } else {
      List<TeamMember> memberships =
          teamMemberRepository.findByAssignmentIdAndUserIdAndStatus(
              assignmentId, actorUserId, TeamMemberStatus.ACCEPTED);
      if (memberships.isEmpty()) {
        throw new NotInTeamException(
            "You must be in an accepted team to request an extension for this assignment");
      }
      team = memberships.get(0).getTeam();
      assertNoExistingRequest(assignmentId, null, team.getId());
    }

    ExtensionRequest request =
        ExtensionRequest.builder()
            .assignment(assignment)
            .team(team)
            .student(student)
            .requestedBy(actor)
            .reason(reason)
            .requestedDueAt(requestedDueAt)
            .status(ExtensionRequestStatus.PENDING)
            .build();

    ExtensionRequest saved = extensionRequestRepository.save(request);

    List<Long> instructorUserIds =
        courseInstructorRepository.findByCourseId(assignment.getCourse().getId()).stream()
            .map(ci -> ci.getUser() != null ? ci.getUser().getId() : null)
            .filter(Objects::nonNull)
            .toList();

    eventPublisher.publishEvent(
        new ExtensionRequestedEvent(
            saved.getId(),
            assignmentId,
            assignment.getTitle(),
            actor.getFullNameOrEmail(),
            requestedDueAt,
            reason,
            instructorUserIds));

    auditService.log(
        actorUserId,
        "EXTENSION_REQUEST_SUBMITTED",
        "ExtensionRequest",
        saved.getId(),
        "{\"assignmentId\":"
            + assignmentId
            + ",\"teamId\":"
            + (team != null ? team.getId() : null)
            + ",\"studentId\":"
            + (student != null ? student.getId() : null)
            + "}",
        null);

    return saved;
  }

  @Transactional
  public ExtensionRequest respond(
      Long extensionRequestId, Long actorUserId, Boolean approve, String instructorNote) {
    ExtensionRequest request =
        extensionRequestRepository
            .findById(extensionRequestId)
            .orElseThrow(
                () -> new ResourceNotFoundException("ExtensionRequest", extensionRequestId));

    User actor =
        userRepository
            .findById(actorUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", actorUserId));

    boolean isAdmin = actor.getRole() == UserRole.ADMIN;
    boolean isCourseInstructor =
        courseInstructorRepository.existsByCourseIdAndUserId(
            request.getAssignment().getCourse().getId(), actorUserId);

    if (!isAdmin && !isCourseInstructor) {
      throw new AccessDeniedException(
          "You do not have permission to respond to this extension request");
    }

    if (request.getStatus() != ExtensionRequestStatus.PENDING) {
      throw new AlreadyRespondedException("Extension request has already been responded to");
    }

    request.setStatus(
        Boolean.TRUE.equals(approve)
            ? ExtensionRequestStatus.APPROVED
            : ExtensionRequestStatus.DENIED);
    request.setInstructorNote(instructorNote);
    request.setRespondedBy(actor);
    request.setRespondedAt(Instant.now());

    ExtensionRequest saved = extensionRequestRepository.save(request);

    if (saved.getStatus() == ExtensionRequestStatus.APPROVED) {
      recalculateIsLate(saved);
    }

    List<Long> recipientUserIds = resolveDecisionRecipients(saved);

    eventPublisher.publishEvent(
        new ExtensionDecidedEvent(
            saved.getId(),
            saved.getAssignment().getId(),
            saved.getAssignment().getTitle(),
            saved.getStatus() == ExtensionRequestStatus.APPROVED,
            saved.getInstructorNote(),
            saved.getStatus() == ExtensionRequestStatus.APPROVED ? saved.getRequestedDueAt() : null,
            recipientUserIds));

    auditService.log(
        actorUserId,
        saved.getStatus() == ExtensionRequestStatus.APPROVED
            ? "EXTENSION_REQUEST_APPROVED"
            : "EXTENSION_REQUEST_DENIED",
        "ExtensionRequest",
        saved.getId(),
        "{\"assignmentId\":" + saved.getAssignment().getId() + "}",
        null);

    return saved;
  }

  @Transactional(readOnly = true)
  public Page<ExtensionRequest> getByAssignment(
      Long assignmentId, Long actorUserId, Pageable pageable) {
    User actor =
        userRepository
            .findById(actorUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", actorUserId));

    Assignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));

    boolean canView =
        actor.getRole() == UserRole.ADMIN
            || courseInstructorRepository.existsByCourseIdAndUserId(
                assignment.getCourse().getId(), actorUserId);

    if (!canView) {
      throw new AccessDeniedException(
          "You do not have permission to view extension requests for this assignment");
    }

    return extensionRequestRepository.findByAssignmentIdOrderByCreatedAtDesc(
        assignmentId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<ExtensionRequest> getMine(Long actorUserId, Pageable pageable) {
    return extensionRequestRepository.findByStudentIdOrderByCreatedAtDesc(actorUserId, pageable);
  }

  private void assertNoExistingRequest(Long assignmentId, Long studentId, Long teamId) {
    List<ExtensionRequestStatus> allStatuses =
        List.of(
            ExtensionRequestStatus.PENDING,
            ExtensionRequestStatus.APPROVED,
            ExtensionRequestStatus.DENIED);

    boolean exists =
        studentId != null
            ? extensionRequestRepository
                .findTopByAssignmentIdAndStudentIdAndStatusInOrderByCreatedAtDesc(
                    assignmentId, studentId, allStatuses)
                .isPresent()
            : extensionRequestRepository
                .findTopByAssignmentIdAndTeamIdAndStatusInOrderByCreatedAtDesc(
                    assignmentId, teamId, allStatuses)
                .isPresent();

    if (exists) {
      throw new ExtensionRequestExistsException(
          "An extension request already exists for this assignment");
    }
  }

  private void recalculateIsLate(ExtensionRequest request) {
    List<Submission> submissions;
    if (request.getStudent() != null) {
      submissions =
          submissionRepository.findByStudentIdAndAssignmentIdOrderByVersionNumberDesc(
              request.getStudent().getId(), request.getAssignment().getId());
    } else {
      submissions =
          submissionRepository.findByTeamIdAndAssignmentIdOrderByVersionNumberDesc(
              request.getTeam().getId(), request.getAssignment().getId());
    }

    if (submissions.isEmpty()) {
      return;
    }

    Instant dueAt = request.getRequestedDueAt();
    for (Submission submission : submissions) {
      boolean isLate =
          submission.getUploadedAt() != null && submission.getUploadedAt().isAfter(dueAt);
      submission.setIsLate(isLate);
    }
    submissionRepository.saveAll(submissions);
  }

  private List<Long> resolveDecisionRecipients(ExtensionRequest request) {
    if (request.getStudent() != null) {
      return List.of(request.getStudent().getId());
    }

    return teamMemberRepository.findByTeamId(request.getTeam().getId()).stream()
        .filter(member -> member.getStatus() == TeamMemberStatus.ACCEPTED)
        .map(TeamMember::getUser)
        .filter(Objects::nonNull)
        .map(User::getId)
        .toList();
  }
}
