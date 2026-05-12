package com.ptt.gateway.service;

import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Fire-and-forget async log save.
     * Uses REQUIRES_NEW so audit log commits even if caller's transaction rolls
     * back.
     */
    @Async("auditLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("[AuditLog] Failed to save audit log: action={}, createdBy={}, error={}",
                    auditLog.getAction(), auditLog.getCreatedBy(), e.getMessage(), e);
        }
    }

    /**
     * Convenience builder for saving an audit log entry.
     */
    @Async("auditLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String description, String logType,
            String severity, String source, String createdBy,
            String clientIp, String accessMethod, String statusType,
            String metadata) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .description(description)
                    .logType(logType)
                    .severity(severity)
                    .source(source)
                    .createdBy(createdBy)
                    .clientIp(clientIp)
                    .accessMethod(accessMethod)
                    .statusType(statusType)
                    .metadata(metadata)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("[AuditLog] Failed to save audit log: action={}, createdBy={}, error={}",
                    action, createdBy, e.getMessage(), e);
        }
    }

    /**
     * Paginated retrieval with optional filters.
     * Uses JPA Specification (Criteria API) to avoid PostgreSQL null-parameter
     * type inference issues that occur with JPQL IS NULL checks.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getLogs(String severity, String source, String logType,
            String createdBy, LocalDateTime from, LocalDateTime to,
            String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());

        org.springframework.data.jpa.domain.Specification<AuditLog> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            if (severity != null && !severity.isBlank())
                predicates.add(cb.equal(root.get("severity"), severity));
            if (source != null && !source.isBlank())
                predicates.add(cb.equal(root.get("source"), source));
            if (logType != null && !logType.isBlank())
                predicates.add(cb.equal(root.get("logType"), logType));
            if (createdBy != null && !createdBy.isBlank())
                predicates.add(cb.equal(root.get("createdBy"), createdBy));
            if (from != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("action")), pattern),
                        cb.like(cb.lower(root.get("createdBy")), pattern)));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable);
    }
}
