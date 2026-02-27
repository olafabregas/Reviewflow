package com.reviewflow.service;

import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.entity.*;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.CourseRepository;
import com.reviewflow.repository.RubricCriterionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    @Transactional
    public Assignment createAssignment(Long courseId, String title, String description, Instant dueAt,
                                       Integer maxTeamSize, Instant teamLockAt, Long creatorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
        ensureInstructor(courseId, creatorId);
        Assignment a = Assignment.builder()
                .course(course)
                .title(title)
                .description(description)
                .dueAt(dueAt)
                .maxTeamSize(maxTeamSize != null ? maxTeamSize : 1)
                .teamLockAt(teamLockAt)
                .isPublished(false)
                .createdAt(Instant.now())
                .build();
        return assignmentRepository.save(a);
    }

    @Transactional
    public Assignment updateAssignment(Long assignmentId, String title, String description, Instant dueAt,
                                       Integer maxTeamSize, Instant teamLockAt, Long updaterId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), updaterId);
        if (title != null) a.setTitle(title);
        if (description != null) a.setDescription(description);
        if (dueAt != null) a.setDueAt(dueAt);
        if (maxTeamSize != null) a.setMaxTeamSize(maxTeamSize);
        if (teamLockAt != null) a.setTeamLockAt(teamLockAt);
        return assignmentRepository.save(a);
    }

    @Transactional
    public void publishAssignment(Long assignmentId, boolean published, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);
        a.setIsPublished(published);
        assignmentRepository.save(a);
    }

    public Assignment getAssignmentById(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
    }

    public List<Assignment> listAssignmentsForCourse(Long courseId, Long userId, UserRole role) {
        if (role == UserRole.ADMIN || courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
            return assignmentRepository.findByCourse_Id(courseId);
        }
        if (courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
            return assignmentRepository.findByCourse_IdAndIsPublishedTrue(courseId);
        }
        throw new ResourceNotFoundException("Course", courseId);
    }

    @Transactional
    public RubricCriterion addRubricCriteria(Long assignmentId, String name, String description, int maxScore, int displayOrder, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);
        RubricCriterion c = RubricCriterion.builder()
                .assignment(a)
                .name(name)
                .description(description)
                .maxScore(maxScore)
                .displayOrder(displayOrder)
                .build();
        return rubricCriterionRepository.save(c);
    }

    @Transactional
    public RubricCriterion updateRubricCriteria(Long assignmentId, Long criterionId, String name, String description, Integer maxScore, Integer displayOrder, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);
        RubricCriterion c = rubricCriterionRepository.findById(criterionId)
                .orElseThrow(() -> new ResourceNotFoundException("RubricCriterion", criterionId));
        if (!c.getAssignment().getId().equals(assignmentId)) {
            throw new ResourceNotFoundException("RubricCriterion", criterionId);
        }
        if (name != null) c.setName(name);
        if (description != null) c.setDescription(description);
        if (maxScore != null) c.setMaxScore(maxScore);
        if (displayOrder != null) c.setDisplayOrder(displayOrder);
        return rubricCriterionRepository.save(c);
    }

    @Transactional
    public void deleteRubricCriterion(Long assignmentId, Long criterionId, Long userId) {
        Assignment a = getAssignmentById(assignmentId);
        ensureInstructor(a.getCourse().getId(), userId);
        RubricCriterion c = rubricCriterionRepository.findById(criterionId)
                .orElseThrow(() -> new ResourceNotFoundException("RubricCriterion", criterionId));
        if (!c.getAssignment().getId().equals(assignmentId)) {
            throw new ResourceNotFoundException("RubricCriterion", criterionId);
        }
        rubricCriterionRepository.delete(c);
    }

    private void ensureInstructor(Long courseId, Long userId) {
        if (!courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Not instructor of this course");
        }
    }
}
