package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import com.reviewflow.exception.AlreadyPublishedException;
import com.reviewflow.exception.ScoreNotPublishedException;
import com.reviewflow.model.dto.response.InstructorScoreResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.InstructorScore;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.model.enums.SubmissionType;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.InstructorScoreRepository;
import com.reviewflow.repository.TeamRepository;
import com.reviewflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstructorScoreServiceTest {

    @Mock
    private InstructorScoreRepository instructorScoreRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private HashidService hashidService;
    @Mock
    private GradeCalculationService gradeCalculationService;

    @InjectMocks
    private InstructorScoreService service;

    @Test
    void update_publishedScore_throwsAlreadyPublished() {
        Course course = Course.builder().id(10L).build();
        Assignment assignment = Assignment.builder()
                .id(20L)
                .course(course)
                .submissionType(SubmissionType.INSTRUCTOR_GRADED)
                .maxScore(new BigDecimal("100.00"))
                .build();

        InstructorScore published = InstructorScore.builder()
                .id(30L)
                .assignment(assignment)
                .score(new BigDecimal("90.00"))
                .maxScore(new BigDecimal("100.00"))
                .isPublished(true)
                .enteredAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(instructorScoreRepository.findById(30L)).thenReturn(Optional.of(published));
        when(userRepository.findById(77L)).thenReturn(Optional.of(User.builder().id(77L).role(UserRole.SYSTEM_ADMIN).build()));

        assertThrows(AlreadyPublishedException.class,
                () -> service.update(30L, 77L, new BigDecimal("95.00"), "update"));
    }

    @Test
    void reopen_draftScore_throwsScoreNotPublished() {
        Course course = Course.builder().id(10L).build();
        Assignment assignment = Assignment.builder()
                .id(20L)
                .course(course)
                .submissionType(SubmissionType.INSTRUCTOR_GRADED)
                .maxScore(new BigDecimal("100.00"))
                .build();

        InstructorScore draft = InstructorScore.builder()
                .id(30L)
                .assignment(assignment)
                .score(new BigDecimal("90.00"))
                .maxScore(new BigDecimal("100.00"))
                .isPublished(false)
                .enteredAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(instructorScoreRepository.findById(30L)).thenReturn(Optional.of(draft));
        when(userRepository.findById(77L)).thenReturn(Optional.of(User.builder().id(77L).role(UserRole.SYSTEM_ADMIN).build()));

        assertThrows(ScoreNotPublishedException.class,
                () -> service.reopen(30L, 77L, "reason"));
    }

    @Test
    void publish_draftScore_marksPublished() {
        Course course = Course.builder().id(10L).build();
        Assignment assignment = Assignment.builder()
                .id(20L)
                .course(course)
                .submissionType(SubmissionType.INSTRUCTOR_GRADED)
                .maxScore(new BigDecimal("100.00"))
                .build();
        InstructorScore draft = InstructorScore.builder()
                .id(30L)
                .assignment(assignment)
                .score(new BigDecimal("85.00"))
                .maxScore(new BigDecimal("100.00"))
                .isPublished(false)
                .enteredAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(instructorScoreRepository.findById(30L)).thenReturn(Optional.of(draft));
        when(userRepository.findById(77L)).thenReturn(Optional.of(User.builder().id(77L).role(UserRole.SYSTEM_ADMIN).build()));
        when(instructorScoreRepository.save(any(InstructorScore.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hashidService.encode(30L)).thenReturn("iscHash");
        when(hashidService.encode(20L)).thenReturn("asgHash");

        InstructorScoreResponse response = service.publish(30L, 77L);

        assertEquals(true, response.getIsPublished());
        verify(gradeCalculationService).evictCourseGradeCaches(10L);
    }
}
