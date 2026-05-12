package com.ptt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.model.PasswordPolicy;
import com.ptt.gateway.repository.PasswordPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private final PasswordPolicyRepository policyRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public PasswordPolicy getActivePolicy() {
        return policyRepository.findAll().stream().findFirst().orElseGet(() -> {
            PasswordPolicy defaultPolicy = new PasswordPolicy();
            defaultPolicy.setMinLength(8);
            defaultPolicy.setPasswordAge(60);
            defaultPolicy.setMinHistory(5);
            defaultPolicy.setRequireUppercase(true);
            defaultPolicy.setRequireLowercase(true);
            defaultPolicy.setRequireDigits(true);
            defaultPolicy.setRequireSpecial(true);
            return defaultPolicy;
        });
    }

    /**
     * Replaces the active password policy and records an audit log entry
     * with a before/after diff in the metadata field (JSON format).
     */
    @Transactional
    public PasswordPolicy updatePolicy(PasswordPolicy newPolicy) {
        PasswordPolicy before = policyRepository.findAll().stream().findFirst().orElse(null);

        // Persist: delete existing then save new
        policyRepository.deleteAll();
        PasswordPolicy saved = policyRepository.save(newPolicy);

        // Audit
        try {
            String username = resolveUsername();
            String metadata = buildMetadata(before, saved);
            String description = username + " updated Password Policy";

            auditLogService.log(AuditLog.builder()
                    .action("updatePasswordPolicy")
                    .description(description)
                    .logType("USER_MANAGEMENT")
                    .severity("INFO")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Action")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not build password policy audit log: {}", e.getMessage());
        }

        return saved;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    /**
     * Builds a JSON string showing only the fields that actually changed,
     * with their before and after values.
     *
     * <pre>
     * {
     *   "minLength":    { "before": 8,  "after": 12 },
     *   "passwordAge":  { "before": 60, "after": 90 }
     * }
     * </pre>
     */
    private String buildMetadata(PasswordPolicy before, PasswordPolicy after) throws Exception {
        Map<String, Object> changes = new LinkedHashMap<>();

        addIfChanged(changes, "minLength",
                before != null ? before.getMinLength() : null, after.getMinLength());
        addIfChanged(changes, "passwordAge",
                before != null ? before.getPasswordAge() : null, after.getPasswordAge());
        addIfChanged(changes, "minHistory",
                before != null ? before.getMinHistory() : null, after.getMinHistory());
        addIfChanged(changes, "requireUppercase",
                before != null ? before.getRequireUppercase() : null, after.getRequireUppercase());
        addIfChanged(changes, "requireLowercase",
                before != null ? before.getRequireLowercase() : null, after.getRequireLowercase());
        addIfChanged(changes, "requireDigits",
                before != null ? before.getRequireDigits() : null, after.getRequireDigits());
        addIfChanged(changes, "requireSpecial",
                before != null ? before.getRequireSpecial() : null, after.getRequireSpecial());

        // If nothing changed, still record the full new state as a snapshot
        if (changes.isEmpty()) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("minLength", after.getMinLength());
            snapshot.put("passwordAge", after.getPasswordAge());
            snapshot.put("minHistory", after.getMinHistory());
            snapshot.put("requireUppercase", after.getRequireUppercase());
            snapshot.put("requireLowercase", after.getRequireLowercase());
            snapshot.put("requireDigits", after.getRequireDigits());
            snapshot.put("requireSpecial", after.getRequireSpecial());
            return objectMapper.writeValueAsString(Map.of("snapshot", snapshot));
        }

        return objectMapper.writeValueAsString(changes);
    }

    private void addIfChanged(Map<String, Object> changes, String key, Object before, Object after) {
        boolean same = (before == null && after == null)
                || (before != null && before.equals(after));
        if (!same) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("before", before);
            diff.put("after", after);
            changes.put(key, diff);
        }
    }

    public List<String> validatePassword(String password) {
        List<String> errors = new ArrayList<>();
        PasswordPolicy policy = getActivePolicy();

        if (policy.getMinLength() != null && password.length() < policy.getMinLength()) {
            errors.add("Password must be at least " + policy.getMinLength() + " characters long.");
        }

        if (Boolean.TRUE.equals(policy.getRequireUppercase())) {
            long count = password.chars().filter(Character::isUpperCase).count();
            if (count < 1)
                errors.add("Password must contain at least one uppercase letter.");
        }

        if (Boolean.TRUE.equals(policy.getRequireLowercase())) {
            long count = password.chars().filter(Character::isLowerCase).count();
            if (count < 1)
                errors.add("Password must contain at least one lowercase letter.");
        }

        if (Boolean.TRUE.equals(policy.getRequireDigits())) {
            long count = password.chars().filter(Character::isDigit).count();
            if (count < 1)
                errors.add("Password must contain at least one digit.");
        }

        if (Boolean.TRUE.equals(policy.getRequireSpecial())) {
            long count = password.chars().filter(ch -> !Character.isLetterOrDigit(ch)).count();
            if (count < 1)
                errors.add("Password must contain at least one special character.");
        }

        return errors;
    }
}
