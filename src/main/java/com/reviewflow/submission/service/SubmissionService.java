package com.reviewflow.submission.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.shared.exception.ServiceUnavailableException;
import org.slf4j.MDC;
import com.reviewflow.shared.exception.FileUploadTimeoutException;
import com.reviewflow.shared.exception.StorageException;
import com.reviewflow.infrastructure.storage.S3Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.reviewflow.infrastructure.storage.S3Service;

import com.reviewflow.submission.event.SubmissionUploadedEvent;
import com.reviewflow.shared.exception.AccessDeniedException;
import com.reviewflow.shared.exception.DuplicateResourceException;
import com.reviewflow.submission.exception.FileTooLargeForPreviewException;
import com.reviewflow.submission.exception.IndividualSubmissionOnlyException;
import com.reviewflow.submission.exception.MalwareDetectedException;
import com.reviewflow.submission.exception.PreviewNotSupportedException;
import com.reviewflow.infrastructure.ratelimit.RateLimitResult;
import com.reviewflow.infrastructure.ratelimit.RateLimitService;
import com.reviewflow.infrastructure.ratelimit.RateLimitStrategy;
import com.reviewflow.shared.exception.TooManyRequestsException;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.grading.exception.SubmissionNotRequiredException;
import com.reviewflow.team.exception.TeamSubmissionRequiredException;
import com.reviewflow.shared.exception.ValidationException;
import com.reviewflow.shared.dto.PreviewResponseDto;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.CourseInstructor;
import com.reviewflow.shared.domain.ExtensionRequest;
import com.reviewflow.shared.domain.Submission;
import com.reviewflow.shared.domain.Team;
import com.reviewflow.shared.domain.TeamMember;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.infrastructure.monitoring.SecurityMetrics;
import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.extension.repository.ExtensionRequestRepository;
import com.reviewflow.submission.repository.SubmissionRepository;
import com.reviewflow.team.repository.TeamMemberRepository;
import com.reviewflow.team.repository.TeamRepository;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.admin.service.AdminStatsService;
import com.reviewflow.admin.service.AuditService;
import com.reviewflow.infrastructure.storage.ClamAvScanService;
import com.reviewflow.infrastructure.storage.FileSecurityValidator;
import com.reviewflow.shared.domain.ExtensionRequestStatus;
import com.reviewflow.shared.domain.TeamMemberStatus;
import com.reviewflow.infrastructure.storage.StorageService;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.shared.util.MimeTypeResolver;
import com.reviewflow.infrastructure.storage.S3KeyBuilder;
import com.reviewflow.infrastructure.storage.S3Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.reviewflow.infrastructure.storage.S3Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

  private static final int MAX_CHANGE_NOTE_LENGTH = 500;
  private static final long MAX_PREVIEW_FILE_SIZE_BYTES = 52_428_800; // 50 MB

  // Concurrent upload tracking
  private final Map<String, Boolean> uploadLocks = new ConcurrentHashMap<>();

  private final SubmissionRepository submissionRepository;
  private final TeamRepository teamRepository;
  private final TeamMemberRepository teamMemberRepository;
  private final CourseEnrollmentRepository courseEnrollmentRepository;
  private final CourseInstructorRepository courseInstructorRepository;
  private final AssignmentRepository assignmentRepository;
  private final ExtensionRequestRepository extensionRequestRepository;
  private final StorageService storageService;
  private final ApplicationEventPublisher eventPublisher;
  private final UserRepository userRepository;
  private final FileSecurityValidator fileSecurityValidator;
  private final AdminStatsService adminStatsService;
  private final ClamAvScanService clamAvScanService;
  private final SecurityMetrics securityMetrics;
  private final AuditService auditService;
  private final HashidService hashidService;
  private final S3Service s3Service;
  private final ReviewFlowMetrics reviewFlowMetrics;
  private final RateLimitService rateLimitService;

  @Value("${file.validation.submission.max-size-bytes:104857600}")
  private long submissionMaxFileSizeBytes;

  @Value("${clamav.timeout-ms:5000}")
  private long clamAvTimeoutMs;

  @Value("${async.upload.timeout-seconds:60}")
  private int uploadTimeoutSeconds;

  @jakarta.annotation.Resource(name = "uploadExecutor")
  private Executor uploadExecutor;

  @Transactional
  public Submission upload(
      Long teamId, Long assignmentId, String changeNote, MultipartFile file, Long uploaderId) {
    User uploader =
        userRepository
            .findById(uploaderId)
            .orElseThrow(() -> new ResourceNotFoundException("User", uploaderId));
    RateLimitResult uploadLimit =
        rateLimitService.tryConsume(
            String.valueOf(uploaderId), RateLimitStrategy.UPLOAD_BLOCK, uploader.getRole());
    if (!uploadLimit.allowed()) {
      reviewFlowMetrics.recordUploadBlockRateLimited();
      throw new TooManyRequestsException(
          "Upload limit reached. Please try again in "
              + uploadLimit.retryAfterSeconds()
              + " seconds.",
          uploadLimit.retryAfterSeconds());
    }

    // Validate file is present
    if (file == null || file.isEmpty()) {
      throw new ValidationException("File is required", "VALIDATION_ERROR");
    }

    // Validate file size (HTTP layer enforcement)
    if (file.getSize() > submissionMaxFileSizeBytes) {
      long sizeMB = file.getSize() / (1024 * 1024);
      long maxMB = submissionMaxFileSizeBytes / (1024 * 1024);
      throw new ValidationException(
          "File size " + sizeMB + "MB exceeds maximum of " + maxMB + "MB", "FILE_TOO_LARGE");
    }

    // =====================================================================
    // THREE-GATE FILE SECURITY VALIDATION + CLAMAV SCAN
    // Save file once to temp location for all validation steps
    // =====================================================================
    String originalName = file.getOriginalFilename();
    String sanitizedName =
        originalName != null ? originalName.replaceAll("[^a-zA-Z0-9.-]", "_") : "upload";
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
      clamAvScanService.scanAndThrow(tempFile, clamAvTimeoutMs);

    } catch (IOException e) {
      log.error(
          "File validation failed for user={}, file={}: {}",
          uploaderId,
          originalName,
          e.getMessage(),
          e);
      securityMetrics.recordFileBlocked();
      try {
        java.nio.file.Files.deleteIfExists(tempFile);
      } catch (IOException ex) {
        log.warn("Failed to delete temp file after IO validation failure: {}", tempFile, ex);
      }
      throw new ValidationException(
          "Failed to validate file: " + e.getMessage(), "FILE_VALIDATION_ERROR");
    } catch (MalwareDetectedException e) {
      securityMetrics.recordFileBlocked();
      try {
        java.nio.file.Files.deleteIfExists(tempFile);
      } catch (IOException ex) {
        log.warn("Failed to delete temp file after malware detection: {}", tempFile, ex);
      }
      throw e;
    } catch (com.reviewflow.infrastructure.storage.MalwareScanUnavailableException e) {
      try {
        java.nio.file.Files.deleteIfExists(tempFile);
      } catch (IOException ex) {
        log.warn("Failed to delete temp file after scanner unavailable: {}", tempFile, ex);
      }
      throw e;
    } catch (RuntimeException e) {
      // Catch BlockedFileTypeException, InvalidMimeTypeException, InvalidFileStructureException,
      // etc.
      securityMetrics.recordFileBlocked();
      try {
        java.nio.file.Files.deleteIfExists(tempFile);
      } catch (IOException ex) {
        log.warn("Failed to delete temp file after security validation failure: {}", tempFile, ex);
      }
      throw e; // Re-throw as-is (these are already user-friendly)
    }

    // Validation passed - continue with upload process
    // Note: tempFile will be cleaned up after storage is complete
    // Validate changeNote length
    if (changeNote != null && changeNote.length() > MAX_CHANGE_NOTE_LENGTH) {
      throw new ValidationException(
          "changeNote cannot exceed " + MAX_CHANGE_NOTE_LENGTH + " characters", "VALIDATION_ERROR");
    }

    Assignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));

    SubmissionType submissionType =
        assignment.getSubmissionType() != null
            ? assignment.getSubmissionType()
            : SubmissionType.INDIVIDUAL;

    if (submissionType == SubmissionType.INSTRUCTOR_GRADED) {
      throw new SubmissionNotRequiredException(
          "This assessment is graded directly by your instructor. No file submission is required.");
    }

    // originalName already extracted above - use it for storage
    Team team = null;
    String lockKey;
    if (submissionType == SubmissionType.INDIVIDUAL) {
      if (teamId != null) {
        throw new IndividualSubmissionOnlyException(
            "Team submissions are not allowed for this assignment");
      }
      if (!courseEnrollmentRepository.existsByCourseIdAndUserId(
          assignment.getCourse().getId(), uploaderId)) {
        auditService.logSecurityEvent(
            uploaderId,
            "SUBMISSION_IDOR_ATTEMPT",
            "Course",
            assignment.getCourse().getId(),
            "Upload rejected: not enrolled in course",
            null);
        throw new AccessDeniedException("You do not have access to this course");
      }
      lockKey = "student_" + uploaderId + "_assignment_" + assignmentId;
    } else {
      if (teamId == null) {
        throw new TeamSubmissionRequiredException(
            "Team submission is required for this assignment");
      }
      team =
          teamRepository
              .findById(teamId)
              .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));

      TeamMember teamMember =
          teamMemberRepository
              .findByTeamIdAndUserId(teamId, uploaderId)
              .orElseThrow(() -> new AccessDeniedException("You are not a member of this team"));

      if (teamMember.getStatus() != TeamMemberStatus.ACCEPTED) {
        throw new AccessDeniedException("You are not an accepted member of this team");
      }
      lockKey = "team_" + teamId + "_assignment_" + assignmentId;
    }

    // Concurrent upload protection
    if (uploadLocks.putIfAbsent(lockKey, Boolean.TRUE) != null) {
      throw new DuplicateResourceException(
          "An upload is already in progress", "UPLOAD_IN_PROGRESS");
    }

    try {
      // Get next version number atomically
      int nextVersion =
          submissionType == SubmissionType.INDIVIDUAL
              ? submissionRepository
                      .findMaxVersionNumberByStudent(uploaderId, assignmentId)
                      .orElse(0)
                  + 1
              : submissionRepository.findMaxVersionNumber(teamId, assignmentId).orElse(0) + 1;
      Instant now = Instant.now();

      Instant effectiveDueAt = assignment.getDueAt();
      if (submissionType == SubmissionType.INDIVIDUAL) {
        effectiveDueAt =
            extensionRequestRepository
                .findTopByAssignmentIdAndStudentIdAndStatusOrderByRespondedAtDesc(
                    assignmentId, uploaderId, ExtensionRequestStatus.APPROVED)
                .map(ExtensionRequest::getRequestedDueAt)
                .orElse(assignment.getDueAt());
      } else if (teamId != null) {
        effectiveDueAt =
            extensionRequestRepository
                .findTopByAssignmentIdAndTeamIdAndStatusOrderByRespondedAtDesc(
                    assignmentId, teamId, ExtensionRequestStatus.APPROVED)
                .map(ExtensionRequest::getRequestedDueAt)
                .orElse(assignment.getDueAt());
      }

      boolean isLate = effectiveDueAt != null && now.isAfter(effectiveDueAt);

      // Store the file (use temp file since MultipartFile was already consumed)
      String hashedAssignmentId = hashidService.encode(assignmentId);
      String hashedOwnerId =
          submissionType == SubmissionType.INDIVIDUAL
              ? hashidService.encode(uploaderId)
              : hashidService.encode(teamId);
      String relativePath =
          S3KeyBuilder.submissionKey(hashedAssignmentId, hashedOwnerId, nextVersion, originalName);
      String storedPath = uploadToStorage(relativePath, tempFile, file);

      Submission submission =
          Submission.builder()
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
        List<Long> instructorIds =
            courseInstructorRepository.findByCourseId(assignment.getCourse().getId()).stream()
                .map(CourseInstructor::getUser)
                .filter(java.util.Objects::nonNull)
                .map(User::getId)
                .toList();
        recipientIds = new java.util.ArrayList<>();
        recipientIds.add(uploaderId);
        recipientIds.addAll(instructorIds);
      } else {
        recipientIds =
            teamMemberRepository.findByTeamId(teamId).stream()
                .filter(
                    m ->
                        m.getStatus() == TeamMemberStatus.ACCEPTED
                            && !m.getUser().getId().equals(uploaderId))
                .map(m -> m.getUser().getId())
                .toList();
      }

      eventPublisher.publishEvent(
          new SubmissionUploadedEvent(
              recipientIds,
              uploader.getFirstName() + " " + uploader.getLastName(),
              team != null ? team.getId() : null,
              submissionType == SubmissionType.INDIVIDUAL ? uploaderId : null,
              team != null ? team.getName() : null,
              assignment.getId(),
              assignment.getTitle(),
              nextVersion,
              submissionType,
              saved.getId()));

      auditService.log(
          uploaderId,
          "SUBMISSION_UPLOADED",
          "SUBMISSION",
          saved.getId(),
          "{\"assignmentId\":"
              + assignmentId
              + ",\"teamId\":"
              + (team != null ? team.getId() : null)
              + ",\"studentId\":"
              + (submissionType == SubmissionType.INDIVIDUAL ? uploaderId : null)
              + ",\"version\":"
              + nextVersion
              + "}",
          null);

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
    Submission sub =
        submissionRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Submission", id));

    // ADMIN and SYSTEM_ADMIN can access any submission
    if (role == UserRole.ADMIN || role == UserRole.SYSTEM_ADMIN) {
      return sub;
    }

    // INSTRUCTOR can access only if they teach the submission's course
    if (role == UserRole.INSTRUCTOR) {
      Long courseId = sub.getAssignment().getCourse().getId();
      if (!courseInstructorRepository.existsByCourseIdAndUserId(courseId, userId)) {
        throw new AccessDeniedException("You do not have access to this submission");
      }
      return sub;
    }

    // STUDENT can access only if they are a member of the team
    if (role == UserRole.STUDENT) {
      if (sub.getStudent() != null) {
        if (!sub.getStudent().getId().equals(userId)) {
          throw new AccessDeniedException("Access denied");
        }
      } else {
        boolean isMember =
            teamMemberRepository.findByTeamIdAndUserId(sub.getTeam().getId(), userId).isPresent();
        if (!isMember) {
          throw new AccessDeniedException("Access denied");
        }
      }
      return sub;
    }

    throw new AccessDeniedException("Access denied");
  }

  public List<Submission> getVersionHistory(
      Long teamId, Long assignmentId, Long userId, UserRole role) {
    // Check if team exists
    Team team =
        teamRepository
            .findById(teamId)
            .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));

    Assignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
    Long courseId = assignment.getCourse().getId();

    // ADMIN and SYSTEM_ADMIN can access any team
    if (role == UserRole.ADMIN || role == UserRole.SYSTEM_ADMIN) {
      return submissionRepository.findByTeamIdAndAssignmentIdOrderByVersionNumberDesc(
          teamId, assignmentId);
    }

    // INSTRUCTOR can access only if they teach the assignment's course
    if (role == UserRole.INSTRUCTOR) {
      if (!courseInstructorRepository.existsByCourseIdAndUserId(courseId, userId)) {
        throw new AccessDeniedException("You do not have access to this submission");
      }
      return submissionRepository.findByTeamIdAndAssignmentIdOrderByVersionNumberDesc(
          teamId, assignmentId);
    }

    // STUDENT can access only their own team's submissions
    if (role == UserRole.STUDENT) {
      boolean isMember = teamMemberRepository.findByTeamIdAndUserId(teamId, userId).isPresent();
      if (!isMember) {
        throw new AccessDeniedException("Access denied");
      }
      return submissionRepository.findByTeamIdAndAssignmentIdOrderByVersionNumberDesc(
          teamId, assignmentId);
    }

    throw new AccessDeniedException("Access denied");
  }

  public List<Submission> getTeamSubmissions(Long teamId, Long userId, UserRole role) {
    // Check if team exists
    Team team =
        teamRepository
            .findById(teamId)
            .orElseThrow(() -> new ResourceNotFoundException("Team", teamId));

    // ADMIN can access any team
    if (role == UserRole.ADMIN) {
      return submissionRepository.findByTeamIdOrderByVersionNumberDesc(teamId);
    }

    // INSTRUCTOR can access if team belongs to their course
    if (role == UserRole.INSTRUCTOR) {
      return submissionRepository.findByTeamIdOrderByVersionNumberDesc(teamId);
    }

    // STUDENT can access only their own team's submissions
    if (role == UserRole.STUDENT) {
      boolean isMember = teamMemberRepository.findByTeamIdAndUserId(teamId, userId).isPresent();
      if (!isMember) {
        throw new AccessDeniedException("Access denied");
      }
      return submissionRepository.findByTeamIdOrderByVersionNumberDesc(teamId);
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

  public PreviewResponseDto getPreviewUrl(String submissionHashId, Long userId, UserRole role) {
    // Decode hashid to get submission ID
    Long submissionId;
    try {
      submissionId = hashidService.decode(submissionHashId);
    } catch (Exception e) {
      throw new ResourceNotFoundException("Submission", submissionHashId);
    }

    // Get submission with access control
    Submission submission = getSubmission(submissionId, userId, role);

    // Get the S3 key for the submission file
    String s3Key =
        S3KeyBuilder.submissionKey(
            hashidService.encode(submission.getAssignment().getId()),
            submission.getStudent() != null
                ? hashidService.encode(submission.getStudent().getId())
                : hashidService.encode(submission.getTeam().getId()),
            submission.getVersionNumber(),
            submission.getFileName());

    // Get file size and validate
    long fileSizeBytes = s3Service.getObjectSize(s3Key);
    if (fileSizeBytes > MAX_PREVIEW_FILE_SIZE_BYTES) {
      throw new FileTooLargeForPreviewException(fileSizeBytes, MAX_PREVIEW_FILE_SIZE_BYTES);
    }

    String mimeType;
    try {
      mimeType = s3Service.headObject(s3Key).contentType();
    } catch (Exception e) {
      mimeType = null;
    }
    if (mimeType == null || mimeType.isBlank()) {
      mimeType = MimeTypeResolver.getMimeType(submission.getFileName());
      log.warn(
          "S3 HeadObject returned no Content-Type for key={}, falling back to extension",
          s3Key);
    }
    if (mimeType == null) {
      throw new PreviewNotSupportedException("Unknown");
    }

    // Check if MIME type is previewable
    if (!MimeTypeResolver.isPreviewable(mimeType)) {
      throw new PreviewNotSupportedException(mimeType);
    }

    // Generate presigned preview URL with inline disposition
    String previewUrl = s3Service.generatePresignedPreviewUrl(s3Key, mimeType);

    return PreviewResponseDto.builder()
        .previewUrl(previewUrl)
        .contentType(mimeType)
        .expiresInSeconds(15 * 60L)
        .filename(submission.getFileName())
        .build();
  }

  private String uploadToStorage(
      String relativePath, java.nio.file.Path tempFile, MultipartFile file) {
    java.util.Map<String, String> mdcContext = MDC.getCopyOfContextMap();
    CompletableFuture<String> uploadFuture;
    try {
      uploadFuture =
          CompletableFuture.supplyAsync(
              () -> {
                if (mdcContext != null) {
                  MDC.setContextMap(mdcContext);
                }
                try (InputStream is = java.nio.file.Files.newInputStream(tempFile)) {
                  String detectedMime = fileSecurityValidator.detectMimeType(tempFile);
                  return storageService.store(
                      relativePath, is, file.getSize(), detectedMime);
                } catch (IOException e) {
                  throw new StorageException("Failed to store file", e);
                }
              },
              uploadExecutor);
    } catch (RejectedExecutionException e) {
      reviewFlowMetrics.recordAsyncRejected("uploadExecutor");
      log.warn(
          "uploadExecutor queue full — submission upload rejected for path={}", relativePath);
      throw new ServiceUnavailableException(
          "Upload service is temporarily at capacity. Please try again shortly.");
    }
    try {
      return uploadFuture.get(uploadTimeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      s3Service.deleteObjectSilently(relativePath);
      throw new FileUploadTimeoutException(uploadTimeoutSeconds);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof RejectedExecutionException) {
        reviewFlowMetrics.recordAsyncRejected("uploadExecutor");
        throw new ServiceUnavailableException(
            "Upload service is temporarily at capacity. Please try again shortly.");
      }
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      throw new StorageException("Upload failed", cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StorageException("Upload interrupted", e);
    }
  }
}
