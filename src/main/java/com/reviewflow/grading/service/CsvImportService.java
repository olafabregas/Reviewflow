package com.reviewflow.grading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.grading.dto.response.ImportJobStartResponse;
import com.reviewflow.grading.exception.ImportInProgressException;
import com.reviewflow.grading.job.CsvRowError;
import com.reviewflow.grading.job.JobState;
import com.reviewflow.grading.job.JobStatus;
import com.reviewflow.grading.job.ValidatedRow;
import com.reviewflow.grading.repository.InstructorScoreRepository;
import com.reviewflow.infrastructure.config.ValidationConfig;
import com.reviewflow.infrastructure.jobs.AsyncJobService;
import com.reviewflow.infrastructure.jobs.JobProgressEvent;
import com.reviewflow.infrastructure.jobs.SseEmitterRegistry;
import com.reviewflow.infrastructure.storage.FileSecurityValidator;
import com.reviewflow.infrastructure.storage.S3Service;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.shared.exception.StorageException;
import com.reviewflow.shared.exception.ValidationException;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.team.repository.TeamRepository;
import com.reviewflow.user.repository.UserRepository;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class CsvImportService {

  private static final String CONTENT_TYPE_CSV = "text/csv";

  private final AssignmentRepository assignmentRepository;
  private final UserRepository userRepository;
  private final TeamRepository teamRepository;
  private final CourseInstructorRepository courseInstructorRepository;
  private final CourseEnrollmentRepository courseEnrollmentRepository;
  private final InstructorScoreRepository instructorScoreRepository;
  private final CsvImportCommitService csvImportCommitService;
  private final AsyncJobService asyncJobService;
  private final S3Service s3Service;
  private final SseEmitterRegistry sseEmitterRegistry;
  private final FileSecurityValidator fileSecurityValidator;
  private final HashidService hashidService;
  private final ObjectMapper objectMapper;
  private final Executor csvWorkerExecutor;
  private final Executor uploadExecutor;

  public CsvImportService(
      AssignmentRepository assignmentRepository,
      UserRepository userRepository,
      TeamRepository teamRepository,
      CourseInstructorRepository courseInstructorRepository,
      CourseEnrollmentRepository courseEnrollmentRepository,
      InstructorScoreRepository instructorScoreRepository,
      CsvImportCommitService csvImportCommitService,
      AsyncJobService asyncJobService,
      S3Service s3Service,
      SseEmitterRegistry sseEmitterRegistry,
      FileSecurityValidator fileSecurityValidator,
      HashidService hashidService,
      ObjectMapper objectMapper,
      @Qualifier("csvWorkerExecutor") Executor csvWorkerExecutor,
      @Qualifier("uploadExecutor") Executor uploadExecutor) {
    this.assignmentRepository = assignmentRepository;
    this.userRepository = userRepository;
    this.teamRepository = teamRepository;
    this.courseInstructorRepository = courseInstructorRepository;
    this.courseEnrollmentRepository = courseEnrollmentRepository;
    this.instructorScoreRepository = instructorScoreRepository;
    this.csvImportCommitService = csvImportCommitService;
    this.asyncJobService = asyncJobService;
    this.s3Service = s3Service;
    this.sseEmitterRegistry = sseEmitterRegistry;
    this.fileSecurityValidator = fileSecurityValidator;
    this.hashidService = hashidService;
    this.objectMapper = objectMapper;
    this.csvWorkerExecutor = csvWorkerExecutor;
    this.uploadExecutor = uploadExecutor;
  }

  public ImportJobStartResponse startImport(Long assignmentId, Long actorId, MultipartFile file) {
    Assignment assignment =
        assignmentRepository
            .findWithDetailsById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));

    ensureCanManage(assignment, actorId);

    if (assignment.getSubmissionType() != SubmissionType.INSTRUCTOR_GRADED) {
      throw new ValidationException(
          "CSV import is only supported for instructor-graded assignments", "VALIDATION_ERROR");
    }

    if (file == null || file.isEmpty()) {
      throw new ValidationException("CSV file is required", "VALIDATION_ERROR");
    }

    try {
      fileSecurityValidator.validateMessageAttachment(file, ValidationConfig.MESSAGE);
    } catch (IOException e) {
      throw new ValidationException("Invalid CSV file: " + e.getMessage(), "VALIDATION_ERROR");
    }

    String jobId = UUID.randomUUID().toString();
    Long courseId = assignment.getCourse().getId();
    String hashedCourseId = hashidService.encode(courseId);
    String hashedAssignmentId = hashidService.encode(assignmentId);
    String hashedInstructorId = hashidService.encode(actorId);

    if (!asyncJobService.acquireImportLock(hashedCourseId, jobId)) {
      throw new ImportInProgressException(
          "Another CSV import is already in progress for this course");
    }

    String sourceKey = "imports/" + jobId + "/source.csv";
    try {
      CompletableFuture.runAsync(
              () -> {
                try {
                  s3Service.putObject(
                      sourceKey, file.getInputStream(), file.getSize(), CONTENT_TYPE_CSV);
                } catch (IOException e) {
                  throw new StorageException("Failed to upload source CSV", e);
                }
              },
              uploadExecutor)
          .join();
    } catch (Exception e) {
      asyncJobService.releaseImportLock(hashedCourseId);
      if (e.getCause() instanceof StorageException se) {
        throw se;
      }
      throw new StorageException("Failed to upload source CSV", e);
    }

    Instant now = Instant.now();
    JobState initial =
        new JobState(
            jobId,
            JobStatus.UPLOADED,
            hashedAssignmentId,
            hashedInstructorId,
            hashedCourseId,
            0,
            0,
            0,
            0,
            sourceKey,
            null,
            null,
            null,
            now,
            now);
    asyncJobService.saveJob(initial);

    CompletableFuture.runAsync(() -> runValidation(jobId), csvWorkerExecutor);

    return ImportJobStartResponse.builder().jobId(jobId).status(JobStatus.UPLOADED).build();
  }

  public void runValidation(String jobId) {
    JobState state =
        asyncJobService
            .getJob(jobId)
            .orElseThrow(() -> new IllegalStateException("Job missing: " + jobId));

    Long assignmentId = hashidService.decodeOrThrow(state.assignmentId());
    Assignment assignment =
        assignmentRepository
            .findWithDetailsById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));

    asyncJobService.updateStatus(jobId, JobStatus.VALIDATING);

    List<CsvRowError> errors = new ArrayList<>();
    List<ValidatedRow> validRows = new ArrayList<>();
    boolean teamMode = assignment.getSubmissionType() == SubmissionType.TEAM;
    int rowCount = 0;
    int dataRows = 0;

    try {
      byte[] csvBytes = s3Service.getObject(state.sourceS3Key());
      List<String[]> rows = parseRows(csvBytes);
      if (rows.isEmpty()) {
        failJob(jobId, state.courseId(), "CSV file is empty");
        return;
      }

      validateHeaders(rows.get(0), teamMode);
      dataRows = Math.max(0, rows.size() - 1);

      for (int i = 1; i < rows.size(); i++) {
        rowCount++;
        String[] row = rows.get(i);
        validateDataRow(
            assignmentId, assignment, teamMode, row, i + 1, validRows, errors);

        if (rowCount % 50 == 0) {
          asyncJobService.updateProgress(jobId, rowCount);
          int percent = dataRows > 0 ? (rowCount * 100) / dataRows : 0;
          sseEmitterRegistry.push(
              jobId,
              JobProgressEvent.builder().processed(rowCount).total(dataRows).percent(percent).build());
        }
      }
    } catch (ValidationException e) {
      failJob(jobId, state.courseId(), e.getMessage());
      sseEmitterRegistry.complete(jobId);
      return;
    } catch (Exception e) {
      log.error("CSV validation failed for job {}: {}", jobId, e.getMessage(), e);
      failJob(jobId, state.courseId(), e.getMessage());
      sseEmitterRegistry.complete(jobId);
      return;
    }

    final int totalDataRows = dataRows;
    final int processedRows = rowCount;
    final int validCount = validRows.size();
    final int errorCount = errors.size();

    asyncJobService.updateJob(
        jobId,
        s ->
            new JobState(
                s.jobId(),
                s.status(),
                s.assignmentId(),
                s.instructorId(),
                s.courseId(),
                totalDataRows,
                validCount,
                errorCount,
                processedRows,
                s.sourceS3Key(),
                s.validatedRowsS3Key(),
                s.errorCsvS3Key(),
                s.errorMessage(),
                s.createdAt(),
                Instant.now()));

    if (!errors.isEmpty()) {
      String errorKey = "imports/" + jobId + "/errors.csv";
      s3Service.putObject(errorKey, buildErrorCsv(errors).getBytes(StandardCharsets.UTF_8), CONTENT_TYPE_CSV);
      asyncJobService.updateJob(
          jobId,
          s ->
              new JobState(
                  s.jobId(),
                  JobStatus.VALIDATION_FAILED,
                  s.assignmentId(),
                  s.instructorId(),
                  s.courseId(),
                  totalDataRows,
                  validCount,
                  errorCount,
                  processedRows,
                  s.sourceS3Key(),
                  null,
                  errorKey,
                  null,
                  s.createdAt(),
                  Instant.now()));
      asyncJobService.releaseImportLock(state.courseId());
      sseEmitterRegistry.complete(jobId);
      return;
    }

    String validatedKey = "imports/" + jobId + "/validated-rows.json";
    try {
      byte[] json = objectMapper.writeValueAsBytes(validRows);
      s3Service.putObject(validatedKey, json, "application/json");
    } catch (IOException e) {
      failJob(jobId, state.courseId(), "Failed to store validated rows");
      sseEmitterRegistry.complete(jobId);
      return;
    }

    asyncJobService.updateJob(
        jobId,
        s ->
            new JobState(
                s.jobId(),
                JobStatus.VALIDATION_PASSED,
                s.assignmentId(),
                s.instructorId(),
                s.courseId(),
                totalDataRows,
                validCount,
                0,
                processedRows,
                s.sourceS3Key(),
                validatedKey,
                null,
                null,
                s.createdAt(),
                Instant.now()));
    sseEmitterRegistry.complete(jobId);
  }

  public void runCommit(String jobId) {
    JobState state =
        asyncJobService
            .getJob(jobId)
            .orElseThrow(() -> new IllegalStateException("Job missing: " + jobId));
    String courseId = state.courseId();
    try {
      csvImportCommitService.commitJob(jobId);
    } catch (Exception e) {
      log.error("CSV commit failed for job {}: {}", jobId, e.getMessage(), e);
      asyncJobService.updateJob(
          jobId,
          s ->
              new JobState(
                  s.jobId(),
                  JobStatus.FAILED,
                  s.assignmentId(),
                  s.instructorId(),
                  s.courseId(),
                  s.totalRows(),
                  s.validRows(),
                  s.invalidRows(),
                  s.processedRows(),
                  s.sourceS3Key(),
                  s.validatedRowsS3Key(),
                  s.errorCsvS3Key(),
                  e.getMessage(),
                  s.createdAt(),
                  Instant.now()));
    } finally {
      asyncJobService.releaseImportLock(courseId);
    }
  }

  private void failJob(String jobId, String hashedCourseId, String message) {
    asyncJobService.updateJob(
        jobId,
        s ->
            new JobState(
                s.jobId(),
                JobStatus.FAILED,
                s.assignmentId(),
                s.instructorId(),
                s.courseId(),
                s.totalRows(),
                s.validRows(),
                s.invalidRows(),
                s.processedRows(),
                s.sourceS3Key(),
                s.validatedRowsS3Key(),
                s.errorCsvS3Key(),
                message,
                s.createdAt(),
                Instant.now()));
    asyncJobService.releaseImportLock(hashedCourseId);
  }

  private void validateDataRow(
      Long assignmentId,
      Assignment assignment,
      boolean teamMode,
      String[] row,
      int rowNumber,
      List<ValidatedRow> validRows,
      List<CsvRowError> errors) {
    if (row.length < 2) {
      errors.add(
          new CsvRowError(rowNumber, null, null, null, null, "Row must include identifier and score"));
      return;
    }
    try {
      if (teamMode) {
        String teamRawId = valueAt(row, 0);
        BigDecimal score = new BigDecimal(valueAt(row, 1));
        String comment = valueAt(row, 2);
        Long teamId = parseLong(teamRawId);
        if (teamId == null || teamRepository.findById(teamId).isEmpty()) {
          errors.add(
              new CsvRowError(rowNumber, null, teamRawId, valueAt(row, 1), comment, "Team not found"));
          return;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0
            || score.compareTo(assignment.getMaxScore()) > 0) {
          errors.add(
              new CsvRowError(
                  rowNumber, null, teamRawId, valueAt(row, 1), comment, "Score exceeds allowed range"));
          return;
        }
        if (instructorScoreRepository
            .findByAssignmentIdAndTeamId(assignmentId, teamId)
            .map(s -> Boolean.TRUE.equals(s.getIsPublished()))
            .orElse(false)) {
          errors.add(
              new CsvRowError(
                  rowNumber,
                  null,
                  teamRawId,
                  valueAt(row, 1),
                  comment,
                  "Published score already exists"));
          return;
        }
        validRows.add(new ValidatedRow(null, teamRawId, score, comment));
      } else {
        String email = valueAt(row, 0).toLowerCase();
        BigDecimal score = new BigDecimal(valueAt(row, 1));
        String comment = valueAt(row, 2);
        User student = userRepository.findByEmail(email).orElse(null);
        if (student == null) {
          errors.add(
              new CsvRowError(rowNumber, email, null, valueAt(row, 1), comment, "Student email not found"));
          return;
        }
        if (!courseEnrollmentRepository.existsByCourseIdAndUserId(
            assignment.getCourse().getId(), student.getId())) {
          errors.add(
              new CsvRowError(
                  rowNumber, email, null, valueAt(row, 1), comment, "Student not enrolled in this course"));
          return;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0
            || score.compareTo(assignment.getMaxScore()) > 0) {
          errors.add(
              new CsvRowError(
                  rowNumber, email, null, valueAt(row, 1), comment, "Score exceeds allowed range"));
          return;
        }
        if (instructorScoreRepository
            .findByAssignmentIdAndStudentId(assignmentId, student.getId())
            .map(s -> Boolean.TRUE.equals(s.getIsPublished()))
            .orElse(false)) {
          errors.add(
              new CsvRowError(
                  rowNumber,
                  email,
                  null,
                  valueAt(row, 1),
                  comment,
                  "Published score already exists"));
          return;
        }
        validRows.add(new ValidatedRow(email, null, score, comment));
      }
    } catch (NumberFormatException ex) {
      errors.add(
          new CsvRowError(
              rowNumber,
              teamMode ? null : valueAt(row, 0),
              teamMode ? valueAt(row, 0) : null,
              valueAt(row, 1),
              valueAt(row, 2),
              "Invalid score format"));
    }
  }

  private String buildErrorCsv(List<CsvRowError> errors) {
    StringBuilder csv = new StringBuilder();
    csv.append("student_email,team_id,score,comment,error_reason\n");
    for (CsvRowError e : errors) {
      csv.append(
          String.format(
              "%s,%s,%s,%s,%s\n",
              nullToEmpty(e.studentEmail()),
              nullToEmpty(e.teamId()),
              nullToEmpty(e.score()),
              nullToEmpty(e.comment()),
              nullToEmpty(e.reason())));
    }
    return csv.toString();
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s.replace(",", " ");
  }

  private List<String[]> parseRows(byte[] csvBytes) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8))) {
      List<String[]> rows = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          rows.add(line.split(",", -1));
        }
      }
      return rows;
    }
  }

  private void ensureCanManage(Assignment assignment, Long actorId) {
    User actor =
        userRepository
            .findById(actorId)
            .orElseThrow(() -> new ResourceNotFoundException("User", actorId));
    if (actor.getRole() == UserRole.SYSTEM_ADMIN) {
      return;
    }
    if (!courseInstructorRepository.existsByCourseIdAndUserId(
        assignment.getCourse().getId(), actorId)) {
      throw new com.reviewflow.shared.exception.AccessDeniedException(
          "Not authorized to import scores for this course");
    }
  }

  private void validateHeaders(String[] header, boolean teamMode) {
    if (teamMode) {
      if (header.length < 2
          || !"team_id".equalsIgnoreCase(header[0].trim())
          || !"score".equalsIgnoreCase(header[1].trim())) {
        throw new ValidationException(
            "CSV headers must be: team_id,score[,comment]", "VALIDATION_ERROR");
      }
    } else if (header.length < 2
        || !"student_email".equalsIgnoreCase(header[0].trim())
        || !"score".equalsIgnoreCase(header[1].trim())) {
      throw new ValidationException(
          "CSV headers must be: student_email,score[,comment]", "VALIDATION_ERROR");
    }
  }

  private String valueAt(String[] row, int index) {
    if (row.length <= index) {
      return null;
    }
    String value = row[index];
    return value == null ? null : value.trim();
  }

  private Long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
