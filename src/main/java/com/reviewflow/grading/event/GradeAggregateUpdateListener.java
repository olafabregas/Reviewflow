package com.reviewflow.grading.event;

import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.grading.dto.response.GradeOverviewDto;
import com.reviewflow.grading.service.GradeAggregateService;
import com.reviewflow.grading.service.GradeCalculationService;
import java.util.List;
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

  @Async("gradeAggregateExecutor")
  @EventListener
  public void handleGradePublished(GradePublishedEvent event) {
    try {
      gradeAggregateService.evictStudent(event.courseId(), event.studentId());
      GradeOverviewDto fresh =
          gradeCalculationService.calculateOverviewFromDb(event.courseId(), event.studentId());
      gradeAggregateService.storeInRedis(event.courseId(), event.studentId(), fresh);
    } catch (Exception e) {
      log.warn(
          "Grade aggregate update failed for student {} in course {}: {}",
          event.studentId(),
          event.courseId(),
          e.getMessage());
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
          log.warn(
              "Batch recompute failed for student {} in course {}: {}",
              studentId,
              event.courseId(),
              ex.getMessage());
        }
      }
    } catch (Exception e) {
      log.warn("Grade structure update failed for course {}: {}", event.courseId(), e.getMessage());
    }
  }
}
