package com.reviewflow.service;

import com.reviewflow.model.entity.AuditLog;
import com.reviewflow.model.entity.User;
import com.reviewflow.repository.AuditLogRepository;
import com.reviewflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void log(Long actorId, String action, String targetType, Long targetId, String metadata, String ip) {
        User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;
        AuditLog log = AuditLog.builder()
                .actor(actor)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .metadata(metadata)
                .ipAddress(ip)
                .createdAt(Instant.now())
                .build();
        auditLogRepository.save(log);
    }

    public Page<AuditLog> findFiltered(Long actorId, String action, String targetType, Instant dateFrom, Instant dateTo, Pageable pageable) {
        return auditLogRepository.findFiltered(actorId, action, targetType, dateFrom, dateTo, pageable);
    }
}
