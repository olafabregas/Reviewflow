package com.reviewflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.reviewflow.model.entity.AuditLog;

@Repository
public interface AuditRepository extends JpaRepository<AuditLog, Long> {
}
