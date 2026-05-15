package com.reviewflow.infrastructure.scheduling;

import com.reviewflow.discussion.event.DiscussionReminderBatchEvent;
import com.reviewflow.discussion.repository.DiscussionReminderRow;
import com.reviewflow.discussion.repository.DiscussionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "scheduler.discussion-reminder.enabled", havingValue = "true", matchIfMissing = true)
public class DiscussionReminderScheduler {

  private final DiscussionRepository discussionRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(cron = "${scheduler.discussion-reminder.cron:0 0 * * * *}")
  public void sendDiscussionReminders() {
    Instant from = Instant.now();
    Instant to = from.plus(24, ChronoUnit.HOURS);
    List<DiscussionReminderRow> rows = discussionRepository.findStudentsNeedingDiscussionReminder(from, to);
    if (rows.isEmpty()) {
      return;
    }
    rows.stream()
        .collect(Collectors.groupingBy(DiscussionReminderRow::getDiscussionId))
        .forEach(
            (discussionId, list) -> {
              var first = list.get(0);
              var recipients =
                  list.stream()
                      .map(
                          r ->
                              new DiscussionReminderBatchEvent.ReminderRecipient(
                                  r.getStudentId(), r.getStudentEmail(), r.getStudentFirstName()))
                      .collect(Collectors.toList());
              eventPublisher.publishEvent(
                  new DiscussionReminderBatchEvent(
                      discussionId, first.getDiscussionTitle(), first.getDueAt(), recipients));
            });
    log.debug("Published {} discussion reminder batch(es)", rows.stream().map(DiscussionReminderRow::getDiscussionId).distinct().count());
  }
}
