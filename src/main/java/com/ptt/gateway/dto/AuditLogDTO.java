package com.ptt.gateway.dto;

import com.ptt.gateway.model.AuditLog;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AuditLogDTO {

    private UUID id;
    private String eventName;
    private LocalDateTime createdAt;
    private String severity;
    private String statusType;
    private String createdBy;
    private String source;
    private String clientIp;
    private String action;
    private String description;
    private String accessMethod;
    private String logType;
    private String metadata; // raw JSON string; frontend can parse

    public static AuditLogDTO from(AuditLog log) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(log.getId());
        dto.setEventName(log.getEventName());
        dto.setCreatedAt(log.getCreatedAt());
        dto.setSeverity(log.getSeverity());
        dto.setStatusType(log.getStatusType());
        dto.setCreatedBy(log.getCreatedBy());
        dto.setSource(log.getSource());
        dto.setClientIp(log.getClientIp());
        dto.setAction(log.getAction());
        dto.setDescription(log.getDescription());
        dto.setAccessMethod(log.getAccessMethod());
        dto.setLogType(log.getLogType());
        dto.setMetadata(log.getMetadata());
        return dto;
    }
}
