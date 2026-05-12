package com.ptt.gateway.controller;

import com.ptt.gateway.model.PasswordPolicy;
import com.ptt.gateway.service.PasswordPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/password-policy")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PasswordPolicyController {

    private final PasswordPolicyService passwordPolicyService;

    @GetMapping
    public ResponseEntity<PasswordPolicy> getPolicy() {
        return ResponseEntity.ok(passwordPolicyService.getActivePolicy());
    }

    @PostMapping
    public ResponseEntity<PasswordPolicy> updatePolicy(@RequestBody PasswordPolicy policy) {
        return ResponseEntity.ok(passwordPolicyService.updatePolicy(policy));
    }
}
