package com.reviewflow.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Evaluation;
import com.reviewflow.model.entity.RubricCriterion;
import com.reviewflow.model.entity.RubricScore;
import com.reviewflow.model.entity.Submission;
import com.reviewflow.model.entity.Team;
import com.reviewflow.model.entity.User;
import com.reviewflow.service.HashidService;
import com.reviewflow.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class PdfGenerationServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private HashidService hashidService;

    @InjectMocks
    private PdfGenerationService pdfGenerationService;

    @Test
    void generateEvaluationPdf_buildsStandardizedKeyAndStoresPdf() {
        Evaluation evaluation = buildEvaluation(21L, "Software Design", "Team Alpha", "Ada", "Lovelace", true, true);
        RubricScore score = RubricScore.builder()
                .criterion(RubricCriterion.builder().name("Correctness").maxScore(10).build())
                .score(BigDecimal.valueOf(8))
                .comment("Good")
                .build();

        when(hashidService.encode(21L)).thenReturn("EVALHASH");
        when(storageService.store(eq("pdfs/EVALHASH/report.pdf"), any(), any(Long.class), eq("application/pdf")))
                .thenReturn("pdfs/EVALHASH/report.pdf");

        String path = pdfGenerationService.generateEvaluationPdf(evaluation, List.of(score));

        assertEquals("pdfs/EVALHASH/report.pdf", path);
        verify(storageService).store(eq("pdfs/EVALHASH/report.pdf"), any(), any(Long.class), eq("application/pdf"));
    }

    @Test
    void generateEvaluationPdf_handlesMissingOptionalFields() {
        Evaluation evaluation = buildEvaluation(22L, "Databases", "Team Beta", "Grace", "Hopper", false, false);
        when(hashidService.encode(22L)).thenReturn("E22");
        when(storageService.store(eq("pdfs/E22/report.pdf"), any(), any(Long.class), eq("application/pdf")))
                .thenReturn("pdfs/E22/report.pdf");

        String path = pdfGenerationService.generateEvaluationPdf(evaluation, List.of());

        assertEquals("pdfs/E22/report.pdf", path);
    }

    @Test
    void generateEvaluationPdf_handlesNullScoreAndCommentCells() {
        Evaluation evaluation = buildEvaluation(23L, "Networks", "Team Gamma", "Linus", "Torvalds", true, false);
        RubricScore score = RubricScore.builder()
                .criterion(RubricCriterion.builder().name("Design").maxScore(5).build())
                .score(null)
                .comment(null)
                .build();

        when(hashidService.encode(23L)).thenReturn("E23");
        when(storageService.store(eq("pdfs/E23/report.pdf"), any(), any(Long.class), eq("application/pdf")))
                .thenReturn("pdfs/E23/report.pdf");

        String path = pdfGenerationService.generateEvaluationPdf(evaluation, List.of(score));

        assertEquals("pdfs/E23/report.pdf", path);
    }

    private Evaluation buildEvaluation(Long id,
            String assignmentTitle,
            String teamName,
            String firstName,
            String lastName,
            boolean withPublishedAt,
            boolean withOverallComment) {
        Submission submission = Submission.builder()
                .assignment(Assignment.builder().title(assignmentTitle).build())
                .team(Team.builder().name(teamName).build())
                .versionNumber(2)
                .build();

        User instructor = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .build();

        return Evaluation.builder()
                .id(id)
                .submission(submission)
                .instructor(instructor)
                .publishedAt(withPublishedAt ? Instant.now() : null)
                .overallComment(withOverallComment ? "Overall good work" : null)
                .totalScore(BigDecimal.valueOf(8.5))
                .build();
    }
}
