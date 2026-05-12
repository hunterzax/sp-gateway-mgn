package com.ptt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptt.gateway.dto.UserCreateDTO;
import com.ptt.gateway.dto.UserUpdateDTO;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.model.PasswordPolicy;
import com.ptt.gateway.model.User;
import com.ptt.gateway.model.UserPasswordHistory;
import com.ptt.gateway.util.AuditMetadataBuilder;
import com.ptt.gateway.util.Audited;
import com.ptt.gateway.repository.UserPasswordHistoryRepository;
import com.ptt.gateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserPasswordHistoryRepository historyRepository;
    private final PasswordPolicyService policyService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<User> smartSearch(String query, int page, int size) {
        int pageZeroBased = (page > 0) ? page - 1 : 0;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest
                .of(pageZeroBased, size);
        String queryPattern = (query != null && !query.isEmpty()) ? "%" + query.toLowerCase() + "%" : null;
        return userRepository.smartSearch(queryPattern, pageable);
    }

    public com.ptt.gateway.dto.PaginationDTO createPagination(org.springframework.data.domain.Page<?> page) {
        return com.ptt.gateway.dto.PaginationDTO.builder()
                .page(page.getNumber() + 1)
                .totalPage(page.getTotalPages())
                .limit(page.getSize())
                .totalData(page.getTotalElements())
                .build();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByAccountId(String accountId) {
        return userRepository.findByAccountId(accountId);
    }

    @Audited(action = "createUser", descriptionTemplate = "{user} adds new user : {accountId} ({email})", resultFields = {
            "accountId", "email" }, logType = "USER_MANAGEMENT", statusType = "Action")
    @Transactional
    public User createUser(User user) {
        if (user.getEmail() != null && userRepository.existsByEmailAndType(user.getEmail(), user.getType())) {
            throw new RuntimeException("Email already exists for " + user.getType() + " account");
        }

        // Capture cleartext, encode immediately, and clear from entity
        // to prevent cleartext from being persisted to disk (CWE-313 remediation)
        String rawPassword = user.getPassword();
        String encodedPassword = null;
        if (rawPassword != null) {
            List<String> errors = policyService.validatePassword(rawPassword);
            if (!errors.isEmpty()) {
                throw new RuntimeException("Password Policy Validation Failed: " + String.join(", ", errors));
            }
            encodedPassword = passwordEncoder.encode(rawPassword);
            user.setPassword(encodedPassword);
        }

        User savedUser = userRepository.save(user);

        // Save history using the already-encoded password
        if (encodedPassword != null) {
            savePasswordHistory(savedUser, encodedPassword);
        }

        return savedUser;
    }

    /**
     * DTO-based overload that prevents mass-assignment (CWE-915).
     * Only the fields exposed on UserCreateDTO are copied to the entity.
     */
    @Transactional
    public User createUser(UserCreateDTO dto) {
        User user = new User();
        user.setAccountId(dto.getAccountId());
        user.setName(dto.getName());
        user.setSurname(dto.getSurname());
        user.setEmail(dto.getEmail());
        user.setPassword(dto.getPassword());
        if (dto.getType() != null) {
            user.setType(User.UserType.valueOf(dto.getType()));
        }
        if (dto.getRole() != null) {
            user.setRole(User.Role.valueOf(dto.getRole()));
        }
        return createUser(user);
    }

    @Transactional
    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        // Snapshot before
        String beforeName = user.getName();
        String beforeSurname = user.getSurname();
        String beforeEmail = user.getEmail();
        Boolean beforeActive = user.getActive();
        Object beforeRole = user.getRole();
        Object beforeType = user.getType();

        if (userDetails.getName() != null)
            user.setName(userDetails.getName());
        if (userDetails.getSurname() != null)
            user.setSurname(userDetails.getSurname());

        if (userDetails.getEmail() != null) {
            User.UserType targetType = userDetails.getType() != null ? userDetails.getType() : user.getType();
            if (userRepository.existsByEmailAndTypeAndIdNot(userDetails.getEmail(), targetType, id)) {
                throw new RuntimeException("Email already exists for this account type: " + userDetails.getEmail());
            }
            user.setEmail(userDetails.getEmail());
        }

        if (userDetails.getActive() != null)
            user.setActive(userDetails.getActive());
        if (userDetails.getRole() != null)
            user.setRole(userDetails.getRole());
        if (userDetails.getType() != null)
            user.setType(userDetails.getType());

        // Capture cleartext in a local variable, validate, encode immediately,
        // and only persist the hash — never store cleartext on disk (CWE-313 remediation)
        boolean passwordChanged = false;
        String rawPassword = userDetails.getPassword();
        if (rawPassword != null && !rawPassword.isEmpty()) {
            List<String> policyErrors = policyService.validatePassword(rawPassword);
            if (!policyErrors.isEmpty()) {
                throw new RuntimeException("Password Policy Validation Failed: " + String.join(", ", policyErrors));
            }
            checkPasswordHistory(user, rawPassword);
            String encodedPassword = passwordEncoder.encode(rawPassword);
            user.setPassword(encodedPassword);
            savePasswordHistory(user, encodedPassword);
            passwordChanged = true;
        }

        User saved = userRepository.save(user);

        // Audit log with before/after metadata
        try {
            String username = resolveUsername();
            AuditMetadataBuilder builder = new AuditMetadataBuilder(objectMapper)
                    .add("name", beforeName, saved.getName())
                    .add("surname", beforeSurname, saved.getSurname())
                    .add("email", beforeEmail, saved.getEmail())
                    .add("active", beforeActive, saved.getActive())
                    .add("role", beforeRole, saved.getRole())
                    .add("type", beforeType, saved.getType());
            if (passwordChanged) {
                builder.add("passwordChanged", false, true);
            }
            auditLogService.log(AuditLog.builder()
                    .action("updateUser")
                    .description(username + " edits User : " + saved.getEmail())
                    .logType("USER_MANAGEMENT")
                    .severity("INFO")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Action")
                    .metadata(builder.build())
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not build user audit metadata: {}", e.getMessage());
        }

        return saved;
    }

    /**
     * DTO-based overload that prevents mass-assignment (CWE-915).
     * Only the fields exposed on UserUpdateDTO are applied to the entity.
     */
    @Transactional
    public User updateUser(Long id, UserUpdateDTO dto) {
        User userDetails = new User();
        userDetails.setName(dto.getName());
        userDetails.setSurname(dto.getSurname());
        userDetails.setEmail(dto.getEmail());
        userDetails.setPassword(dto.getPassword());
        userDetails.setActive(dto.getActive());
        if (dto.getRole() != null) {
            userDetails.setRole(User.Role.valueOf(dto.getRole()));
        }
        return updateUser(id, userDetails);
    }

    private void checkPasswordHistory(User user, String newPassword) {
        PasswordPolicy policy = policyService.getActivePolicy();
        if (policy.getMinHistory() == null || policy.getMinHistory() <= 0)
            return;

        List<UserPasswordHistory> histories = historyRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        // Check only the last 'minHistory' passwords
        int checkCount = Math.min(histories.size(), policy.getMinHistory());

        for (int i = 0; i < checkCount; i++) {
            if (passwordEncoder.matches(newPassword, histories.get(i).getPassword())) {
                throw new RuntimeException(
                        "Password Policy Validation Failed: Password has been used recently (History limit: "
                                + policy.getMinHistory() + ")");
            }
        }
    }

    private void savePasswordHistory(User user, String encodedPassword) {
        UserPasswordHistory history = new UserPasswordHistory();
        history.setUser(user);
        history.setPassword(encodedPassword);
        historyRepository.save(history);

        prunePasswordHistory(user);
    }

    private void prunePasswordHistory(User user) {
        PasswordPolicy policy = policyService.getActivePolicy();
        if (policy.getMinHistory() == null)
            return;

        List<UserPasswordHistory> histories = historyRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (histories.size() > policy.getMinHistory()) {
            // Histories are ordered newest first. We want to keep the top 'minHistory'.
            // The ones AFTER index 'minHistory' - 1 should be deleted.
            // Example: limit 5. Size 6. Indexes 0-4 keep. Index 5 delete.

            List<UserPasswordHistory> toDelete = histories.subList(policy.getMinHistory(), histories.size());
            historyRepository.deleteAll(toDelete);
        }
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        // Snapshot before delete
        String accountId = user.getAccountId();
        String name = user.getName();
        String surname = user.getSurname();
        String email = user.getEmail();
        Object role = user.getRole();
        Object type = user.getType();
        Boolean active = user.getActive();

        historyRepository.deleteByUserId(id);
        userRepository.deleteById(id);

        // Audit log with pre-delete snapshot
        try {
            String username = resolveUsername();
            java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("userId", id);
            meta.put("accountId", accountId);
            meta.put("name", name);
            meta.put("surname", surname);
            meta.put("email", email);
            meta.put("role", role);
            meta.put("type", type);
            meta.put("active", active);
            String metadata = objectMapper.writeValueAsString(meta);
            auditLogService.log(AuditLog.builder()
                    .action("deleteUser")
                    .description(username + " deletes User : " + id)
                    .logType("USER_MANAGEMENT")
                    .severity("WARNING")
                    .source("BACKEND")
                    .createdBy(username)
                    .statusType("Delete")
                    .metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("[AuditLog] Could not log deleteUser: {}", e.getMessage());
        }
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }
}
