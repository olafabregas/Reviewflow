package com.reviewflow.repository;

import com.reviewflow.model.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
    // TODO [STYLE-AGENT]: fix structural violation
public interface AuditRepository extends JpaRepository<AuditLog, Long> {}
