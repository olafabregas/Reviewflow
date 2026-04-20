package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import com.reviewflow.util.CacheNames;
import com.reviewflow.exception.CourseArchivedReadOnlyException;
import com.reviewflow.exception.ModuleNotFoundException;
import com.reviewflow.exception.ModuleNotInCourseException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.model.dto.response.AssignmentModuleMoveResponse;
import com.reviewflow.model.dto.response.AssignmentModuleResponse;
import com.reviewflow.model.dto.response.CourseModulesResponse;
import com.reviewflow.model.entity.Assignment;
import com.reviewflow.model.entity.AssignmentModule;
import com.reviewflow.model.entity.Course;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.repository.AssignmentModuleRepository;
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

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ModuleService {

    private static final int DEFAULT_DISPLAY_ORDER = 0;

    private final AssignmentModuleRepository assignmentModuleRepository;
    private final AssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final HashidService hashidService;
    private final CacheManager cacheManager;

    @Transactional
    public AssignmentModuleResponse create(Long courseId, Long actorId, String name, Integer displayOrder) {
        Course course = getCourse(courseId);
        verifyCanManage(courseId, actorId);
        ensureCourseWritable(course);

        User actor = getActor(actorId);
        AssignmentModule module = AssignmentModule.builder()
                .course(course)
                .name(name)
                .displayOrder(displayOrder != null ? displayOrder : DEFAULT_DISPLAY_ORDER)
                .createdBy(actor)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AssignmentModule saved = assignmentModuleRepository.save(module);
        auditService.log(actorId, "ASSIGNMENT_MODULE_CREATED", "AssignmentModule", saved.getId(), "Created module: " + name, null);
        evictCourseModulesCache(courseId);

        return toResponse(saved, List.of());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.CACHE_COURSE_MODULES, key = "#courseId")
    public CourseModulesResponse listByCourse(Long courseId) {
        getCourse(courseId);
        List<AssignmentModule> modules = assignmentModuleRepository.findByCourse_IdOrderByDisplayOrderAsc(courseId);
        List<AssignmentModuleResponse> moduleResponses = modules.stream()
                .map(module -> toResponse(module, sortedAssignments(module.getAssignments())))
                .toList();

        List<AssignmentModuleResponse.AssignmentSummary> unmoduled = assignmentRepository
                .findByCourse_IdAndAssignmentModuleIsNull(courseId)
                .stream()
                .sorted(Comparator.comparing(Assignment::getDueAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toSummary)
                .toList();

        return CourseModulesResponse.builder()
                .modules(moduleResponses)
                .unmoduled(unmoduled)
                .build();
    }

    @Transactional
    public AssignmentModuleResponse update(Long moduleId, Long actorId, String name, Integer displayOrder) {
        AssignmentModule module = getModule(moduleId);
        Long courseId = module.getCourse().getId();
        verifyCanManage(courseId, actorId);
        ensureCourseWritable(module.getCourse());

        if (name != null) {
            module.setName(name);
        }
        if (displayOrder != null) {
            module.setDisplayOrder(displayOrder);
        }
        module.setUpdatedAt(Instant.now());

        AssignmentModule saved = assignmentModuleRepository.save(module);
        auditService.log(actorId, "ASSIGNMENT_MODULE_UPDATED", "AssignmentModule", saved.getId(), "Updated module", null);
        evictCourseModulesCache(courseId);

        return toResponse(saved, sortedAssignments(saved.getAssignments()));
    }

    @Transactional
    public AssignmentModuleMoveResponse assignToModule(Long assignmentId, Long moduleId, Long actorId) {
        Assignment assignment = assignmentRepository.findWithDetailsById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));

        Long courseId = assignment.getCourse().getId();
        verifyCanManage(courseId, actorId);
        ensureCourseWritable(assignment.getCourse());

        AssignmentModule assignmentModule = null;
        if (moduleId != null) {
            assignmentModule = getModule(moduleId);
            if (!assignmentModule.getCourse().getId().equals(courseId)) {
                throw new ModuleNotInCourseException("Assignment module does not belong to this course");
            }
        }

        assignment.setAssignmentModule(assignmentModule);
        assignmentRepository.save(assignment);

        auditService.log(actorId, "ASSIGNMENT_MOVED_TO_MODULE", "Assignment", assignmentId, "Moved assignment module", null);
        evictAssignmentCache(assignmentId);
        evictCourseModulesCache(courseId);

        return AssignmentModuleMoveResponse.builder()
                .assignmentId(hashidService.encode(assignmentId))
                .moduleId(hashidService.encode(assignmentModule != null ? assignmentModule.getId() : null))
                .moduleName(assignmentModule != null ? assignmentModule.getName() : null)
                .build();
    }

    @Transactional
    public CourseModulesResponse reorder(Long courseId, List<Long> orderedModuleIds, Long actorId) {
        Course course = getCourse(courseId);
        verifyCanManage(courseId, actorId);
        ensureCourseWritable(course);

        List<AssignmentModule> modules = assignmentModuleRepository.findByCourse_IdOrderByDisplayOrderAsc(courseId);
        validateReorderRequest(modules, orderedModuleIds);

        for (int index = 0; index < orderedModuleIds.size(); index++) {
            Long moduleId = orderedModuleIds.get(index);
            final int displayOrder = index;
            modules.stream()
                    .filter(module -> module.getId().equals(moduleId))
                    .findFirst()
                    .ifPresent(module -> {
                        module.setDisplayOrder(displayOrder);
                        module.setUpdatedAt(Instant.now());
                    });
        }
        assignmentModuleRepository.saveAll(modules);

        auditService.log(actorId, "ASSIGNMENT_MODULES_REORDERED", "Course", courseId, "Reordered assignment modules", null);
        evictCourseModulesCache(courseId);

        return listByCourse(courseId);
    }

    @Transactional
    public long delete(Long moduleId, Long actorId) {
        AssignmentModule module = getModule(moduleId);
        Long courseId = module.getCourse().getId();
        verifyCanManage(courseId, actorId);
        ensureCourseWritable(module.getCourse());

        List<Assignment> linkedAssignments = assignmentRepository.findByCourse_Id(courseId).stream()
                .filter(assignment -> assignment.getAssignmentModule() != null
                && assignment.getAssignmentModule().getId().equals(moduleId))
                .toList();

        long affectedAssignments = linkedAssignments.size();
        linkedAssignments.forEach(assignment -> {
            assignment.setAssignmentModule(null);
            assignmentRepository.save(assignment);
            evictAssignmentCache(assignment.getId());
        });

        assignmentModuleRepository.delete(module);
        auditService.log(actorId, "ASSIGNMENT_MODULE_DELETED", "AssignmentModule", moduleId,
                "Deleted module; affected assignments: " + affectedAssignments, null);
        evictCourseModulesCache(courseId);

        return affectedAssignments;
    }

    private List<Assignment> sortedAssignments(List<Assignment> assignments) {
        return assignments.stream()
                .sorted(Comparator.comparing(Assignment::getDueAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private AssignmentModuleResponse toResponse(AssignmentModule module, List<Assignment> assignments) {
        return AssignmentModuleResponse.builder()
                .id(hashidService.encode(module.getId()))
                .name(module.getName())
                .displayOrder(module.getDisplayOrder())
                .assignments(assignments.stream().map(this::toSummary).toList())
                .build();
    }

    private AssignmentModuleResponse.AssignmentSummary toSummary(Assignment assignment) {
        return AssignmentModuleResponse.AssignmentSummary.builder()
                .id(hashidService.encode(assignment.getId()))
                .title(assignment.getTitle())
                .dueAt(assignment.getDueAt())
                .submissionType(assignment.getSubmissionType())
                .groupId(hashidService.encode(assignment.getAssignmentGroup() != null ? assignment.getAssignmentGroup().getId() : null))
                .groupName(assignment.getAssignmentGroup() != null ? assignment.getAssignmentGroup().getName() : null)
                .build();
    }

    private Course getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private AssignmentModule getModule(Long moduleId) {
        return assignmentModuleRepository.findById(moduleId)
                .orElseThrow(() -> new ModuleNotFoundException("Assignment module not found"));
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
        throw new AccessDeniedException("Not authorized to manage assignment modules for this course");
    }

    private void ensureCourseWritable(Course course) {
        if (Boolean.TRUE.equals(course.getIsArchived())) {
            throw new CourseArchivedReadOnlyException("Archived courses are read-only for module changes");
        }
    }

    private void validateReorderRequest(List<AssignmentModule> modules, List<Long> orderedModuleIds) {
        if (orderedModuleIds == null || orderedModuleIds.isEmpty()) {
            throw new ValidationException("Module order must not be empty", "VALIDATION_ERROR");
        }

        Set<Long> expectedIds = modules.stream().map(AssignmentModule::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> providedIds = new HashSet<>(orderedModuleIds);

        if (providedIds.size() != orderedModuleIds.size()) {
            throw new ValidationException("Module order contains duplicate IDs", "VALIDATION_ERROR");
        }
        if (!expectedIds.equals(providedIds)) {
            throw new ValidationException("Module order must include exactly all course modules", "VALIDATION_ERROR");
        }
    }

    private void evictCourseModulesCache(Long courseId) {
        Cache cache = cacheManager.getCache(CacheNames.CACHE_COURSE_MODULES);
        if (cache != null) {
            cache.evict(courseId);
        }
    }

    private void evictAssignmentCache(Long assignmentId) {
        Cache cache = cacheManager.getCache(CacheNames.CACHE_ASSIGNMENT);
        if (cache != null) {
            cache.evict(assignmentId);
        }
    }
}
