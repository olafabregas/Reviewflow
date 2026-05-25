package com.reviewflow.assignment.service;

import com.reviewflow.assignment.exception.CannotDeleteUncategorizedException;
import com.reviewflow.assignment.exception.GroupNotEmptyException;
import com.reviewflow.assignment.exception.GroupNotInCourseException;
import com.reviewflow.assignment.exception.InvalidDropLowestNException;
import com.reviewflow.assignment.exception.InvalidGroupWeightException;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.assignment.dto.response.AssignmentGroupListResponse;
import com.reviewflow.assignment.dto.response.AssignmentGroupMoveResponse;
import com.reviewflow.assignment.dto.response.AssignmentGroupResponse;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.AssignmentGroup;
import com.reviewflow.shared.domain.Course;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.assignment.repository.AssignmentGroupRepository;
import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.course.repository.CourseRepository;
import com.reviewflow.admin.service.AuditService;
import com.reviewflow.grading.service.GradeCalculationService;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.shared.constant.CacheNames;
import com.reviewflow.shared.util.HashidService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import com.reviewflow.grading.event.GradeStructureChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignmentGroupService {

  private static final BigDecimal MAX_GROUP_WEIGHT = new BigDecimal("100");

  private final AssignmentGroupRepository assignmentGroupRepository;
  private final AssignmentRepository assignmentRepository;
  private final CourseRepository courseRepository;
  private final CourseEnrollmentRepository courseEnrollmentRepository;
  private final CourseInstructorRepository courseInstructorRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final HashidService hashidService;
  private final AssignmentGroupCacheEviction assignmentGroupCacheEviction;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public AssignmentGroupResponse create(
      Long courseId,
      Long actorId,
      String name,
      BigDecimal weight,
      Integer dropLowestN,
      Integer displayOrder) {
    Course course = getCourse(courseId);
    verifyCanManage(courseId, actorId);
    validateWeight(weight);
    validateDropLowestN(dropLowestN, 0);

    User creator = getActor(actorId);
    AssignmentGroup group =
        AssignmentGroup.builder()
            .course(course)
            .name(name)
            .weight(weight)
            .dropLowestN(dropLowestN)
            .displayOrder(displayOrder != null ? displayOrder : 0)
            .isUncategorized(false)
            .createdBy(creator)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    AssignmentGroup saved = assignmentGroupRepository.save(group);
    auditService.log(
        actorId,
        "ASSIGNMENT_GROUP_CREATED",
        "AssignmentGroup",
        saved.getId(),
        "Created group: " + name,
        null);

    assignmentGroupCacheEviction.evictForCourse(courseId);
    return toResponse(
        saved, countAssignments(saved.getId()), calculateWeightWarning(courseId), false);
  }

  @Transactional
  public AssignmentGroupResponse update(
      Long groupId,
      Long actorId,
      String name,
      BigDecimal weight,
      Integer dropLowestN,
      Integer displayOrder) {
    AssignmentGroup group = getGroup(groupId);
    verifyCanManage(group.getCourse().getId(), actorId);

    validateWeight(weight);
    validateDropLowestN(dropLowestN, assignmentRepository.countByAssignmentGroupId(groupId));

    boolean nameChanged = !group.getName().equals(name);
    group.setName(name);
    group.setWeight(weight);
    group.setDropLowestN(dropLowestN);
    group.setDisplayOrder(displayOrder != null ? displayOrder : group.getDisplayOrder());
    group.setUpdatedAt(Instant.now());

    AssignmentGroup saved = assignmentGroupRepository.save(group);
    auditService.log(
        actorId,
        "ASSIGNMENT_GROUP_UPDATED",
        "AssignmentGroup",
        saved.getId(),
        "Updated group: " + name,
        null);

    Long courseId = saved.getCourse().getId();
    assignmentGroupCacheEviction.evictForCourse(courseId);
    eventPublisher.publishEvent(new GradeStructureChangedEvent(courseId));
    if (nameChanged) {
      evictAssignmentCachesForCourse(courseId);
    }

    return toResponse(
        saved, countAssignments(saved.getId()), calculateWeightWarning(courseId), false);
  }

  @Transactional
  public void delete(Long groupId, Long actorId) {
    AssignmentGroup group = getGroup(groupId);
    verifyCanManage(group.getCourse().getId(), actorId);

    if (Boolean.TRUE.equals(group.getIsUncategorized())) {
      throw new CannotDeleteUncategorizedException("Cannot delete Uncategorized group");
    }

    long assignmentCount = assignmentRepository.countByAssignmentGroupId(groupId);
    if (assignmentCount > 0) {
      throw new GroupNotEmptyException("Move all assignments out of this group before deleting");
    }

    assignmentGroupRepository.delete(group);
    auditService.log(
        actorId, "ASSIGNMENT_GROUP_DELETED", "AssignmentGroup", groupId, "Deleted group", null);
    assignmentGroupCacheEviction.evictForCourse(group.getCourse().getId());
  }

  @Transactional(readOnly = true)
  @Cacheable(value = CacheNames.CACHE_ASSIGNMENT_GROUPS, key = "#courseId")
  public AssignmentGroupListResponse listByCourse(Long courseId) {
    getCourse(courseId);
    List<AssignmentGroup> groups =
        assignmentGroupRepository.findByCourseIdOrderByDisplayOrderAsc(courseId);
    BigDecimal totalConfiguredWeight = calculateTotalConfiguredWeight(groups);
    String weightWarning = calculateWeightWarning(totalConfiguredWeight);

    return AssignmentGroupListResponse.builder()
        .groups(
            groups.stream()
                .map(group -> toResponse(group, countAssignments(group.getId()), null, true))
                .toList())
        .totalConfiguredWeight(totalConfiguredWeight)
        .weightWarning(weightWarning)
        .build();
  }

  @Transactional(readOnly = true)
  public void verifyCanView(Long courseId, Long actorId) {
    getCourse(courseId);
    User actor = getActor(actorId);

    if (actor.getRole() == UserRole.ADMIN || actor.getRole() == UserRole.SYSTEM_ADMIN) {
      return;
    }
    if (courseInstructorRepository.existsByCourseIdAndUserId(courseId, actorId)) {
      return;
    }
    if (courseEnrollmentRepository.existsByCourseIdAndUserId(courseId, actorId)) {
      return;
    }

    throw new AccessDeniedException("Not authorized to view assignment groups for this course");
  }

  @Transactional
  public AssignmentGroupMoveResponse moveAssignment(
      Long assignmentId, Long newGroupId, Long actorId) {
    Assignment assignment =
        assignmentRepository
            .findWithDetailsById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
    AssignmentGroup newGroup = getGroup(newGroupId);

    verifyCanManage(assignment.getCourse().getId(), actorId);

    if (!newGroup.getCourse().getId().equals(assignment.getCourse().getId())) {
      throw new GroupNotInCourseException("Assignment group does not belong to this course");
    }

    assignment.setAssignmentGroup(newGroup);
    assignmentRepository.save(assignment);

    auditService.log(
        actorId,
        "ASSIGNMENT_MOVED_TO_GROUP",
        "Assignment",
        assignmentId,
        "Moved to group: " + newGroup.getName(),
        null);

    assignmentGroupCacheEviction.evictAssignment(assignmentId);
    Long courseId = assignment.getCourse().getId();
    assignmentGroupCacheEviction.evictForCourse(courseId);
    eventPublisher.publishEvent(new GradeStructureChangedEvent(courseId));

    return AssignmentGroupMoveResponse.builder()
        .assignmentId(hashidService.encode(assignmentId))
        .newGroupId(hashidService.encode(newGroupId))
        .newGroupName(newGroup.getName())
        .build();
  }

  private AssignmentGroupResponse toResponse(
      AssignmentGroup group,
      long assignmentCount,
      String weightWarning,
      boolean includeAssignments) {
    List<AssignmentGroupResponse.AssignmentSummary> assignments =
        includeAssignments && group.getAssignments() != null
            ? group.getAssignments().stream()
                .sorted(
                    Comparator.comparing(
                        Assignment::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toSummary)
                .toList()
            : List.of();

    return AssignmentGroupResponse.builder()
        .id(hashidService.encode(group.getId()))
        .name(group.getName())
        .weight(group.getWeight())
        .dropLowestN(group.getDropLowestN())
        .displayOrder(group.getDisplayOrder())
        .isUncategorized(group.getIsUncategorized())
        .assignmentCount(assignmentCount)
        .assignments(assignments)
        .weightWarning(weightWarning)
        .build();
  }

  private AssignmentGroupResponse.AssignmentSummary toSummary(Assignment assignment) {
    return AssignmentGroupResponse.AssignmentSummary.builder()
        .id(hashidService.encode(assignment.getId()))
        .title(assignment.getTitle())
        .dueAt(assignment.getDueAt())
        .submissionType(assignment.getSubmissionType())
        .build();
  }

  private BigDecimal calculateTotalConfiguredWeight(List<AssignmentGroup> groups) {
    return groups.stream()
        .map(group -> group.getWeight() == null ? BigDecimal.ZERO : group.getWeight())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String calculateWeightWarning(Long courseId) {
    return calculateWeightWarning(
        calculateTotalConfiguredWeight(
            assignmentGroupRepository.findByCourseIdOrderByDisplayOrderAsc(courseId)));
  }

  private String calculateWeightWarning(BigDecimal totalConfiguredWeight) {
    if (totalConfiguredWeight.compareTo(MAX_GROUP_WEIGHT) == 0) {
      return null;
    }
    return "Group weights total "
        + formatWeight(totalConfiguredWeight)
        + "%. Grades will be normalised to completed work.";
  }

  private String formatWeight(BigDecimal weight) {
    return weight.stripTrailingZeros().toPlainString();
  }

  private long countAssignments(Long groupId) {
    return assignmentRepository.countByAssignmentGroupId(groupId);
  }

  private void validateWeight(BigDecimal weight) {
    if (weight == null
        || weight.compareTo(BigDecimal.ZERO) < 0
        || weight.compareTo(MAX_GROUP_WEIGHT) > 0) {
      throw new InvalidGroupWeightException("Weight must be between 0 and 100");
    }
  }

  private void validateDropLowestN(Integer dropLowestN, long assignmentCount) {
    if (dropLowestN == null || dropLowestN < 0) {
      throw new InvalidDropLowestNException("Drop lowest N must be greater than or equal to 0");
    }
    if (assignmentCount > 0 && dropLowestN >= assignmentCount) {
      throw new InvalidDropLowestNException(
          "Cannot drop "
              + dropLowestN
              + " grades from a group with "
              + assignmentCount
              + " assignments");
    }
  }

  private Course getCourse(Long courseId) {
    return courseRepository
        .findById(courseId)
        .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
  }

  private AssignmentGroup getGroup(Long groupId) {
    return assignmentGroupRepository
        .findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("AssignmentGroup", groupId));
  }

  private User getActor(Long actorId) {
    return userRepository
        .findById(actorId)
        .orElseThrow(() -> new ResourceNotFoundException("User", actorId));
  }

  private void verifyCanManage(Long courseId, Long actorId) {
    User actor = getActor(actorId);
    if (actor.getRole() == UserRole.ADMIN || actor.getRole() == UserRole.SYSTEM_ADMIN) {
      return;
    }
    if (courseInstructorRepository.existsByCourseIdAndUserId(courseId, actorId)) {
      return;
    }
    throw new AccessDeniedException("Not authorized to manage assignment groups for this course");
  }

  private void evictAssignmentCachesForCourse(Long courseId) {
    assignmentRepository
        .findByCourseId(courseId)
        .forEach(assignment -> assignmentGroupCacheEviction.evictAssignment(assignment.getId()));
  }
}
