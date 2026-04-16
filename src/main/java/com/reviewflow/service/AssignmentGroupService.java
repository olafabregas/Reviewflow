package com.reviewflow.service;

import com.reviewflow.config.CacheConfig;
import com.reviewflow.exception.CannotDeleteUncategorizedException;
import com.reviewflow.exception.GroupNotEmptyException;
import com.reviewflow.exception.GroupNotInCourseException;
import com.reviewflow.exception.InvalidDropLowestNException;
import com.reviewflow.exception.InvalidGroupWeightException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.model.dto.response.AssignmentGroupListResponse;
import com.reviewflow.model.dto.response.AssignmentGroupMoveResponse;
import com.reviewflow.model.dto.response.AssignmentGroupResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.AssignmentGroup;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.repository.AssignmentGroupRepository;
import com.reviewflow.repository.AssignmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.CourseRepository;
import com.reviewflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentGroupService {

    private static final BigDecimal MAX_GROUP_WEIGHT = new BigDecimal("100");

    private final AssignmentGroupRepository assignmentGroupRepository;
    private final AssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final HashidService hashidService;
    private final CacheManager cacheManager;

    @Transactional
    public AssignmentGroupResponse create(Long courseId, Long actorId, String name, BigDecimal weight, Integer dropLowestN, Integer displayOrder) {
        Course course = getCourse(courseId);
        verifyCanManage(courseId, actorId);
        validateWeight(weight);
        validateDropLowestN(dropLowestN, 0);

        User creator = getActor(actorId);
        AssignmentGroup group = AssignmentGroup.builder()
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
        auditService.log(actorId, "ASSIGNMENT_GROUP_CREATED", "AssignmentGroup", saved.getId(), "Created group: " + name, null);

        evictCourseCaches(courseId);
        return toResponse(saved, countAssignments(saved.getId()), calculateWeightWarning(courseId), false);
    }

    @Transactional
    public AssignmentGroupResponse update(Long groupId, Long actorId, String name, BigDecimal weight, Integer dropLowestN, Integer displayOrder) {
        AssignmentGroup group = getGroup(groupId);
        verifyCanManage(group.getCourse().getId(), actorId);

        validateWeight(weight);
        validateDropLowestN(dropLowestN, assignmentRepository.countByAssignmentGroup_Id(groupId));

        boolean nameChanged = !group.getName().equals(name);
        group.setName(name);
        group.setWeight(weight);
        group.setDropLowestN(dropLowestN);
        group.setDisplayOrder(displayOrder != null ? displayOrder : group.getDisplayOrder());
        group.setUpdatedAt(Instant.now());

        AssignmentGroup saved = assignmentGroupRepository.save(group);
        auditService.log(actorId, "ASSIGNMENT_GROUP_UPDATED", "AssignmentGroup", saved.getId(), "Updated group: " + name, null);

        Long courseId = saved.getCourse().getId();
        evictCourseCaches(courseId);
        if (nameChanged) {
            evictAssignmentCachesForCourse(courseId);
        }

        return toResponse(saved, countAssignments(saved.getId()), calculateWeightWarning(courseId), false);
    }

    @Transactional
    public void delete(Long groupId, Long actorId) {
        AssignmentGroup group = getGroup(groupId);
        verifyCanManage(group.getCourse().getId(), actorId);

        if (Boolean.TRUE.equals(group.getIsUncategorized())) {
            throw new CannotDeleteUncategorizedException("Cannot delete Uncategorized group");
        }

        long assignmentCount = assignmentRepository.countByAssignmentGroup_Id(groupId);
        if (assignmentCount > 0) {
            throw new GroupNotEmptyException("Move all assignments out of this group before deleting");
        }

        assignmentGroupRepository.delete(group);
        auditService.log(actorId, "ASSIGNMENT_GROUP_DELETED", "AssignmentGroup", groupId, "Deleted group", null);
        evictCourseCaches(group.getCourse().getId());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_ASSIGNMENT_GROUPS, key = "#courseId")
    public AssignmentGroupListResponse listByCourse(Long courseId) {
        getCourse(courseId);
        List<AssignmentGroup> groups = assignmentGroupRepository.findByCourse_IdOrderByDisplayOrderAsc(courseId);
        BigDecimal totalConfiguredWeight = calculateTotalConfiguredWeight(groups);
        String weightWarning = calculateWeightWarning(totalConfiguredWeight);

        return AssignmentGroupListResponse.builder()
                .groups(groups.stream()
                        .map(group -> toResponse(group, countAssignments(group.getId()), null, true))
                        .toList())
                .totalConfiguredWeight(totalConfiguredWeight)
                .weightWarning(weightWarning)
                .build();
    }

    @Transactional
    public AssignmentGroupMoveResponse moveAssignment(Long assignmentId, Long newGroupId, Long actorId) {
        Assignment assignment = assignmentRepository.findWithDetailsById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
        AssignmentGroup newGroup = getGroup(newGroupId);

        verifyCanManage(assignment.getCourse().getId(), actorId);

        if (!newGroup.getCourse().getId().equals(assignment.getCourse().getId())) {
            throw new GroupNotInCourseException("Assignment group does not belong to this course");
        }

        assignment.setAssignmentGroup(newGroup);
        assignmentRepository.save(assignment);

        auditService.log(actorId, "ASSIGNMENT_MOVED_TO_GROUP", "Assignment", assignmentId, "Moved to group: " + newGroup.getName(), null);

        evictAssignmentCache(assignmentId);
        evictCourseCaches(assignment.getCourse().getId());
        evictGradeOverviewCache();

        return AssignmentGroupMoveResponse.builder()
                .assignmentId(hashidService.encode(assignmentId))
                .newGroupId(hashidService.encode(newGroupId))
                .newGroupName(newGroup.getName())
                .build();
    }

    private AssignmentGroupResponse toResponse(AssignmentGroup group, long assignmentCount, String weightWarning, boolean includeAssignments) {
        List<AssignmentGroupResponse.AssignmentSummary> assignments = includeAssignments && group.getAssignments() != null
                ? group.getAssignments().stream()
                        .sorted(Comparator.comparing(Assignment::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
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
        return calculateWeightWarning(calculateTotalConfiguredWeight(assignmentGroupRepository.findByCourse_IdOrderByDisplayOrderAsc(courseId)));
    }

    private String calculateWeightWarning(BigDecimal totalConfiguredWeight) {
        if (totalConfiguredWeight.compareTo(MAX_GROUP_WEIGHT) == 0) {
            return null;
        }
        return "Group weights total " + formatWeight(totalConfiguredWeight) + "%. Grades will be normalised to completed work.";
    }

    private String formatWeight(BigDecimal weight) {
        return weight.stripTrailingZeros().toPlainString();
    }

    private long countAssignments(Long groupId) {
        return assignmentRepository.countByAssignmentGroup_Id(groupId);
    }

    private void validateWeight(BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(MAX_GROUP_WEIGHT) > 0) {
            throw new InvalidGroupWeightException("Weight must be between 0 and 100");
        }
    }

    private void validateDropLowestN(Integer dropLowestN, long assignmentCount) {
        if (dropLowestN == null || dropLowestN < 0) {
            throw new InvalidDropLowestNException("Drop lowest N must be greater than or equal to 0");
        }
        if (assignmentCount > 0 && dropLowestN >= assignmentCount) {
            throw new InvalidDropLowestNException("Cannot drop " + dropLowestN + " grades from a group with " + assignmentCount + " assignments");
        }
    }

    private Course getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private AssignmentGroup getGroup(Long groupId) {
        return assignmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("AssignmentGroup", groupId));
    }

    private User getActor(Long actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorId));
    }

    private void verifyCanManage(Long courseId, Long actorId) {
        User actor = getActor(actorId);
        if (actor.getRole() == UserRole.ADMIN || actor.getRole() == UserRole.SYSTEM_ADMIN) {
            return;
        }
        if (courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, actorId)) {
            return;
        }
        throw new AccessDeniedException("Not authorized to manage assignment groups for this course");
    }

    private void evictCourseCaches(Long courseId) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_ASSIGNMENT_GROUPS);
        if (cache != null) {
            cache.evict(courseId);
        }
    }

    private void evictAssignmentCache(Long assignmentId) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_ASSIGNMENT);
        if (cache != null) {
            cache.evict(assignmentId);
        }
    }

    private void evictAssignmentCachesForCourse(Long courseId) {
        assignmentRepository.findByCourse_Id(courseId).forEach(assignment -> evictAssignmentCache(assignment.getId()));
    }

    private void evictGradeOverviewCache() {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_GRADE_OVERVIEW);
        if (cache != null) {
            cache.clear();
        }
    }
}
