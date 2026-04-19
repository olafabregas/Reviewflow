package com.reviewflow.service;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.exception.CannotCommitWithErrorsException;
import com.reviewflow.exception.ImportSessionExpiredException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.dto.response.InstructorScoreImportCommitResponse;
import com.reviewflow.model.dto.response.InstructorScoreImportPreviewResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.InstructorScoreRepository;
import com.reviewflow.repository.TeamRepository;
import com.reviewflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final InstructorScoreRepository instructorScoreRepository;
    private final InstructorScoreService instructorScoreService;
    private final CacheManager cacheManager;

    @Value("${cache.csv-imports.ttl-seconds:600}")
    private int csvImportTtlSeconds;

    @Transactional(readOnly = true)
    public InstructorScoreImportPreviewResponse dryRun(Long assignmentId, Long actorId, MultipartFile file) {
        Assignment assignment = assignmentRepository.findWithDetailsById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));

        ensureCanManage(assignment, actorId);

        if (file == null || file.isEmpty()) {
            throw new com.reviewflow.exception.ValidationException("CSV file is required", "VALIDATION_ERROR");
        }

        List<InstructorScoreImportPreviewResponse.ValidRow> validRows = new ArrayList<>();
        List<InstructorScoreImportPreviewResponse.RowIssue> errors = new ArrayList<>();
        List<InstructorScoreImportPreviewResponse.RowIssue> warnings = new ArrayList<>();

        List<String[]> rows = parseRows(file);
        if (rows.isEmpty()) {
            throw new com.reviewflow.exception.ValidationException("CSV file is empty", "VALIDATION_ERROR");
        }

        String[] header = rows.get(0);
        boolean teamMode = assignment.getSubmissionType() == SubmissionType.TEAM;
        validateHeaders(header, teamMode);

        for (int i = 1; i < rows.size(); i++) {
            int rowNumber = i + 1;
            String[] row = rows.get(i);
            if (row.length < 2) {
                errors.add(issue(rowNumber, null, null, "Row must include identifier and score", null));
                continue;
            }

            try {
                if (teamMode) {
                    String teamRawId = valueAt(row, 0);
                    BigDecimal score = new BigDecimal(valueAt(row, 1));
                    String comment = valueAt(row, 2);
                    Long teamId = parseLong(teamRawId);
                    if (teamId == null || teamRepository.findById(teamId).isEmpty()) {
                        errors.add(issue(rowNumber, null, teamRawId, "Team not found", null));
                        continue;
                    }
                    if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(assignment.getMaxScore()) > 0) {
                        errors.add(issue(rowNumber, null, teamRawId, "Score exceeds allowed range", null));
                        continue;
                    }
                    if (instructorScoreRepository.findByAssignment_IdAndTeam_Id(assignmentId, teamId)
                            .map(s -> Boolean.TRUE.equals(s.getIsPublished()))
                            .orElse(false)) {
                        warnings.add(issue(rowNumber, null, teamRawId, null, "Published score already exists"));
                        continue;
                    }

                    validRows.add(InstructorScoreImportPreviewResponse.ValidRow.builder()
                            .teamId(teamRawId)
                            .score(score)
                            .comment(comment)
                            .build());
                } else {
                    String email = valueAt(row, 0).toLowerCase();
                    BigDecimal score = new BigDecimal(valueAt(row, 1));
                    String comment = valueAt(row, 2);
                    User student = userRepository.findByEmail(email).orElse(null);
                    if (student == null) {
                        errors.add(issue(rowNumber, email, null, "Student email not found", null));
                        continue;
                    }
                    if (!courseEnrollmentRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), student.getId())) {
                        errors.add(issue(rowNumber, email, null, "Student not enrolled in this course", null));
                        continue;
                    }
                    if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(assignment.getMaxScore()) > 0) {
                        errors.add(issue(rowNumber, email, null, "Score exceeds allowed range", null));
                        continue;
                    }
                    if (instructorScoreRepository.findByAssignment_IdAndStudent_Id(assignmentId, student.getId())
                            .map(s -> Boolean.TRUE.equals(s.getIsPublished()))
                            .orElse(false)) {
                        warnings.add(issue(rowNumber, email, null, null, "Published score already exists"));
                        continue;
                    }

                    validRows.add(InstructorScoreImportPreviewResponse.ValidRow.builder()
                            .studentEmail(email)
                            .score(score)
                            .comment(comment)
                            .build());
                }
            } catch (NumberFormatException ex) {
                errors.add(issue(rowNumber, teamMode ? null : valueAt(row, 0), teamMode ? valueAt(row, 0) : null, "Invalid score format", null));
            }
        }

        String importId = null;
        if (!validRows.isEmpty()) {
            importId = UUID.randomUUID().toString();
            Cache cache = cacheManager.getCache(CacheConfig.CACHE_CSV_IMPORTS);
            if (cache != null) {
                cache.put(importId, new CachedImportSession(assignmentId, actorId, teamMode, validRows, errors, warnings, Instant.now()));
            }
        }

        return InstructorScoreImportPreviewResponse.builder()
                .importId(importId)
                .totalRows(Math.max(0, rows.size() - 1))
                .valid(validRows)
                .errors(errors)
                .warnings(warnings)
                .expiresInSeconds(csvImportTtlSeconds)
                .build();
    }

    @Transactional
    public InstructorScoreImportCommitResponse commit(Long assignmentId, Long actorId, String importId) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_CSV_IMPORTS);
        if (cache == null) {
            throw new ImportSessionExpiredException("Import session store is unavailable");
        }

        CachedImportSession session = cache.get(importId, CachedImportSession.class);
        if (session == null || !session.assignmentId().equals(assignmentId) || !session.actorId().equals(actorId)) {
            throw new ImportSessionExpiredException("Import session expired. Upload CSV again");
        }
        if (!session.errors().isEmpty()) {
            throw new CannotCommitWithErrorsException("Resolve all errors before committing");
        }

        int created = 0;
        int updated = 0;
        for (InstructorScoreImportPreviewResponse.ValidRow row : session.validRows()) {
            if (session.teamMode()) {
                Long teamId = parseLong(row.getTeamId());
                boolean existed = instructorScoreRepository.findByAssignment_IdAndTeam_Id(assignmentId, teamId).isPresent();
                instructorScoreService.create(assignmentId, actorId, null, teamId, row.getScore(), row.getComment());
                if (existed) {
                    updated++;
                } else {
                    created++;
                }
            } else {
                User student = userRepository.findByEmail(row.getStudentEmail())
                        .orElseThrow(() -> new ResourceNotFoundException("User", row.getStudentEmail()));
                boolean existed = instructorScoreRepository.findByAssignment_IdAndStudent_Id(assignmentId, student.getId()).isPresent();
                instructorScoreService.create(assignmentId, actorId, student.getId(), null, row.getScore(), row.getComment());
                if (existed) {
                    updated++;
                } else {
                    created++;
                }
            }
        }

        cache.evict(importId);
        return InstructorScoreImportCommitResponse.builder()
                .created(created)
                .updated(updated)
                .message(created + updated + " scores saved as drafts. Use publish-all to release to students.")
                .build();
    }

    private void ensureCanManage(Assignment assignment, Long actorId) {
        User actor = userRepository.findById(actorId).orElseThrow(() -> new ResourceNotFoundException("User", actorId));
        if (actor.getRole() == com.reviewflow.model.entity.UserRole.SYSTEM_ADMIN) {
            return;
        }
        if (!courseInstructorRepository.existsByCourse_IdAndUser_Id(assignment.getCourse().getId(), actorId)) {
            throw new com.reviewflow.exception.AccessDeniedException("Not authorized to import scores for this course");
        }
    }

    private List<String[]> parseRows(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(line.split(",", -1));
                }
            }
            return rows;
        } catch (IOException e) {
            throw new com.reviewflow.exception.ValidationException("Unable to read CSV file", "VALIDATION_ERROR");
        }
    }

    private void validateHeaders(String[] header, boolean teamMode) {
        if (teamMode) {
            if (header.length < 2 || !"team_id".equalsIgnoreCase(header[0].trim()) || !"score".equalsIgnoreCase(header[1].trim())) {
                throw new com.reviewflow.exception.ValidationException("CSV headers must be: team_id,score[,comment]", "VALIDATION_ERROR");
            }
        } else if (header.length < 2 || !"student_email".equalsIgnoreCase(header[0].trim()) || !"score".equalsIgnoreCase(header[1].trim())) {
            throw new com.reviewflow.exception.ValidationException("CSV headers must be: student_email,score[,comment]", "VALIDATION_ERROR");
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

    private InstructorScoreImportPreviewResponse.RowIssue issue(int row, String email, String teamId, String error, String warning) {
        return InstructorScoreImportPreviewResponse.RowIssue.builder()
                .row(row)
                .studentEmail(email)
                .teamId(teamId)
                .error(error)
                .warning(warning)
                .build();
    }

    private record CachedImportSession(Long assignmentId,
            Long actorId,
            boolean teamMode,
            List<InstructorScoreImportPreviewResponse.ValidRow> validRows,
            List<InstructorScoreImportPreviewResponse.RowIssue> errors,
            List<InstructorScoreImportPreviewResponse.RowIssue> warnings,
            Instant createdAt) {

    }
}
