package com.reviewflow.course.repository;

import com.reviewflow.shared.domain.CourseInstructor;
import com.reviewflow.shared.domain.CourseInstructor.CourseInstructorId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseInstructorRepository
    extends JpaRepository<CourseInstructor, CourseInstructorId> {

  Optional<CourseInstructor> findByCourseIdAndUserId(Long courseId, Long userId);

  List<CourseInstructor> findByCourseId(Long courseId);

  boolean existsByCourseIdAndUserId(Long courseId, Long userId);

  void deleteByCourseIdAndUserId(Long courseId, Long userId);

  long countByCourseId(Long courseId);

  long countByUserId(Long userId);
}
