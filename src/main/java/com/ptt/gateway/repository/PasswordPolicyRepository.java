package com.ptt.gateway.repository;

import com.ptt.gateway.model.PasswordPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordPolicyRepository extends JpaRepository<PasswordPolicy, Long> {
    // Usually there is only one active policy, or we fetch the first one.
    // We can add a method to find the latest or default if needed.
}
