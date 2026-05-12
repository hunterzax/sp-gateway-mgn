package com.ptt.gateway.service;

import com.ptt.gateway.dto.AuthRequest;
import com.ptt.gateway.dto.AuthResponse;
import com.ptt.gateway.model.AuditLog;
import com.ptt.gateway.model.User;
import com.ptt.gateway.repository.UserRepository;
import com.ptt.gateway.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import com.ptt.gateway.exception.AuthException;

import java.util.Optional;

@Slf4j
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    private final CaaAuthService caaAuthService;

    public AuthService(AuthenticationManager authenticationManager,
            JwtUtils jwtUtils,
            AuditLogService auditLogService,
            UserRepository userRepository,
            CaaAuthService caaAuthService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.caaAuthService = caaAuthService;
    }

    public AuthResponse login(AuthRequest request, HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        String username = request.getUsername();

        // Resolve user type (AD / LOCAL) — needed regardless of auth success/failure
        Optional<User> optionalUser = userRepository.findByEmail(username);
        String accessMethod = optionalUser.map(u -> u.getType().name()).orElse("UNKNOWN");

        // Pre-Authentication Lock Check
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.getLockoutTime() != null && user.getLockoutTime().isAfter(java.time.LocalDateTime.now())) {
                // Log failed attempt due to lockout
                auditLogService.log(AuditLog.builder()
                        .action("login")
                        .logType("ACCESS_HISTORY")
                        .severity("WARNING")
                        .source("AUTH_SERVICE")
                        .createdBy(username)
                        .clientIp(clientIp)
                        .accessMethod(accessMethod)
                        .statusType("FAILED")
                        .description(username + " login failed (Account Locked for 5 minutes)")
                        .build());
                throw new AuthException("Account is locked. Please try again later.", 0, user.getLockoutTime());
            }
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));

            if (authentication.isAuthenticated()) {
                // Reset lockout counters on success
                if (optionalUser.isPresent()) {
                    User user = optionalUser.get();
                    user.setFailedAttemptCount(0);
                    user.setLockoutTime(null);
                    userRepository.save(user);
                }

                // ── Success: log ACCESS_HISTORY ───────────────────────────────
                auditLogService.log(AuditLog.builder()
                        .action("login")
                        .logType("ACCESS_HISTORY")
                        .severity("INFO")
                        .source("AUTH_SERVICE")
                        .createdBy(username)
                        .clientIp(clientIp)
                        .accessMethod(accessMethod)
                        .statusType("SUCCESS")
                        .description(username + " logged in (" + accessMethod.toLowerCase() + ")")
                        .build());

                return new AuthResponse(jwtUtils.generateToken(optionalUser.get().getAccountId()));
            } else {
                throw new RuntimeException("Invalid Access");
            }

        } catch (AuthenticationException ex) {
            int remaining = 0;
            java.time.LocalDateTime lockout = null;

            // Handle failed attempts and potential lockout
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                int attempts = user.getFailedAttemptCount() == null ? 0 : user.getFailedAttemptCount();
                attempts++;
                user.setFailedAttemptCount(attempts);
                
                if (attempts >= 3) {
                    lockout = java.time.LocalDateTime.now().plusMinutes(5);
                    user.setLockoutTime(lockout);
                    remaining = 0;
                } else {
                    remaining = 3 - attempts;
                }
                userRepository.save(user);
            }

            // ── Failed: log ACCESS_HISTORY ────────────────────────────────────
            auditLogService.log(AuditLog.builder()
                    .action("login")
                    .logType("ACCESS_HISTORY")
                    .severity("WARNING")
                    .source("AUTH_SERVICE")
                    .createdBy(username)
                    .clientIp(clientIp)
                    .accessMethod(accessMethod)
                    .statusType("FAILED")
                    .description(username + " login failed")
                    .build());

            throw new AuthException("Invalid username or password", remaining, lockout);
        }
    }

    public AuthResponse loginAd(String msalToken, HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);

        try {
            // 1. Call CA&A
            com.fasterxml.jackson.databind.JsonNode caaResponse = caaAuthService.authenticateAdUser(msalToken);

            // Null-guard: authenticateAdUser() can return null on certain error paths,
            // and calling .path() on null would be a NullPointerException (CWE-476, issue #87682)
            if (caaResponse == null) {
                throw new AuthException("Empty response from CA&A authentication service", 0, null);
            }

            // Extract user email, name
            String email = caaResponse.path("email").asText();
            
            String firstName = "AD User";
            String lastName = "";
            if (caaResponse.path("name").isArray() && caaResponse.path("name").size() > 0) {
                com.fasterxml.jackson.databind.JsonNode nameObj = caaResponse.path("name").get(0);
                firstName = nameObj.path("first").asText();
                lastName = nameObj.path("last").asText();
            } else if (caaResponse.path("name").isTextual()) {
                firstName = caaResponse.path("name").asText();
            }

            if (email == null || email.isBlank() || "null".equals(email)) {
                throw new AuthException("Cannot retrieve email from AD Token", 0, null);
            }

            // 2. Find or Create User
            Optional<User> optionalUser = userRepository.findByAccountId(email);
            User user;
            if (optionalUser.isPresent()) {
                user = optionalUser.get();
                if (user.getLockoutTime() != null && user.getLockoutTime().isAfter(java.time.LocalDateTime.now())) {
                    throw new AuthException("Account is locked.", 0, user.getLockoutTime());
                }
            } else {
                user = new User();
                user.setAccountId(email);
                user.setEmail(email);
                user.setName(firstName);
                user.setSurname(lastName);
                user.setType(User.UserType.AD);
                
                // Parse Role from CA&A response if needed, default to ADMIN for now or USER
                boolean isAdmin = false;
                if (caaResponse.path("menu").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode menuNode : caaResponse.path("menu")) {
                        if (menuNode.path("rolename").asText().toLowerCase().contains("admin")) {
                            isAdmin = true;
                            break;
                        }
                    }
                }
                user.setRole(isAdmin ? User.Role.ADMIN : User.Role.USER);
                user.setActive(true);
                userRepository.save(user);
            }

            // 3. Log Success
            auditLogService.log(AuditLog.builder()
                    .action("login")
                    .logType("ACCESS_HISTORY")
                    .severity("INFO")
                    .source("AUTH_SERVICE")
                    .createdBy(email)
                    .clientIp(clientIp)
                    .accessMethod("AD")
                    .statusType("SUCCESS")
                    .description(email + " logged in via AD (CA&A)")
                    .build());

            // 4. Reset lockout if any
            if (user.getFailedAttemptCount() != null && user.getFailedAttemptCount() > 0 || user.getLockoutTime() != null) {
                user.setFailedAttemptCount(0);
                user.setLockoutTime(null);
                userRepository.save(user);
            }

            // 5. Generate Internal JWT
            return new AuthResponse(jwtUtils.generateToken(email));

        } catch (Exception e) {
            log.error("AD Login Failed", e);
            auditLogService.log(AuditLog.builder()
                    .action("login")
                    .logType("ACCESS_HISTORY")
                    .severity("WARNING")
                    .source("AUTH_SERVICE")
                    .createdBy("UNKNOWN")
                    .clientIp(clientIp)
                    .accessMethod("AD")
                    .statusType("FAILED")
                    .description("AD login failed: " + e.getMessage())
                    .build());
            throw new AuthException("AD Login Failed: " + e.getMessage(), 0, null);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null)
            return "unknown";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
