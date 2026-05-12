package com.ptt.gateway.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for building before/after JSON diff strings used in audit log
 * metadata.
 *
 * <p>
 * Usage:
 * 
 * <pre>
 * AuditMetadataBuilder builder = new AuditMetadataBuilder(objectMapper);
 * builder.add("fieldName", oldValue, newValue);
 * String json = builder.build();
 * </pre>
 */
public class AuditMetadataBuilder {

    private final ObjectMapper objectMapper;
    private final Map<String, Object> changes = new LinkedHashMap<>();

    public AuditMetadataBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Records a field change only if the before and after values differ.
     */
    public AuditMetadataBuilder add(String field, Object before, Object after) {
        boolean same = (before == null && after == null)
                || (before != null && before.equals(after));
        if (!same) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("before", before);
            diff.put("after", after);
            changes.put(field, diff);
        }
        return this;
    }

    /**
     * Returns true if no fields were changed.
     */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * Serialises the change map to a JSON string.
     * Returns {@code null} if nothing changed.
     */
    public String build() {
        if (changes.isEmpty())
            return null;
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception e) {
            return "{\"error\":\"Unable to serialize metadata\"}";
        }
    }
}
