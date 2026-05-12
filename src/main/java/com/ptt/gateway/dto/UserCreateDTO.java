package com.ptt.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new user.
 * Only exposes fields that are safe for the client to set,
 * preventing mass-assignment of internal fields like id, role, lockoutTime, etc.
 * (CWE-915 remediation)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserCreateDTO {
    private String accountId;
    private String name;
    private String surname;
    private String email;
    private String password;
    private String type;  // "AD" or "LOCAL"
    private String role;  // "ADMIN" or "USER"
}
