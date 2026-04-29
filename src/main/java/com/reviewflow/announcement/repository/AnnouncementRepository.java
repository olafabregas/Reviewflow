package com.reviewflow.announcement.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reviewflow.shared.domain.Announcement;
import com.reviewflow.shared.domain.AnnouncementTarget;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

  Page<Announcement> findByCourseIdAndIsPublishedTrue(Long courseId, Pageable pageable);

  @Query(
      "SELECT a FROM Announcement a WHERE a.course.id = :courseId AND a.createdBy.id = :creatorId"
          + " AND a.isPublished = false")
  Page<Announcement> findCourseAnnouncementDrafts(
      @Param("courseId") Long courseId, @Param("creatorId") Long creatorId, Pageable pageable);

  Optional<Announcement> findByIdAndCreatedById(Long id, Long creatorId);

  Optional<Announcement> findById(Long id);

  long countByTargetAndIsPublishedTrue(AnnouncementTarget target);
}
