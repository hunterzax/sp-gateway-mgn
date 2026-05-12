package com.ptt.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating an existing user.
 * Only exposes fields that are safe for the client to modify,
 * preventing mass-assignment of internal fields like id, failedAttemptCount, lockoutTime.
 * (CWE-915 remediation)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserUpdateDTO {
    private String name;
    private String surname;
    private String email;
    private String password;
    private String role;  // "ADMIN" or "USER"
    private Boolean active;
}
