package com.reviewflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewflow.dto.AuditLogDto;
import com.reviewflow.model.entity.AuditLog;
import com.reviewflow.model.entity.User;
import com.reviewflow.repository.AuditLogRepository;
import com.reviewflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

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

    /**
     * PRD-09: Overloaded log method that accepts Map-based metadata
     */
    @Transactional
    public void log(String action, String targetType, Long targetId, Map<String, Object> metadata, String ip) {
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.error("Failed to serialize audit metadata to JSON; falling back to toString()", e);
            metadataJson = metadata.toString();
        }
        AuditLog entry = AuditLog.builder()
                .actor(null)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .metadata(metadataJson)
                .ipAddress(ip)
                .createdAt(Instant.now())
                .build();
        auditLogRepository.save(entry);
    }

    public Page<AuditLog> findFiltered(Long actorId, String action, String targetType, Instant dateFrom, Instant dateTo, Pageable pageable) {
        return auditLogRepository.findFiltered(actorId, action, targetType, dateFrom, dateTo, pageable);
    }

    /**
     * PRD-09: Get security events only (filtered action types)
     */
    public List<AuditLogDto> getSecurityEvents(int limit) {
        List<String> securityActions = Arrays.asList(
                "USER_LOGIN_FAILED",
                "RATE_LIMIT_HIT",
                "FILE_BLOCKED"
        );

        Page<AuditLog> events = auditLogRepository.findByActionInOrderByCreatedAtDesc(
                securityActions,
                PageRequest.of(0, Math.min(limit, 100))
        );

        return events.getContent().stream()
                .map(log -> AuditLogDto.builder()
                .id(log.getId())
                .actorId(log.getActor() != null ? log.getActor().getId() : null)
                .actorEmail(log.getActor() != null ? log.getActor().getEmail() : null)
                .action(log.getAction())
                .entityType(log.getTargetType())
                .entityId(log.getTargetId())
                .metadata(log.getMetadata())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build())
                .collect(Collectors.toList());
    }
}
