package com.reviewflow.announcement.service;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reviewflow.announcement.event.AnnouncementPublishedEvent;
import com.reviewflow.announcement.exception.AnnouncementNotFoundException;
import com.reviewflow.announcement.repository.AnnouncementRepository;
import com.reviewflow.evaluation.exception.AlreadyPublishedException;
import com.reviewflow.course.exception.CourseNotOwnedException;
import com.reviewflow.shared.domain.Course;
import com.reviewflow.shared.domain.User;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.user.repository.UserRepository;
import com.reviewflow.admin.service.AuditService;
import com.reviewflow.shared.domain.Announcement;
import com.reviewflow.shared.domain.AnnouncementTarget;
import com.reviewflow.shared.domain.RecipientType;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.AccessDeniedException;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.shared.util.HashidService;

import lombok.RequiredArgsConstructor;

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

  @Transactional
  public Announcement createCourseAnnouncement(
      String courseId, Long instructorId, String title, String body) {
    Long decodedCourseId = hashidService.decodeOrThrow(courseId);

    if (!courseInstructorRepository.existsByCourseIdAndUserId(decodedCourseId, instructorId)) {
      auditService.log(
          instructorId,
          "ANNOUNCEMENT_CREATE_DENIED",
          "Announcement",
          null,
          "Instructor tried to create announcement for unowned course: " + courseId,
          null);
      throw new CourseNotOwnedException("You do not teach this course");
    }

    Course course = new Course();
    course.setId(decodedCourseId);

    User creator =
        userRepository
            .findById(instructorId)
            .orElseThrow(() -> new ResourceNotFoundException("User", instructorId));

    Announcement announcement =
        Announcement.builder()
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

    auditService.log(
        instructorId,
        "ANNOUNCEMENT_CREATED",
        "Announcement",
        announcement.getId(),
        "Created course announcement: " + title,
        null);

    return announcement;
  }

  @Transactional
  public Announcement createPlatformAnnouncement(
      Long adminId, String title, String body, RecipientType recipientType) {
    User admin =
        userRepository
            .findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("User", adminId));

    if (!UserRole.ADMIN.equals(admin.getRole())) {
      auditService.log(
          adminId,
          "ANNOUNCEMENT_CREATE_PLATFORM_DENIED",
          "Announcement",
          null,
          "Non-admin tried to create platform announcement",
          null);
      throw new AccessDeniedException("Only admins can create platform announcements");
    }

    Announcement announcement =
        Announcement.builder()
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

    auditService.log(
        adminId,
        "ANNOUNCEMENT_CREATED_PLATFORM",
        "Announcement",
        announcement.getId(),
        "Created platform announcement: " + title + " for " + recipientType,
        null);

    return announcement;
  }

  @Transactional
  public Announcement publish(Long announcementId, Long actorId) {
    Announcement announcement =
        announcementRepository
            .findById(announcementId)
            .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));

    User actor =
        userRepository
            .findById(actorId)
            .orElseThrow(() -> new ResourceNotFoundException("User", actorId));

    boolean isOwner = announcement.getCreatedBy().getId().equals(actorId);
    boolean isAdmin = UserRole.ADMIN.equals(actor.getRole());

    if (!isOwner && !isAdmin) {
      auditService.log(
          actorId,
          "ANNOUNCEMENT_PUBLISH_DENIED",
          "Announcement",
          announcementId,
          "Non-owner tried to publish announcement",
          null);
      throw new AccessDeniedException("Only the creator or an admin can publish this announcement");
    }

    if (announcement.getIsPublished()) {
      throw new AlreadyPublishedException("This announcement is already published");
    }

    announcement.setIsPublished(true);
    announcement.setPublishedAt(Instant.now());
    announcement = announcementRepository.save(announcement);

    eventPublisher.publishEvent(AnnouncementPublishedEvent.from(announcement, getDisplayName(actor)));

    auditService.log(
        actorId,
        "ANNOUNCEMENT_PUBLISHED",
        "Announcement",
        announcementId,
        "Published announcement: " + announcement.getTitle(),
        null);

    return announcement;
  }

  @Transactional
  public void delete(Long announcementId, Long actorId) {
    Announcement announcement =
        announcementRepository
            .findById(announcementId)
            .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));

    User actor =
        userRepository
            .findById(actorId)
            .orElseThrow(() -> new ResourceNotFoundException("User", actorId));

    boolean isOwner = announcement.getCreatedBy().getId().equals(actorId);
    boolean isAdmin = UserRole.ADMIN.equals(actor.getRole());

    if (!isOwner && !isAdmin) {
      auditService.log(
          actorId,
          "ANNOUNCEMENT_DELETE_DENIED",
          "Announcement",
          announcementId,
          "Non-owner tried to delete announcement",
          null);
      throw new AccessDeniedException("Only the creator or an admin can delete this announcement");
    }

    announcementRepository.deleteById(announcementId);

    auditService.log(
        actorId,
        "ANNOUNCEMENT_DELETED",
        "Announcement",
        announcementId,
        "Deleted announcement: " + announcement.getTitle(),
        null);
  }

  @Transactional(readOnly = true)
  public Page<Announcement> getByCourse(Long courseId, Long userId, Pageable pageable) {
    boolean isEnrolled = courseEnrollmentRepository.existsByCourseIdAndUserId(courseId, userId);
    boolean isInstructor = courseInstructorRepository.existsByCourseIdAndUserId(courseId, userId);

    if (!isEnrolled && !isInstructor) {
      throw new AccessDeniedException("You are not enrolled in or teaching this course");
    }

    return announcementRepository.findByCourseIdAndIsPublishedTrue(courseId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<Announcement> getDraftsByCourse(Long courseId, Long userId, Pageable pageable) {
    return announcementRepository.findCourseAnnouncementDrafts(courseId, userId, pageable);
  }

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
