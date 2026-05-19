package com.reviewflow.evaluation.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import com.reviewflow.evaluation.dto.EvaluationPdfContext;
import com.reviewflow.evaluation.dto.EvaluationPdfRubricRow;
import com.reviewflow.infrastructure.storage.S3KeyBuilder;
import com.reviewflow.infrastructure.storage.StorageService;
import com.reviewflow.shared.util.HashidService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PdfGenerationService {

  private static final DateTimeFormatter FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  private final StorageService storageService;
  private final HashidService hashidService;

  public String generateEvaluationPdf(EvaluationPdfContext context) {
    byte[] pdfBytes = render(context);
    String relativePath = S3KeyBuilder.pdfKey(hashidService.encode(context.evaluationId()));
    storageService.store(
        relativePath, new ByteArrayInputStream(pdfBytes), pdfBytes.length, "application/pdf");
    return relativePath;
  }

  byte[] render(EvaluationPdfContext context) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf)) {

      document.add(new Paragraph("Evaluation Report").setBold().setFontSize(18));
      document.add(new Paragraph("Assignment: " + context.assignmentTitle()));
      document.add(new Paragraph(context.submitterLabelLine()));
      document.add(new Paragraph("Submission Version: " + context.submissionVersion()));
      document.add(new Paragraph("Instructor: " + context.instructorDisplayName()));
      if (context.publishedAt() != null) {
        document.add(new Paragraph("Published At: " + FMT.format(context.publishedAt())));
      }
      document.add(new Paragraph(" "));

      if (!context.rubricRows().isEmpty()) {
        Table table =
            new Table(UnitValue.createPercentArray(new float[] {4, 2, 2, 4}))
                .useAllAvailableWidth();
        table.addHeaderCell("Criterion");
        table.addHeaderCell("Score");
        table.addHeaderCell("Max");
        table.addHeaderCell("Comment");
        for (EvaluationPdfRubricRow row : context.rubricRows()) {
          table.addCell(row.criterionName());
          table.addCell(row.score() != null ? row.score().toPlainString() : "-");
          table.addCell(String.valueOf(row.maxScore()));
          table.addCell(row.comment() != null ? row.comment() : "");
        }
        document.add(table);
        document.add(new Paragraph(" "));
        document.add(
            new Paragraph(
                "Total Score: "
                    + (context.totalScore() != null
                        ? context.totalScore().toPlainString()
                        : "0")));
      }

      if (context.overallComment() != null && !context.overallComment().isBlank()) {
        document.add(new Paragraph("Overall Comment:").setBold());
        document.add(new Paragraph(context.overallComment()));
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to generate PDF for evaluation " + context.evaluationId(), e);
    }
    return baos.toByteArray();
  }

  public Resource loadPdf(String path) {
    return storageService.load(path);
  }
}
