package com.reviewflow.grading.event;

import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.grading.dto.response.GradeOverviewDto;
import com.reviewflow.grading.service.GradeAggregateService;
import com.reviewflow.grading.service.GradeCalculationService;
import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GradeAggregateUpdateListener {

  private final GradeAggregateService gradeAggregateService;
  private final GradeCalculationService gradeCalculationService;
  private final CourseEnrollmentRepository courseEnrollmentRepository;
  private final ReviewFlowMetrics metrics;

  private final Set<String> pendingRecomputes = ConcurrentHashMap.newKeySet();

  @Async("gradeAggregateExecutor")
  @EventListener
  public void handleGradePublished(GradePublishedEvent event) {
    String key = event.courseId() + ":" + event.studentId();

    if (!pendingRecomputes.add(key)) {
      log.debug("Grade aggregate recompute already queued for key={}", key);
      return;
    }

    try {
      gradeAggregateService.evictStudent(event.courseId(), event.studentId());
      GradeOverviewDto fresh =
          gradeCalculationService.calculateOverviewFromDb(event.courseId(), event.studentId());
      gradeAggregateService.storeInRedis(event.courseId(), event.studentId(), fresh);
    } catch (Exception e) {
      metrics.recordGradeAggregateFailed();
      log.error(
          "Grade aggregate update failed courseId={} studentId={}: {}",
          event.courseId(),
          event.studentId(),
          e.getMessage(),
          e);
    } finally {
      pendingRecomputes.remove(key);
    }
  }

  @Async("gradeAggregateExecutor")
  @EventListener
  public void handleGradeStructureChanged(GradeStructureChangedEvent event) {
    try {
      List<Long> studentIds =
          courseEnrollmentRepository.findWithUserByCourseId(event.courseId()).stream()
              .map(e -> e.getUser().getId())
              .toList();
      gradeAggregateService.evictCourse(event.courseId(), studentIds);
      for (Long studentId : studentIds) {
        try {
          GradeOverviewDto fresh =
              gradeCalculationService.calculateOverviewFromDb(event.courseId(), studentId);
          gradeAggregateService.storeInRedis(event.courseId(), studentId, fresh);
        } catch (Exception ex) {
          metrics.recordGradeAggregateFailed();
          log.error(
              "Batch recompute failed for student {} in course {}: {}",
              studentId,
              event.courseId(),
              ex.getMessage(),
              ex);
        }
      }
    } catch (Exception e) {
      metrics.recordGradeAggregateFailed();
      log.error(
          "Grade structure update failed for course {}: {}", event.courseId(), e.getMessage(), e);
    }
  }
}
