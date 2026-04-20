package com.reviewflow.model.dto.response;

import java.time.Instant;

import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.util.HashidService;

import lombok.Builder;
import lombok.Value;
import io.swagger.v3.oas.annotations.media.Schema;

@Value
@Builder
public class SubmissionResponse {
    String id;
    SubmissionType submissionType;
    String studentId;
    String teamId;
    String teamName;
    String assignmentId;
    String assignmentTitle;
    String courseCode;
    Integer versionNumber;
    String fileName;
    Long fileSizeBytes;
    Boolean isLate;
    Instant uploadedAt;
    String changeNote;
    String uploadedById;
    String uploadedByName;

    public static SubmissionResponse from(Submission s, HashidService hashidService) {
        return SubmissionResponse.builder()
                .id(hashidService.encode(s.getId()))
                .submissionType(s.getAssignment() != null ? s.getAssignment().getSubmissionType() : null)
                .studentId(hashidService.encode(s.getStudent() != null ? s.getStudent().getId() : null))
                .teamId(hashidService.encode(s.getTeam() != null ? s.getTeam().getId() : null))
                .teamName(s.getTeam() != null ? s.getTeam().getName() : null)
                .assignmentId(hashidService.encode(s.getAssignment() != null ? s.getAssignment().getId() : null))
                .assignmentTitle(s.getAssignment() != null ? s.getAssignment().getTitle() : null)
                .courseCode(s.getAssignment() != null && s.getAssignment().getCourse() != null
                        ? s.getAssignment().getCourse().getCode() : null)
                .versionNumber(s.getVersionNumber())
                .fileName(s.getFileName())
                .fileSizeBytes(s.getFileSizeBytes())
                .isLate(s.getIsLate())
                .uploadedAt(s.getUploadedAt())
                .changeNote(s.getChangeNote())
                .uploadedById(hashidService.encode(s.getUploadedBy() != null ? s.getUploadedBy().getId() : null))
                .uploadedByName(s.getUploadedBy() != null
                        ? s.getUploadedBy().getFirstName() + " " + s.getUploadedBy().getLastName() : null)
                .build();
    }
}
