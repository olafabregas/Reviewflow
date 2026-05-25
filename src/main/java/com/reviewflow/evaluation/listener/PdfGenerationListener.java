package com.reviewflow.evaluation.listener;

import com.reviewflow.evaluation.event.EvaluationPublishedEvent;
import com.reviewflow.evaluation.event.PdfReadyEvent;
import com.reviewflow.evaluation.dto.EvaluationPdfContext;
import com.reviewflow.evaluation.service.EvaluationService;
import com.reviewflow.evaluation.service.PdfGenerationService;
import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import com.reviewflow.shared.domain.Evaluation;
import com.reviewflow.shared.domain.RubricScore;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.grading.repository.RubricScoreRepository;
import com.reviewflow.evaluation.repository.EvaluationRepository;
import com.reviewflow.shared.util.HashidService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PdfGenerationListener {

  private final EvaluationRepository evaluationRepository;
  private final RubricScoreRepository rubricScoreRepository;
  private final PdfGenerationService pdfGenerationService;
  private final EvaluationService evaluationService;
  private final HashidService hashidService;
  private final ApplicationEventPublisher eventPublisher;
  private final ReviewFlowMetrics metrics;

  @Async("pdfExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleEvaluationPublished(EvaluationPublishedEvent event) {
    String hashedEvalId = hashidService.encode(event.evaluationId());
    MDC.put("evaluationId", hashedEvalId);
    try {
      Evaluation evaluation =
          evaluationRepository.findByIdWithPdfRelations(event.evaluationId()).orElse(null);
      if (evaluation == null) {
        log.warn(
            "PDF generation skipped — evaluation not found id={}", event.evaluationId());
        return;
      }

      if (evaluation.getPdfPath() != null) {
        log.debug(
            "PDF generation skipped — pdfPath already set evaluationId={}",
            event.evaluationId());
        return;
      }

      List<RubricScore> scores =
          rubricScoreRepository.findByEvaluationIdWithCriterion(event.evaluationId());
      EvaluationPdfContext context = EvaluationService.toPdfContext(evaluation, scores);
      String pdfPath = pdfGenerationService.generateEvaluationPdf(context);
      evaluationService.updatePdfPath(event.evaluationId(), pdfPath);

      List<Long> recipients = new ArrayList<>(event.recipientUserIds());
      if (event.submissionType() == SubmissionType.INDIVIDUAL && event.studentId() != null) {
        recipients = List.of(event.studentId());
      }
      eventPublisher.publishEvent(
          new PdfReadyEvent(
              event.evaluationId(),
              event.assignmentId(),
              recipients,
              event.assignmentTitle()));
      log.info("PDF generated for evaluation {}", hashedEvalId);
    } catch (Exception e) {
      log.error(
          "PDF generation failed evaluationId={}: {}",
          hashedEvalId,
          e.getMessage(),
          e);
      metrics.recordPdfGenerationFailed();
    } finally {
      MDC.remove("evaluationId");
    }
  }
}
