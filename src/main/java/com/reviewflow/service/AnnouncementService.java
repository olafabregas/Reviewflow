package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import com.reviewflow.event.AnnouncementPublishedEvent;
import com.reviewflow.exception.*;
import com.reviewflow.model.entity.*;
import com.reviewflow.model.enums.AnnouncementTarget;
import com.reviewflow.model.enums.RecipientType;
import com.reviewflow.repository.AnnouncementRepository;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * AnnouncementService — manages lifecycle of announcements (create, publish,
 * delete, read). Enforces role-based permissions per PRD-04 roles matrix. Fires
 * AnnouncementPublishedEvent on publish for async notification + email
 * dispatch.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final HashidService hashidService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Create a course announcement (draft). Only instructors of the course can
     * create; students and non-instructor admins cannot.
     *
     * @param courseId course hashid
     * @param instructorId actor user ID
     * @param title announcement title (max 255 chars)
     * @param body announcement body (any length)
     * @return created Announcement entity (not yet published)
     * @throws ResourceNotFoundException if course not found
     * @throws CourseNotOwnedException if actor is not an instructor of this
     * course
     */
    @Transactional
    public Announcement createCourseAnnouncement(String courseId, Long instructorId, String title, String body) {
        Long decodedCourseId = hashidService.decodeOrThrow(courseId);

        // Verify instructor owns this course
        if (!courseInstructorRepository.existsByCourse_IdAndUser_Id(decodedCourseId, instructorId)) {
            auditService.log(instructorId, "ANNOUNCEMENT_CREATE_DENIED", "Announcement", null,
                    "Instructor tried to create announcement for unowned course: " + courseId, null);
            throw new CourseNotOwnedException("You do not teach this course");
        }

        Course course = new Course();
        course.setId(decodedCourseId);

        User creator = userRepository.findById(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", instructorId));

        Announcement announcement = Announcement.builder()
                .course(course)
                .createdBy(creator)
                .title(title)
                .body(body)
                .target(AnnouncementTarget.COURSE)
                .recipientType(null)
                .isPublished(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        announcement = announcementRepository.save(announcement);

        auditService.log(instructorId, "ANNOUNCEMENT_CREATED", "Announcement", announcement.getId(),
                "Created course announcement: " + title, null);

        return announcement;
    }

    /**
     * Create a platform-wide announcement (draft). Only admins can create
     * platform announcements.
     *
     * @param adminId actor user ID (must be ADMIN role)
     * @param title announcement title
     * @param body announcement body
     * @param recipientType recipient group (ALL_STUDENTS, ALL_INSTRUCTORS,
     * ALL_USERS)
     * @return created Announcement entity (not yet published)
     * @throws AccessDeniedException if actor is not an admin
     */
    @Transactional
    public Announcement createPlatformAnnouncement(Long adminId, String title, String body, RecipientType recipientType) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", adminId));

        if (!UserRole.ADMIN.equals(admin.getRole())) {
            auditService.log(adminId, "ANNOUNCEMENT_CREATE_PLATFORM_DENIED", "Announcement", null,
                    "Non-admin tried to create platform announcement", null);
            throw new AccessDeniedException("Only admins can create platform announcements");
        }

        Announcement announcement = Announcement.builder()
                .course(null)
                .createdBy(admin)
                .title(title)
                .body(body)
                .target(AnnouncementTarget.PLATFORM)
                .recipientType(recipientType)
                .isPublished(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        announcement = announcementRepository.save(announcement);

        auditService.log(adminId, "ANNOUNCEMENT_CREATED_PLATFORM", "Announcement", announcement.getId(),
                "Created platform announcement: " + title + " for " + recipientType, null);

        return announcement;
    }

    /**
     * Publish a draft announcement. Only the creator or an admin can publish.
     * Publishing is a one-way transition. Fires AnnouncementPublishedEvent for
     * async notification and email dispatch.
     *
     * @param announcementId announcement long ID
     * @param actorId actor user ID
     * @return published Announcement entity
     * @throws AnnouncementNotFoundException if announcement not found or not
     * owned by actor
     * @throws AlreadyPublishedException if announcement is already published
     */
    @Transactional
    public Announcement publish(Long announcementId, Long actorId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));

        // Verify actor is owner or admin
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorId));

        boolean isOwner = announcement.getCreatedBy().getId().equals(actorId);
        boolean isAdmin = UserRole.ADMIN.equals(actor.getRole());

        if (!isOwner && !isAdmin) {
            auditService.log(actorId, "ANNOUNCEMENT_PUBLISH_DENIED", "Announcement", announcementId,
                    "Non-owner tried to publish announcement", null);
            throw new AccessDeniedException("Only the creator or an admin can publish this announcement");
        }

        // Verify not already published
        if (announcement.getIsPublished()) {
            throw new AlreadyPublishedException("This announcement is already published");
        }

        // Publish: set flag and timestamp
        announcement.setIsPublished(true);
        announcement.setPublishedAt(Instant.now());
        announcement = announcementRepository.save(announcement);

        // Fire event for async notification + email dispatch
        AnnouncementPublishedEvent event = AnnouncementPublishedEvent.from(announcement, getDisplayName(actor));
        eventPublisher.publishEvent(event);

        auditService.log(actorId, "ANNOUNCEMENT_PUBLISHED", "Announcement", announcementId,
                "Published announcement: " + announcement.getTitle(), null);

        return announcement;
    }

    /**
     * Delete an announcement (hard delete, no soft delete). Only the creator or
     * an admin can delete.
     *
     * @param announcementId announcement long ID
     * @param actorId actor user ID
     * @throws AnnouncementNotFoundException if announcement not found
     * @throws AccessDeniedException if actor is not owner or admin
     */
    @Transactional
    public void delete(Long announcementId, Long actorId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));

        // Verify actor is owner or admin
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorId));

        boolean isOwner = announcement.getCreatedBy().getId().equals(actorId);
        boolean isAdmin = UserRole.ADMIN.equals(actor.getRole());

        if (!isOwner && !isAdmin) {
            auditService.log(actorId, "ANNOUNCEMENT_DELETE_DENIED", "Announcement", announcementId,
                    "Non-owner tried to delete announcement", null);
            throw new AccessDeniedException("Only the creator or an admin can delete this announcement");
        }

        announcementRepository.deleteById(announcementId);

        auditService.log(actorId, "ANNOUNCEMENT_DELETED", "Announcement", announcementId,
                "Deleted announcement: " + announcement.getTitle(), null);
    }

    /**
     * Get published announcements for a course (paginated, by publish date
     * DESC). Only students/instructors enrolled in or teaching the course can
     * view.
     *
     * @param courseId course long ID (decoded)
     * @param userId actor user ID
     * @param pageable pagination spec
     * @return Page of published Announcements
     * @throws AccessDeniedException if actor is not enrolled/teaching the
     * course
     */
    @Transactional(readOnly = true)
    public Page<Announcement> getByCourse(Long courseId, Long userId, Pageable pageable) {
        // Verify enrollment or instructor status
        boolean isEnrolled = courseEnrollmentRepository.existsByCourse_IdAndUser_Id(courseId, userId);
        boolean isInstructor = courseInstructorRepository.existsByCourse_IdAndUser_Id(courseId, userId);

        if (!isEnrolled && !isInstructor) {
            throw new AccessDeniedException("You are not enrolled in or teaching this course");
        }

        return announcementRepository.findByCourse_IdAndIsPublishedTrue(courseId, pageable);
    }

    /**
     * Get draft announcements for a course (instructor/admin view for editing).
     *
     * @param courseId course long ID (decoded)
     * @param userId actor user ID
     * @param pageable pagination spec
     * @return Page of draft Announcements created by user
     */
    @Transactional(readOnly = true)
    public Page<Announcement> getDraftsByCourse(Long courseId, Long userId, Pageable pageable) {
        return announcementRepository.findCourseAnnouncementDrafts(courseId, userId, pageable);
    }

    /**
     * Helper to get display name from user (firstName + lastName or email).
     */
    private String getDisplayName(User user) {
        if (user.getFirstName() != null && user.getLastName() != null) {
            return user.getFirstName() + " " + user.getLastName();
        }
        if (user.getFirstName() != null) {
            return user.getFirstName();
        }
        return user.getEmail();
    }
}
