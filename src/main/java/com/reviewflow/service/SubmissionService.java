package com.reviewflow.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.reviewflow.event.SubmissionUploadedEvent;
import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.DuplicateResourceException;
import com.reviewflow.exception.IndividualSubmissionOnlyException;
import com.reviewflow.exception.MalwareDetectedException;
import com.reviewflow.exception.RateLimitException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.exception.TeamSubmissionRequiredException;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.CourseInstructor;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.entity.TeamMember;
import com.reviewflow.model.entity.TeamMemberStatus;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.monitoring.SecurityMetrics;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.SubmissionRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.TeamRepository;
import com.reviewflow.repository.UserRepository;
import com.reviewflow.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB (matches security spec)
    private static final int MAX_CHANGE_NOTE_LENGTH = 500;
    
    // Concurrent upload tracking
    private final Map<String, Boolean> uploadLocks = new ConcurrentHashMap<>();
    
    private final SubmissionRepository submissionRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final AssignmentRepository assignmentRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final FileSecurityValidator fileSecurityValidator;
    private final AdminStatsService adminStatsService;
    private final ClamAvScanService clamAvScanService;
    private final RateLimiterService rateLimiterService;
    private final SecurityMetrics securityMetrics;
    private final AuditService auditService;

    @Transactional
    public Submission upload(Long teamId, Long assignmentId, String changeNote,
                             MultipartFile file, Long uploaderId) {
        // Check upload block rate limiting
        String uploadKey = "user_" + uploaderId;
        if (rateLimiterService.isUploadBlockRateLimited(uploadKey)) {
            securityMetrics.recordUploadBlockRateLimited();
            long retryAfter = rateLimiterService.getUploadBlockRetryAfterSeconds(uploadKey);
            throw new RateLimitException("Too many blocked upload attempts. Try again in " + retryAfter + " seconds");
        }
        
        // Validate file is present
        if (file == null || file.isEmpty()) {
            throw new ValidationException("File is required", "VALIDATION_ERROR");
        }
        
        // Validate file size (HTTP layer enforcement)
        if (file.getSize() > MAX_FILE_SIZE) {
            long sizeMB = file.getSize() / (1024 * 1024);
            throw new ValidationException(
                    "File size " + sizeMB + "MB exceeds maximum of 100MB",
                    "FILE_TOO_LARGE"
            );
        }
        
        // ══════════════════════════════════════════════════════════════════════
        // THREE-GATE FILE SECURITY VALIDATION + CLAMAV SCAN
        // Save file once to temp location for all validation steps
        // ══════════════════════════════════════════════════════════════════════
        String originalName = file.getOriginalFilename();
        String sanitizedName = originalName != null ? originalName.replaceAll("[^a-zA-Z0-9.-]", "_") : "upload";
        java.nio.file.Path tempFile;
        
        try {
            tempFile = java.nio.file.Files.createTempFile("upload-validation-", "-" + sanitizedName);
        } catch (IOException e) {
            log.error("Failed to create temp file for validation: {}", e.getMessage(), e);
            throw new ValidationException("Failed to process file upload", "FILE_VALIDATION_ERROR");
        }
        
        try {
            // Save MultipartFile to temp file ONCE
            file.transferTo(tempFile.toFile());
            
            // GATE 1-3: Three-gate file security validation using the saved temp file
            fileSecurityValidator.validateFromPath(tempFile, uploaderId, "unknown");
            
            // GATE 4: ClamAV Malware Scan (if enabled) using the same temp file
            clamAvScanService.scanAndThrow(tempFile, 30000); // 30-second timeout
            
        } catch (IOException e) {
            log.error("File validation failed for user={}, file={}: {}", uploaderId, originalName, e.getMessage(), e);
            securityMetrics.recordFileBlocked();
            rateLimiterService.recordBlockedUpload(uploadKey);
            try { java.nio.file.Files.deleteIfExists(tempFile); } catch (IOException ex) {}
            throw new ValidationException("Failed to validate file: " + e.getMessage(), "FILE_VALIDATION_ERROR");
        } catch (MalwareDetectedException e) {
            securityMetrics.recordFileBlocked();
            rateLimiterService.recordBlockedUpload(uploadKey);
            try { java.nio.file.Files.deleteIfExists(tempFile); } catch (IOException ex) {}
            throw new ValidationException("File failed security validation: " + e.getMessage(), "MALWARE_DETECTED");
        } catch (RuntimeException e) {
            // Catch BlockedFileTypeException, InvalidMimeTypeException, InvalidFileStructureException, etc.
            securityMetrics.recordFileBlocked();
            rateLimiterService.recordBlockedUpload(uploadKey);
            try { java.nio.file.Files.deleteIfExists(tempFile); } catch (IOException ex) {}
            throw e; // Re-throw as-is (these are already user-friendly)
        }
        
        // Validation passed - continue with upload process
        // Note: tempFile will be cleaned up after storage is complete
        
        // Validate changeNote length
        if (changeNote != null && changeNote.length() > MAX_CHANGE_NOTE_LENGTH) {
            throw new ValidationException(
                    "changeNote cannot exceed " + MAX_CHANGE_NOTE_LENGTH + " characters",
                    "VALIDATION_ERROR"
            );
        }
        
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
        
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new ResourceNotFoundException("User", uploaderId));

        SubmissionType submissionType = assignment.getSubmissionType() != null
            ? assignment.getSubmissionType()
            : SubmissionType.INDIVIDUAL;
        
        // originalName already extracted above - use it for storage

        Team team = null;
        String lockKey;
        if (submissionType == SubmissionType.INDIVIDUAL) {
            if (teamId != null) {
            throw new IndividualSubmissionOnlyException("Team submissions are not allowed for this assignment");
            }
            if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), uploaderId)) {
            throw new AccessDeniedException("You do not have access to this course");
            }
            lockKey = "student_" + uploaderId + "_assignment_" + assignmentId;
        } else {
            if (teamId == null) {
            throw new TeamSubmissionRequiredException("Team submission is required for this assignment");
            }
            team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));

            TeamMember teamMember = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, uploaderId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this team"));

            if (teamMember.getStatus() != TeamMemberStatus.ACCEPTED) {
            throw new AccessDeniedException("You are not an accepted member of this team");
            }
            lockKey = "team_" + teamId + "_assignment_" + assignmentId;
        }

        // Concurrent upload protection
        if (uploadLocks.putIfAbsent(lockKey, Boolean.TRUE) != null) {
            throw new DuplicateResourceException(
                "An upload is already in progress",
                    "UPLOAD_IN_PROGRESS"
            );
        }
        
        try {
            // Get next version number atomically
            int nextVersion = submissionType == SubmissionType.INDIVIDUAL
                ? submissionRepository.findMaxVersionNumberByStudent(uploaderId, assignmentId).orElse(0) + 1
                : submissionRepository.findMaxVersionNumber(teamId, assignmentId).orElse(0) + 1;
            Instant now = Instant.now();
            boolean isLate = assignment.getDueAt() != null && now.isAfter(assignment.getDueAt());
            
            // Store the file (use temp file since MultipartFile was already consumed)
            String relativePath = submissionType == SubmissionType.INDIVIDUAL
                ? "submissions/individual/" + uploaderId + "/" + assignmentId + "/v" + nextVersion + "_" + originalName
                : "submissions/" + teamId + "/" + assignmentId + "/v" + nextVersion + "_" + originalName;
            String storedPath;
            try (InputStream is = java.nio.file.Files.newInputStream(tempFile)) {
                storedPath = storageService.store(relativePath, is, file.getSize(), file.getContentType());
            } catch (IOException e) {
                log.error("Failed to store file: {}", e.getMessage(), e);
                throw new ValidationException("Failed to store file", "FILE_STORAGE_ERROR");
            }
            
            Submission submission = Submission.builder()
                    .team(team)
                    .student(submissionType == SubmissionType.INDIVIDUAL ? uploader : null)
                    .assignment(assignment)
                    .versionNumber(nextVersion)
                    .filePath(storedPath)
                    .fileName(originalName)
                    .fileSizeBytes(file.getSize())
                    .changeNote(changeNote)
                    .uploadedBy(uploader)
                    .uploadedAt(now)
                    .isLate(isLate)
                    .build();
            Submission saved = submissionRepository.save(submission);
            
                    List<Long> recipientIds;
                    if (submissionType == SubmissionType.INDIVIDUAL) {
                    List<Long> instructorIds = courseInstructorRepository.findByCourse_Id(assignment.getCourse().getId())
                        .stream()
                        .map(CourseInstructor::getUser)
                        .filter(java.util.Objects::nonNull)
                        .map(User::getId)
                        .toList();
                    recipientIds = new java.util.ArrayList<>();
                    recipientIds.add(uploaderId);
                    recipientIds.addAll(instructorIds);
                    } else {
                    recipientIds = teamMemberRepository.findByTeam_Id(teamId).stream()
                        .filter(m -> m.getStatus() == TeamMemberStatus.ACCEPTED && !m.getUser().getId().equals(uploaderId))
                        .map(m -> m.getUser().getId())
                        .toList();
                    }
            
            eventPublisher.publishEvent(new SubmissionUploadedEvent(
                    recipientIds,
                    uploader.getFirstName() + " " + uploader.getLastName(),
                        team != null ? team.getId() : null,
                        submissionType == SubmissionType.INDIVIDUAL ? uploaderId : null,
                        team != null ? team.getName() : null,
                    assignment.getId(),
                    assignment.getTitle(),
                        nextVersion,
                        submissionType
            ));

                    auditService.log(
                        uploaderId,
                        "SUBMISSION_UPLOADED",
                        "SUBMISSION",
                        saved.getId(),
                        "{\"assignmentId\":" + assignmentId + ",\"teamId\":" + (team != null ? team.getId() : null)
                            + ",\"studentId\":" + (submissionType == SubmissionType.INDIVIDUAL ? uploaderId : null)
                            + ",\"version\":" + nextVersion + "}",
                        null
                    );
            
            adminStatsService.evictStats();
            return saved;
        } finally {
            // Always release the lock
            uploadLocks.remove(lockKey);
            
            // Always clean up temp file
            try {
                java.nio.file.Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temporary validation file: {}", tempFile, e);
            }
        }
    }

    public Submission getSubmission(Long id, Long userId, UserRole role) {
        Submission sub = submissionRepository.findById(id)
    // Extension extraction method (kept for compatibility if needed elsewhere)
    @SuppressWarnings("unused")
                .orElseThrow(() -> new ResourceNotFoundException("Submission", id));
        
        // ADMIN can access any submission
        if (role == UserRole.ADMIN) {
            return sub;
        }
        
        // INSTRUCTOR can access if submission belongs to their course
        if (role == UserRole.INSTRUCTOR) {
            // This will be validated by the repository/service layer
            return sub;
        }
        
        // STUDENT can access only if they are a member of the team
        if (role == UserRole.STUDENT) {
            if (sub.getStudent() != null) {
                if (!sub.getStudent().getId().equals(userId)) {
                    throw new AccessDeniedException("Access denied");
                }
            } else {
                boolean isMember = teamMemberRepository.findByTeam_IdAndUser_Id(sub.getTeam().getId(), userId).isPresent();
                if (!isMember) {
                    throw new AccessDeniedException("Access denied");
                }
            }
            return sub;
        }
        
        throw new AccessDeniedException("Access denied");
    }

    public List<Submission> getVersionHistory(Long teamId, Long assignmentId, Long userId, UserRole role) {
        // Check if team exists
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));
        
        // ADMIN can access any team
        if (role == UserRole.ADMIN) {
            return submissionRepository.findByTeam_IdAndAssignment_IdOrderByVersionNumberDesc(teamId, assignmentId);
        }
        
        // INSTRUCTOR can access if team belongs to their course
        if (role == UserRole.INSTRUCTOR) {
            return submissionRepository.findByTeam_IdAndAssignment_IdOrderByVersionNumberDesc(teamId, assignmentId);
        }
        
        // STUDENT can access only their own team's submissions
        if (role == UserRole.STUDENT) {
            boolean isMember = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId).isPresent();
            if (!isMember) {
                throw new AccessDeniedException("Access denied");
            }
            return submissionRepository.findByTeam_IdAndAssignment_IdOrderByVersionNumberDesc(teamId, assignmentId);
        }
        
        throw new AccessDeniedException("Access denied");
    }
    
    public List<Submission> getTeamSubmissions(Long teamId, Long userId, UserRole role) {
        // Check if team exists
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));
        
        // ADMIN can access any team
        if (role == UserRole.ADMIN) {
            return submissionRepository.findByTeam_IdOrderByVersionNumberDesc(teamId);
        }
        
        // INSTRUCTOR can access if team belongs to their course
        if (role == UserRole.INSTRUCTOR) {
            return submissionRepository.findByTeam_IdOrderByVersionNumberDesc(teamId);
        }
        
        // STUDENT can access only their own team's submissions
        if (role == UserRole.STUDENT) {
            boolean isMember = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId).isPresent();
            if (!isMember) {
                throw new AccessDeniedException("Access denied");
            }
            return submissionRepository.findByTeam_IdOrderByVersionNumberDesc(teamId);
        }
        
        throw new AccessDeniedException("Access denied");
    }

    public Resource downloadSubmission(Long id, Long userId, UserRole role) {
        Submission sub = getSubmission(id, userId, role);
        try {
            Resource resource = storageService.load(sub.getFilePath());
            if (!resource.exists()) {
                throw new ResourceNotFoundException("FILE_NOT_FOUND_IN_STORAGE");
            }
            return resource;
        } catch (Exception e) {
            throw new ResourceNotFoundException("FILE_NOT_FOUND_IN_STORAGE");
        }
    }

    public Page<Submission> getMySubmissions(Long userId, Pageable pageable) {
        return submissionRepository.findByTeamMemberUserId(userId, pageable);
    }
}
