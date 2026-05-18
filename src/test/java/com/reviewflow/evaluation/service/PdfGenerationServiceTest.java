package com.reviewflow.evaluation.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reviewflow.evaluation.dto.EvaluationPdfContext;
import com.reviewflow.evaluation.dto.EvaluationPdfRubricRow;
import com.reviewflow.infrastructure.storage.StorageService;
import com.reviewflow.shared.util.HashidService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PdfGenerationServiceTest {

  @Mock private StorageService storageService;

  @Mock private HashidService hashidService;

  @InjectMocks private PdfGenerationService pdfGenerationService;

  @Test
  void generateEvaluationPdf_teamSubmitter_buildsKeyAndStoresValidPdf() throws IOException {
    EvaluationPdfContext context = teamContext(21L, true, true);

    when(hashidService.encode(21L)).thenReturn("EVALHASH");
    when(storageService.store(
            eq("pdfs/EVALHASH/report.pdf"), any(), any(Long.class), eq("application/pdf")))
        .thenReturn("pdfs/EVALHASH/report.pdf");

    String path = pdfGenerationService.generateEvaluationPdf(context);

    assertEquals("pdfs/EVALHASH/report.pdf", path);
    assertStoredPdfStartsWithPercentPdf();
  }

  @Test
  void generateEvaluationPdf_individualSubmitter_buildsKeyAndStoresValidPdf() throws IOException {
    EvaluationPdfContext context = individualContext(31L, false, false);

    when(hashidService.encode(31L)).thenReturn("E31");
    when(storageService.store(
            eq("pdfs/E31/report.pdf"), any(), any(Long.class), eq("application/pdf")))
        .thenReturn("pdfs/E31/report.pdf");

    String path = pdfGenerationService.generateEvaluationPdf(context);

    assertEquals("pdfs/E31/report.pdf", path);
    assertStoredPdfStartsWithPercentPdf();
  }

  @Test
  void generateEvaluationPdf_handlesMissingOptionalFields() {
    EvaluationPdfContext context = teamContext(22L, false, false);
    when(hashidService.encode(22L)).thenReturn("E22");
    when(storageService.store(
            eq("pdfs/E22/report.pdf"), any(), any(Long.class), eq("application/pdf")))
        .thenReturn("pdfs/E22/report.pdf");

    String path = pdfGenerationService.generateEvaluationPdf(context);

    assertEquals("pdfs/E22/report.pdf", path);
  }

  @Test
  void generateEvaluationPdf_handlesNullScoreAndCommentCells() {
    EvaluationPdfContext context =
        new EvaluationPdfContext(
            23L,
            "Networks",
            1,
            "Team: Team Gamma",
            "Linus Torvalds",
            null,
            List.of(new EvaluationPdfRubricRow("Design", null, 5, null)),
            BigDecimal.ZERO,
            null);

    when(hashidService.encode(23L)).thenReturn("E23");
    when(storageService.store(
            eq("pdfs/E23/report.pdf"), any(), any(Long.class), eq("application/pdf")))
        .thenReturn("pdfs/E23/report.pdf");

    String path = pdfGenerationService.generateEvaluationPdf(context);

    assertEquals("pdfs/E23/report.pdf", path);
  }

  @Test
  void render_individualContext_producesNonEmptyPdfDocument() {
    byte[] pdfBytes = pdfGenerationService.render(individualContext(40L, true, true));

    assertTrue(pdfBytes.length > 200);
    assertArrayEquals("%PDF-".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(pdfBytes, 5));
  }

  @Test
  void render_teamContext_producesNonEmptyPdfDocument() {
    byte[] pdfBytes = pdfGenerationService.render(teamContext(41L, true, true));

    assertTrue(pdfBytes.length > 200);
    assertArrayEquals("%PDF-".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(pdfBytes, 5));
  }

  private void assertStoredPdfStartsWithPercentPdf() throws IOException {
    ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
    verify(storageService)
        .store(any(String.class), streamCaptor.capture(), any(Long.class), eq("application/pdf"));
    try (InputStream stored = streamCaptor.getValue()) {
      byte[] header = stored.readNBytes(5);
      assertArrayEquals("%PDF-".getBytes(StandardCharsets.US_ASCII), header);
    }
  }

  private static EvaluationPdfContext teamContext(
      Long evaluationId, boolean withPublishedAt, boolean withOverallComment) {
    return new EvaluationPdfContext(
        evaluationId,
        "Software Design",
        2,
        "Team: Team Alpha",
        "Ada Lovelace",
        withPublishedAt ? Instant.now() : null,
        List.of(
            new EvaluationPdfRubricRow(
                "Correctness", BigDecimal.valueOf(8), 10, "Good")),
        BigDecimal.valueOf(8.5),
        withOverallComment ? "Overall good work" : null);
  }

  private static EvaluationPdfContext individualContext(
      Long evaluationId, boolean withPublishedAt, boolean withOverallComment) {
    return new EvaluationPdfContext(
        evaluationId,
        "Algorithms",
        1,
        "Student: Ada Lovelace (ada@example.com)",
        "Grace Hopper",
        withPublishedAt ? Instant.now() : null,
        List.of(),
        BigDecimal.valueOf(9),
        withOverallComment ? "Well done" : null);
  }
}
