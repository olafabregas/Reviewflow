package com.reviewflow.repository;

import com.reviewflow.model.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByCourse_Id(Long courseId);

    List<Assignment> findByCourse_IdAndIsPublishedTrue(Long courseId);
}
