package com.reviewflow.service;

import com.reviewflow.event.ExtensionDecidedEvent;
import com.reviewflow.event.ExtensionRequestedEvent;
import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.AlreadyRespondedException;
import com.reviewflow.exception.ExtensionCutoffPassedException;
import com.reviewflow.exception.ExtensionRequestExistsException;
import com.reviewflow.exception.InvalidRequestedDateException;
import com.reviewflow.exception.NotInTeamException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.ExtensionRequest;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.entity.TeamMember;
import com.reviewflow.model.entity.TeamMemberStatus;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.ExtensionRequestStatus;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.ExtensionRequestRepository;
import com.reviewflow.repository.SubmissionRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

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
    public ExtensionRequest create(String assignmentHashId, Long actorUserId, String reason, Instant requestedDueAt) {
        Long assignmentId = hashidService.decodeOrThrow(assignmentHashId);

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorUserId));

        if (actor.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only students can submit extension requests");
        }

        if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), actorUserId)) {
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
            throw new InvalidRequestedDateException("Requested due date must be after assignment due date");
        }

        Team team = null;
        User student = null;

        SubmissionType submissionType = assignment.getSubmissionType() == null
                ? SubmissionType.INDIVIDUAL
                : assignment.getSubmissionType();

        if (submissionType == SubmissionType.INDIVIDUAL) {
            student = actor;
            assertNoExistingRequest(assignmentId, actorUserId, null);
        } else {
            List<TeamMember> memberships = teamMemberRepository.findByAssignment_IdAndUser_IdAndStatus(
                    assignmentId,
                    actorUserId,
                    TeamMemberStatus.ACCEPTED
            );
            if (memberships.isEmpty()) {
                throw new NotInTeamException("You must be in an accepted team to request an extension for this assignment");
            }
            team = memberships.get(0).getTeam();
            assertNoExistingRequest(assignmentId, null, team.getId());
        }

        ExtensionRequest request = ExtensionRequest.builder()
                .assignment(assignment)
                .team(team)
                .student(student)
                .requestedBy(actor)
                .reason(reason)
                .requestedDueAt(requestedDueAt)
                .status(ExtensionRequestStatus.PENDING)
                .build();

        ExtensionRequest saved = extensionRequestRepository.save(request);

        List<Long> instructorUserIds = courseInstructorRepository.findByCourse_Id(assignment.getCourse().getId())
                .stream()
                .map(ci -> ci.getUser() != null ? ci.getUser().getId() : null)
                .filter(Objects::nonNull)
                .toList();

        eventPublisher.publishEvent(new ExtensionRequestedEvent(
                saved.getId(),
                assignmentId,
                assignment.getTitle(),
                fullNameOrEmail(actor),
                requestedDueAt,
                reason,
                instructorUserIds
        ));

        auditService.log(
                actorUserId,
                "EXTENSION_REQUEST_SUBMITTED",
                "ExtensionRequest",
                saved.getId(),
                "{\"assignmentId\":" + assignmentId + ",\"teamId\":" + (team != null ? team.getId() : null)
                + ",\"studentId\":" + (student != null ? student.getId() : null) + "}",
                null
        );

        return saved;
    }

    @Transactional
    public ExtensionRequest respond(Long extensionRequestId, Long actorUserId, Boolean approve, String instructorNote) {
        ExtensionRequest request = extensionRequestRepository.findById(extensionRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("ExtensionRequest", extensionRequestId));

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorUserId));

        boolean isAdmin = actor.getRole() == UserRole.ADMIN;
        boolean isCourseInstructor = courseInstructorRepository.existsByCourse_IdAndUser_Id(
                request.getAssignment().getCourse().getId(),
                actorUserId
        );

        if (!isAdmin && !isCourseInstructor) {
            throw new AccessDeniedException("You do not have permission to respond to this extension request");
        }

        if (request.getStatus() != ExtensionRequestStatus.PENDING) {
            throw new AlreadyRespondedException("Extension request has already been responded to");
        }

        request.setStatus(Boolean.TRUE.equals(approve)
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

        eventPublisher.publishEvent(new ExtensionDecidedEvent(
                saved.getId(),
                saved.getAssignment().getId(),
                saved.getAssignment().getTitle(),
                saved.getStatus() == ExtensionRequestStatus.APPROVED,
                saved.getInstructorNote(),
                saved.getStatus() == ExtensionRequestStatus.APPROVED ? saved.getRequestedDueAt() : null,
                recipientUserIds
        ));

        auditService.log(
                actorUserId,
                saved.getStatus() == ExtensionRequestStatus.APPROVED
                ? "EXTENSION_REQUEST_APPROVED"
                : "EXTENSION_REQUEST_DENIED",
                "ExtensionRequest",
                saved.getId(),
                "{\"assignmentId\":" + saved.getAssignment().getId() + "}",
                null
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ExtensionRequest> getByAssignment(Long assignmentId, Long actorUserId, Pageable pageable) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorUserId));

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));

        boolean canView = actor.getRole() == UserRole.ADMIN
                || courseInstructorRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), actorUserId);

        if (!canView) {
            throw new AccessDeniedException("You do not have permission to view extension requests for this assignment");
        }

        return extensionRequestRepository.findByAssignment_IdOrderByCreatedAtDesc(assignmentId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ExtensionRequest> getMine(Long actorUserId, Pageable pageable) {
        return extensionRequestRepository.findByStudent_IdOrderByCreatedAtDesc(actorUserId, pageable);
    }

    private void assertNoExistingRequest(Long assignmentId, Long studentId, Long teamId) {
        List<ExtensionRequestStatus> allStatuses = List.of(
                ExtensionRequestStatus.PENDING,
                ExtensionRequestStatus.APPROVED,
                ExtensionRequestStatus.DENIED
        );

        boolean exists = studentId != null
                ? extensionRequestRepository
                        .findTopByAssignment_IdAndStudent_IdAndStatusInOrderByCreatedAtDesc(assignmentId, studentId, allStatuses)
                        .isPresent()
                : extensionRequestRepository
                        .findTopByAssignment_IdAndTeam_IdAndStatusInOrderByCreatedAtDesc(assignmentId, teamId, allStatuses)
                        .isPresent();

        if (exists) {
            throw new ExtensionRequestExistsException("An extension request already exists for this assignment");
        }
    }

    private void recalculateIsLate(ExtensionRequest request) {
        List<Submission> submissions;
        if (request.getStudent() != null) {
            submissions = submissionRepository.findByStudent_IdAndAssignment_IdOrderByVersionNumberDesc(
                    request.getStudent().getId(),
                    request.getAssignment().getId()
            );
        } else {
            submissions = submissionRepository.findByTeam_IdAndAssignment_IdOrderByVersionNumberDesc(
                    request.getTeam().getId(),
                    request.getAssignment().getId()
            );
        }

        if (submissions.isEmpty()) {
            return;
        }

        Instant dueAt = request.getRequestedDueAt();
        for (Submission submission : submissions) {
            boolean isLate = submission.getUploadedAt() != null && submission.getUploadedAt().isAfter(dueAt);
            submission.setIsLate(isLate);
        }
        submissionRepository.saveAll(submissions);
    }

    private List<Long> resolveDecisionRecipients(ExtensionRequest request) {
        if (request.getStudent() != null) {
            return List.of(request.getStudent().getId());
        }

        return teamMemberRepository.findByTeam_Id(request.getTeam().getId()).stream()
                .filter(member -> member.getStatus() == TeamMemberStatus.ACCEPTED)
                .map(TeamMember::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .toList();
    }

    private String fullNameOrEmail(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }
}
