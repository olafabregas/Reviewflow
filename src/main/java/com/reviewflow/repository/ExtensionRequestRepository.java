package com.reviewflow.repository;

import com.reviewflow.model.entity.ExtensionRequest;
import com.reviewflow.model.enums.ExtensionRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface ExtensionRequestRepository extends JpaRepository<ExtensionRequest, Long> {

    Optional<ExtensionRequest> findTopByAssignment_IdAndStudent_IdAndStatusInOrderByCreatedAtDesc(
            Long assignmentId,
            Long studentId,
            Collection<ExtensionRequestStatus> statuses
    );

    Optional<ExtensionRequest> findTopByAssignment_IdAndTeam_IdAndStatusInOrderByCreatedAtDesc(
            Long assignmentId,
            Long teamId,
            Collection<ExtensionRequestStatus> statuses
    );

    Optional<ExtensionRequest> findTopByAssignment_IdAndStudent_IdAndStatusOrderByRespondedAtDesc(
            Long assignmentId,
            Long studentId,
            ExtensionRequestStatus status
    );

    Optional<ExtensionRequest> findTopByAssignment_IdAndTeam_IdAndStatusOrderByRespondedAtDesc(
            Long assignmentId,
            Long teamId,
            ExtensionRequestStatus status
    );

    Page<ExtensionRequest> findByAssignment_IdOrderByCreatedAtDesc(Long assignmentId, Pageable pageable);

    Page<ExtensionRequest> findByStudent_IdOrderByCreatedAtDesc(Long studentId, Pageable pageable);
}
