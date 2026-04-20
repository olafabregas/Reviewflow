package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import com.opencsv.CSVWriter;
import com.reviewflow.exception.AssignmentNotInCourseException;
import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.NoSubmissionsFoundException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Evaluation;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.entity.TeamMember;
import com.reviewflow.model.entity.TeamMemberStatus;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.EvaluationRepository;
import com.reviewflow.repository.SubmissionRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GradeExportService {

    private static final String[] TEAM_HEADERS = {
            "Course Code", "Assignment Title", "Team Name", "Students", "Total Score", "Max Score", "Percentage",
            "Is Late", "Submitted At", "Evaluated At"
    };

    private static final String[] INDIVIDUAL_HEADERS = {
            "Course Code", "Assignment Title", "Student Name", "Student Email", "Total Score", "Max Score", "Percentage",
            "Is Late", "Submitted At", "Evaluated At"
    };

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final HashidService hashidService;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final SubmissionRepository submissionRepository;
    private final EvaluationRepository evaluationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuditService auditService;

    @Transactional
    public ExportResult export(String courseHashId, String assignmentHashId, Long actorUserId) {
        Long courseId = hashidService.decodeOrThrow(courseHashId);
        Long assignmentId = hashidService.decodeOrThrow(assignmentHashId);

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));

        if (!Objects.equals(assignment.getCourse().getId(), courseId)) {
            throw new AssignmentNotInCourseException();
        }

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorUserId));

        boolean isAdmin = actor.getRole() == UserRole.ADMIN;
        boolean isCourseInstructor = courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorUserId);
        if (!isAdmin && !isCourseInstructor) {
            throw new AccessDeniedException("You do not have permission to export grades for this assignment");
        }

        int maxScore = assignment.getRubricCriteria() == null ? 0
                : assignment.getRubricCriteria().stream()
                        .map(rc -> rc.getMaxScore() == null ? 0 : rc.getMaxScore())
                        .reduce(0, Integer::sum);

        List<String[]> rows = assignment.getSubmissionType() == SubmissionType.TEAM
                ? buildTeamRows(assignment, maxScore)
                : buildIndividualRows(assignment, maxScore);

        if (rows.isEmpty()) {
            throw new NoSubmissionsFoundException();
        }

        String[] headers = assignment.getSubmissionType() == SubmissionType.TEAM ? TEAM_HEADERS : INDIVIDUAL_HEADERS;
        byte[] csv = writeCsv(headers, rows);
        String filename = buildFileName(assignment.getCourse().getCode(), assignment.getTitle());

        auditService.log(
                actorUserId,
                "GRADE_EXPORT_DOWNLOADED",
                "Assignment",
                assignmentId,
                "{\"courseId\":" + courseId + ",\"assignmentId\":" + assignmentId + ",\"actorId\":" + actorUserId + "}",
                null
        );

        return new ExportResult(csv, filename);
    }

    private List<String[]> buildTeamRows(Assignment assignment, int maxScore) {
        List<Submission> submissions = submissionRepository.findLatestTeamSubmissionsByAssignmentId(assignment.getId());
        Map<Long, Evaluation> evaluationBySubmissionId = findPublishedEvaluationsBySubmissionId(submissions);

        List<Long> teamIds = submissions.stream()
                .map(Submission::getTeam)
                .filter(Objects::nonNull)
                .map(team -> team.getId())
                .distinct()
                .toList();

        Map<Long, List<TeamMember>> acceptedMembersByTeamId = teamMemberRepository
                .findByTeamIdsAndStatusWithUser(teamIds, TeamMemberStatus.ACCEPTED)
                .stream()
                .collect(Collectors.groupingBy(tm -> tm.getTeam().getId()));

        List<String[]> rows = new ArrayList<>();
        for (Submission submission : submissions) {
            Long teamId = submission.getTeam() == null ? null : submission.getTeam().getId();
            List<TeamMember> acceptedMembers = acceptedMembersByTeamId.getOrDefault(teamId, List.of());
            List<String> studentNames = acceptedMembers.stream()
                    .map(TeamMember::getUser)
                    .filter(Objects::nonNull)
                    .map(user -> fullName(user.getFirstName(), user.getLastName()))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            Evaluation evaluation = evaluationBySubmissionId.get(submission.getId());
            rows.add(csvSafeRow(
                    assignment.getCourse().getCode(),
                    assignment.getTitle(),
                    submission.getTeam() == null ? "" : submission.getTeam().getName(),
                    String.join(", ", studentNames),
                    formatScore(evaluation),
                    String.valueOf(maxScore),
                    formatPercentage(evaluation, maxScore),
                    yesNo(submission.getIsLate()),
                    formatInstant(submission.getUploadedAt()),
                    formatInstant(evaluation == null ? null : evaluation.getPublishedAt())
            ));
        }

        return rows;
    }

    private List<String[]> buildIndividualRows(Assignment assignment, int maxScore) {
        List<Submission> submissions = submissionRepository.findLatestIndividualSubmissionsByAssignmentId(assignment.getId());
        Map<Long, Evaluation> evaluationBySubmissionId = findPublishedEvaluationsBySubmissionId(submissions);

        List<String[]> rows = new ArrayList<>();
        for (Submission submission : submissions) {
            User student = submission.getStudent();
            Evaluation evaluation = evaluationBySubmissionId.get(submission.getId());

            rows.add(csvSafeRow(
                    assignment.getCourse().getCode(),
                    assignment.getTitle(),
                    student == null ? "" : fullName(student.getLastName(), student.getFirstName()),
                    student == null ? "" : nullToEmpty(student.getEmail()),
                    formatScore(evaluation),
                    String.valueOf(maxScore),
                    formatPercentage(evaluation, maxScore),
                    yesNo(submission.getIsLate()),
                    formatInstant(submission.getUploadedAt()),
                    formatInstant(evaluation == null ? null : evaluation.getPublishedAt())
            ));
        }

        return rows;
    }

    private Map<Long, Evaluation> findPublishedEvaluationsBySubmissionId(List<Submission> submissions) {
        if (submissions.isEmpty()) {
            return Map.of();
        }

        List<Long> submissionIds = submissions.stream().map(Submission::getId).toList();
        Map<Long, Evaluation> map = new HashMap<>();
        for (Evaluation evaluation : evaluationRepository.findPublishedBySubmissionIds(submissionIds)) {
            map.put(evaluation.getSubmission().getId(), evaluation);
        }
        return map;
    }

    private byte[] writeCsv(String[] headers, List<String[]> rows) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
                CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeNext(headers, false);
            for (String[] row : rows) {
                csvWriter.writeNext(row, false);
            }
            csvWriter.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate CSV export", e);
        }
    }

    private String buildFileName(String courseCode, String assignmentTitle) {
        String safeCourseCode = (courseCode == null ? "course" : courseCode.trim()).replaceAll("[^A-Za-z0-9_-]", "");
        String normalized = assignmentTitle == null ? "assignment" : assignmentTitle.toLowerCase(Locale.ROOT);
        String hyphenated = normalized.replaceAll("\\s+", "-");
        String stripped = hyphenated.replaceAll("[^a-z0-9-]", "");
        String collapsed = stripped.replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        String titlePart = collapsed.isBlank() ? "assignment" : collapsed;
        if (titlePart.length() > 60) {
            titlePart = titlePart.substring(0, 60);
            titlePart = titlePart.replaceAll("-+$", "");
        }
        String date = DATE_FORMAT.format(LocalDate.now(ZoneOffset.UTC));
        return safeCourseCode + "_" + titlePart + "_" + date + ".csv";
    }

    private String formatScore(Evaluation evaluation) {
        if (evaluation == null || evaluation.getTotalScore() == null) {
            return "";
        }
        return trimTrailingZeros(evaluation.getTotalScore());
    }

    private String formatPercentage(Evaluation evaluation, int maxScore) {
        if (evaluation == null || evaluation.getTotalScore() == null || maxScore <= 0) {
            return "";
        }
        BigDecimal percent = evaluation.getTotalScore()
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maxScore), 1, RoundingMode.HALF_UP);
        return percent.toPlainString() + "%";
    }

    private String trimTrailingZeros(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : TIMESTAMP_FORMAT.format(instant);
    }

    private String yesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Yes" : "No";
    }

    private String fullName(String first, String last) {
        String f = nullToEmpty(first).trim();
        String l = nullToEmpty(last).trim();
        String full = (f + " " + l).trim();
        return full.isBlank() ? "" : full;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String[] csvSafeRow(String... values) {
        return Arrays.stream(values)
                .map(this::csvSafeCell)
                .toArray(String[]::new);
    }

    private String csvSafeCell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty()) {
            char firstChar = v.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@') {
                return "\t" + v;
            }
        }
        return v;
    }

    public record ExportResult(byte[] bytes, String filename) {
    }
}
