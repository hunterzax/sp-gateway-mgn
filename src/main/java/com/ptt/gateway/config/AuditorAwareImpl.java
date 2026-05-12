package com.ptt.gateway.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementation of AuditorAware to provide the current authenticated user
 * for JPA Auditing (created_by, updated_by fields)
 */
@Component("auditorProvider")
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        // If principal is UserDetails, extract username (account ID)
        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            return Optional.of(username);
        }

        // If principal is a String (username directly)
        if (principal instanceof String) {
            return Optional.of((String) principal);
        }

        return Optional.empty();
    }
}
