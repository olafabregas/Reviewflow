package com.reviewflow.scheduler;

import com.reviewflow.event.DeadlineWarningEvent;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineWarningScheduler {

    private final AssignmentRepository      assignmentRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseRepository          courseRepository;
    private final ApplicationEventPublisher  eventPublisher;

    // Runs every hour on the hour
    @Scheduled(cron = "0 0 * * * *")
    public void sendDeadlineWarnings() {
        log.info("Deadline warning scheduler running");
        checkDeadlines(48);
        checkDeadlines(24);
    }

    private void checkDeadlines(int hoursUntilDue) {
        Instant windowStart = Instant.now().plus(hoursUntilDue - 1, ChronoUnit.HOURS);
        Instant windowEnd   = Instant.now().plus(hoursUntilDue,     ChronoUnit.HOURS);

        List<Long> assignmentIds =
                assignmentRepository.findPublishedDueBetween(windowStart, windowEnd);

        for (Long assignmentId : assignmentIds) {
            // Only notify students who have NOT yet submitted
            List<Long> studentsWithoutSubmission =
                    enrollmentRepository.findEnrolledStudentsWithoutSubmission(assignmentId);

            if (studentsWithoutSubmission.isEmpty()) {
                continue;
            }

            // Get assignment details
            Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
            if (assignment == null) {
                continue;
            }

            String courseCode = assignment.getCourse().getCode();

            eventPublisher.publishEvent(new DeadlineWarningEvent(
                    studentsWithoutSubmission,
                    assignmentId,
                    assignment.getTitle(),
                    courseCode,
                    hoursUntilDue
            ));

            log.info("Sent {}h deadline warning for assignment {} to {} students",
                    hoursUntilDue, assignmentId, studentsWithoutSubmission.size());
        }
    }
}
