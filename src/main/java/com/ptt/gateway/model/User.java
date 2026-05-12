package com.ptt.gateway.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", unique = true, nullable = false)
    private String accountId; // Login ID

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String surname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType type; // AD or LOCAL

    @Column
    private String email;

    @Column(name = "failed_attempt_count")
    private Integer failedAttemptCount = 0;

    @Column(name = "lockout_time")
    private java.time.LocalDateTime lockoutTime;

    @Column(nullable = false)
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; // ADMIN or USER

    @Column(nullable = true) // Nullable for AD users potentially
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password; // Hashed password

    public enum UserType {
        AD, LOCAL
    }

    public enum Role {
        ADMIN, USER
    }
}
