package com.reviewflow.repository;

import com.reviewflow.model.entity.ExtensionRequest;
import com.reviewflow.model.enums.ExtensionRequestStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtensionRequestRepository extends JpaRepository<ExtensionRequest, Long> {

  Optional<ExtensionRequest> findTopByAssignmentIdAndStudentIdAndStatusInOrderByCreatedAtDesc(
      Long assignmentId, Long studentId, Collection<ExtensionRequestStatus> statuses);

  Optional<ExtensionRequest> findTopByAssignmentIdAndTeamIdAndStatusInOrderByCreatedAtDesc(
      Long assignmentId, Long teamId, Collection<ExtensionRequestStatus> statuses);

  Optional<ExtensionRequest> findTopByAssignmentIdAndStudentIdAndStatusOrderByRespondedAtDesc(
      Long assignmentId, Long studentId, ExtensionRequestStatus status);

  Optional<ExtensionRequest> findTopByAssignmentIdAndTeamIdAndStatusOrderByRespondedAtDesc(
      Long assignmentId, Long teamId, ExtensionRequestStatus status);

  Page<ExtensionRequest> findByAssignmentIdOrderByCreatedAtDesc(
      Long assignmentId, Pageable pageable);

  Page<ExtensionRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId, Pageable pageable);
}
