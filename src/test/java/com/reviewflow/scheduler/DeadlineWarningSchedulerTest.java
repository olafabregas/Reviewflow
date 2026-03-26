package com.reviewflow.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.reviewflow.event.DeadlineWarningEvent;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Course;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseRepository;

@ExtendWith(MockitoExtension.class)
class DeadlineWarningSchedulerTest {

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private CourseEnrollmentRepository enrollmentRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DeadlineWarningScheduler scheduler;

    @Test
    void sendDeadlineWarnings_publishesEventWithDueAt() {
        Long assignmentId = 44L;
        Instant dueAt = Instant.parse("2026-03-26T12:00:00Z");

        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .title("Assignment A")
                .dueAt(dueAt)
                .course(Course.builder().id(9L).code("CSC101").build())
                .build();

        when(assignmentRepository.findPublishedDueBetween(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(assignmentId), List.of());
        when(enrollmentRepository.findEnrolledStudentsWithoutSubmission(assignmentId)).thenReturn(List.of(70L, 71L));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        scheduler.sendDeadlineWarnings();

        ArgumentCaptor<DeadlineWarningEvent> captor = ArgumentCaptor.forClass(DeadlineWarningEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DeadlineWarningEvent event = captor.getValue();
        assertEquals(List.of(70L, 71L), event.recipientUserIds());
        assertEquals(assignmentId, event.assignmentId());
        assertNotNull(event.dueAt());
        assertEquals(dueAt, event.dueAt());
    }

    @Test
    void sendDeadlineWarnings_skipsWhenNoStudentsWithoutSubmission() {
        Long assignmentId = 45L;

        when(assignmentRepository.findPublishedDueBetween(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(assignmentId), List.of());
        when(enrollmentRepository.findEnrolledStudentsWithoutSubmission(assignmentId)).thenReturn(List.of());

        scheduler.sendDeadlineWarnings();

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(DeadlineWarningEvent.class));
    }

    @Test
    void sendDeadlineWarnings_skipsWhenAssignmentNotFound() {
        Long assignmentId = 46L;

        when(assignmentRepository.findPublishedDueBetween(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(assignmentId), List.of());
        when(enrollmentRepository.findEnrolledStudentsWithoutSubmission(assignmentId)).thenReturn(List.of(80L));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        scheduler.sendDeadlineWarnings();

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(DeadlineWarningEvent.class));
    }
}
