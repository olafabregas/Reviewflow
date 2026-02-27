package com.reviewflow.repository;

import com.reviewflow.model.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    Optional<Evaluation> findBySubmission_Id(Long submissionId);

    List<Evaluation> findByInstructor_IdAndSubmission_Assignment_Course_Id(Long instructorId, Long courseId);
}
