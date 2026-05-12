package com.ptt.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.service.AuditLogService;
import com.ptt.gateway.util.Audited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * AOP aspect that intercepts methods annotated with {@link Audited}
 * and saves an audit log entry asynchronously.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String username = resolveUsername();
        String resource = resolveResource(joinPoint, audited.resourceParam());

        try {
            Object result = joinPoint.proceed();

            // Resolve {id} from single resultIdField (backward-compat)
            String resultId = resolveResultId(result, audited.resultIdField());

            // Resolve named {fieldName} placeholders from resultFields[]
            Map<String, String> resultFieldMap = resolveResultFields(result, audited.resultFields());

            // Success path
            String description = buildDescription(audited.descriptionTemplate(), username, resource, resultId,
                    resultFieldMap);
            auditLogService
                    .log(buildLog(audited, username, description, audited.severity(), audited.statusType(), null));

            return result;

        } catch (Throwable ex) {
            // Error path
            String errorDescription = username + " failed to " + audited.action();
            String metadata = buildErrorMetadata(ex);
            auditLogService.log(buildLog(audited, username, errorDescription, "ERROR", "Error", metadata));
            throw ex;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AuditLog buildLog(Audited audited, String username, String description,
            String severity, String statusType, String metadata) {
        return AuditLog.builder()
                .action(audited.action())
                .description(description)
                .logType(audited.logType())
                .severity(severity)
                .source(audited.source())
                .createdBy(username)
                .statusType(statusType)
                .metadata(metadata)
                .build();
    }

    private String buildDescription(String template, String username, String resource,
            String resultId, Map<String, String> resultFieldMap) {
        String desc = template
                .replace("{user}", username != null ? username : "anonymous")
                .replace("{resource}", resource != null ? resource : "")
                .replace("{id}", resultId != null ? resultId : "");
        // Replace each {fieldName} from resultFields
        for (Map.Entry<String, String> entry : resultFieldMap.entrySet()) {
            desc = desc.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return desc;
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "anonymous";
    }

    private String resolveResource(ProceedingJoinPoint joinPoint, String paramName) {
        if (paramName == null || paramName.isBlank())
            return null;

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName) && args[i] != null) {
                return args[i].toString();
            }
        }
        return null;
    }

    /**
     * Extracts the value of {@code fieldName} from the method return value via
     * reflection. Used to capture auto-generated IDs (e.g. {@code tagID}) that are
     * only available after the method executes.
     */
    private String resolveResultId(Object result, String fieldName) {
        if (result == null || fieldName == null || fieldName.isBlank())
            return null;
        try {
            Field field = result.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(result);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.debug("Could not resolve resultIdField '{}' from {}: {}", fieldName,
                    result.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Extracts multiple named fields from the method return value via reflection.
     * Returns a Map of fieldName → value (as String) for use in description
     * template.
     * Missing or null fields are mapped to empty string.
     */
    private Map<String, String> resolveResultFields(Object result, String[] fieldNames) {
        Map<String, String> map = new HashMap<>();
        if (result == null || fieldNames == null || fieldNames.length == 0)
            return map;
        for (String fieldName : fieldNames) {
            try {
                Field field = result.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(result);
                map.put(fieldName, value != null ? value.toString() : "");
            } catch (Exception e) {
                log.debug("Could not resolve resultField '{}' from {}: {}", fieldName,
                        result.getClass().getSimpleName(), e.getMessage());
                map.put(fieldName, "");
            }
        }
        return map;
    }

    private String buildErrorMetadata(Throwable ex) {
        try {
            Map<String, String> meta = new HashMap<>();
            meta.put("errorType", ex.getClass().getSimpleName());
            meta.put("errorMessage", ex.getMessage());
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return "{\"errorMessage\": \"Unable to serialize error\"}";
        }
    }
}
