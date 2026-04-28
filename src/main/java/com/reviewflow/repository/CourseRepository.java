package com.reviewflow.repository;

import com.reviewflow.model.entity.Course;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

  @Query("SELECT c FROM Course c JOIN c.instructors i WHERE i.user.id = :userId")
  List<Course> findByInstructorId(@Param("userId") Long userId);

  @Query("SELECT c FROM Course c JOIN c.enrollments e WHERE e.user.id = :userId")
  List<Course> findByEnrolledUserId(@Param("userId") Long userId);

  @Query(
      "SELECT c FROM Course c JOIN c.instructors i WHERE i.user.id = :userId AND (:archived IS NULL"
          + " OR c.isArchived = :archived)")
  Page<Course> findByInstructorIdPaged(
      @Param("userId") Long userId, @Param("archived") Boolean archived, Pageable pageable);

  @Query(
      "SELECT c FROM Course c JOIN c.enrollments e WHERE e.user.id = :userId AND (:archived IS NULL"
          + " OR c.isArchived = :archived)")
  Page<Course> findByEnrolledUserIdPaged(
      @Param("userId") Long userId, @Param("archived") Boolean archived, Pageable pageable);

  @Query("SELECT c FROM Course c WHERE :archived IS NULL OR c.isArchived = :archived")
  Page<Course> findAllFiltered(@Param("archived") Boolean archived, Pageable pageable);

  Optional<Course> findByCode(String code);

  boolean existsByCode(String code);

  boolean existsByCodeAndIdNot(String code, Long id);

  long countByIsArchivedFalse();

  long countByIsArchivedTrue();
}
