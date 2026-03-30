package com.reviewflow.repository;

import com.reviewflow.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE (:actorId IS NULL OR a.actor.id = :actorId) "
            + "AND (:action IS NULL OR a.action = :action) AND (:targetType IS NULL OR a.targetType = :targetType) "
            + "AND (:from IS NULL OR a.createdAt >= :from) AND (:to IS NULL OR a.createdAt <= :to) ORDER BY a.createdAt DESC")
    Page<AuditLog> findFiltered(@Param("actorId") Long actorId, @Param("action") String action,
            @Param("targetType") String targetType, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);

    /**
     * PRD-09: Find security events by action type (for system dashboard)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.action IN :actions ORDER BY a.createdAt DESC")
    Page<AuditLog> findByActionInOrderByCreatedAtDesc(@Param("actions") List<String> actions, Pageable pageable);
}
