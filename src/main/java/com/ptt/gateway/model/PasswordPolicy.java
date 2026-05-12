package com.ptt.gateway.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "password_policies")
@Data
@NoArgsConstructor
public class PasswordPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "password_age")
    private Integer passwordAge; // Maximum age in days

    @Column(name = "min_length")
    private Integer minLength; // Minimum character length

    @Column(name = "min_history")
    private Integer minHistory; // Number of historic passwords to keep/check

    @Column(name = "require_uppercase")
    private Boolean requireUppercase; // Require at least one uppercase letter

    @Column(name = "require_lowercase")
    private Boolean requireLowercase; // Require at least one lowercase letter

    @Column(name = "require_digits")
    private Boolean requireDigits; // Require at least one digit

    @Column(name = "require_special")
    private Boolean requireSpecial; // Require at least one special character
}
