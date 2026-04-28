package com.reviewflow.repository;

import com.reviewflow.model.entity.Announcement;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

  /**
   * Find published announcements for a course, ordered by published_at DESC. Used by
   * students/instructors to list course announcements.
   */
  Page<Announcement> findByCourseIdAndIsPublishedTrue(Long courseId, Pageable pageable);

  /**
   * Find draft announcements for a course by creator (owner check). Used by instructor to edit/view
   * draft announcements.
   */
  @Query(
      "SELECT a FROM Announcement a WHERE a.course.id = :courseId AND a.createdBy.id = :creatorId"
          + " AND a.isPublished = false")
  Page<Announcement> findCourseAnnouncementDrafts(
      @Param("courseId") Long courseId, @Param("creatorId") Long creatorId, Pageable pageable);

  /**
   * Find announcement by ID with ownership check. Used by publish/delete endpoints to verify actor
   * owns the announcement.
   */
  Optional<Announcement> findByIdAndCreatedById(Long id, Long creatorId);

  /** Find any announcement by ID (admin/system use). */
  Optional<Announcement> findById(Long id);

  /** Count published platform announcements. Used for audit/reporting. */
  long countByTargetAndIsPublishedTrue(com.reviewflow.model.enums.AnnouncementTarget target);
}
