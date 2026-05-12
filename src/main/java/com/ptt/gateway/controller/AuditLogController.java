package com.ptt.gateway.controller;

import com.ptt.gateway.dto.ApiResponse;
import com.ptt.gateway.dto.AuditLogDTO;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.service.AuditLogService;
import static com.ptt.gateway.util.LogSanitizer.sanitize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * GET /api/audit-logs
     * ?logType=ACTION&severity=ERROR&source=BACKEND&createdBy=user@x.com
     * &from=2026-01-01T00:00:00&to=2026-12-31T23:59:59&page=0&size=20
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AuditLogDTO>>> getLogs(
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/audit-logs logType={}, severity={}, source={}, keyword={}", sanitize(logType), sanitize(severity), sanitize(source),
                sanitize(keyword));
        Page<AuditLog> logs = auditLogService.getLogs(severity, source, logType, createdBy, from, to, keyword, page,
                size);
        Page<AuditLogDTO> dtoPage = logs.map(AuditLogDTO::from);
        return ResponseEntity.ok(ApiResponse.success(dtoPage, "Audit logs retrieved successfully"));
    }
}
