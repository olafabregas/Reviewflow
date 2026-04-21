package com.reviewflow.pdf;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import com.reviewflow.model.entity.Evaluation;
import com.reviewflow.model.entity.RubricScore;
import com.reviewflow.storage.StorageService;
import com.reviewflow.util.S3KeyBuilder;
import com.reviewflow.util.HashidService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfGenerationService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final StorageService storageService;
    private final HashidService hashidService;

    public String generateEvaluationPdf(Evaluation evaluation, List<RubricScore> scores) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos); PdfDocument pdf = new PdfDocument(writer); Document document = new Document(pdf)) {

            document.add(new Paragraph("Evaluation Report").setBold().setFontSize(18));
            document.add(new Paragraph("Assignment: " + evaluation.getSubmission().getAssignment().getTitle()));
            document.add(new Paragraph("Team: " + evaluation.getSubmission().getTeam().getName()));
            document.add(new Paragraph("Submission Version: " + evaluation.getSubmission().getVersionNumber()));
            document.add(new Paragraph("Instructor: "
                    + evaluation.getInstructor().getFirstName() + " " + evaluation.getInstructor().getLastName()));
            if (evaluation.getPublishedAt() != null) {
                document.add(new Paragraph("Published At: " + FMT.format(evaluation.getPublishedAt())));
            }
            document.add(new Paragraph(" "));

            if (!scores.isEmpty()) {
                Table table = new Table(UnitValue.createPercentArray(new float[]{4, 2, 2, 4}))
                        .useAllAvailableWidth();
                table.addHeaderCell("Criterion");
                table.addHeaderCell("Score");
                table.addHeaderCell("Max");
                table.addHeaderCell("Comment");
                for (RubricScore rs : scores) {
                    table.addCell(rs.getCriterion().getName());
                    table.addCell(rs.getScore() != null ? rs.getScore().toPlainString() : "-");
                    table.addCell(String.valueOf(rs.getCriterion().getMaxScore()));
                    table.addCell(rs.getComment() != null ? rs.getComment() : "");
                }
                document.add(table);
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Total Score: "
                        + (evaluation.getTotalScore() != null ? evaluation.getTotalScore().toPlainString() : "0")));
            }

            if (evaluation.getOverallComment() != null && !evaluation.getOverallComment().isBlank()) {
                document.add(new Paragraph("Overall Comment:").setBold());
                document.add(new Paragraph(evaluation.getOverallComment()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF for evaluation " + evaluation.getId(), e);
        }
        String relativePath = S3KeyBuilder.pdfKey(hashidService.encode(evaluation.getId()));
        storageService.store(relativePath,
                new ByteArrayInputStream(baos.toByteArray()), baos.size(), "application/pdf");
        return relativePath;
    }

    public Resource loadPdf(String path) {
        return storageService.load(path);
    }
}
