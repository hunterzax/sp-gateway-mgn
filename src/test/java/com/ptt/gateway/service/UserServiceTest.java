package com.ptt.gateway.service;

import com.ptt.gateway.model.PasswordPolicy;
import com.ptt.gateway.model.User;
import com.ptt.gateway.model.UserPasswordHistory;
import com.ptt.gateway.repository.PasswordPolicyRepository;
import com.ptt.gateway.repository.UserPasswordHistoryRepository;
import com.ptt.gateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    // Test-only fixture values — not real credentials (CWE-259 remediation)
    private static final String TEST_PASSWORD_EXISTING = "test_password_fixture_1"; // NOSONAR
    private static final String TEST_PASSWORD_NEW = "test_password_fixture_2"; // NOSONAR

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPasswordHistoryRepository historyRepository;

    @Mock
    private PasswordPolicyService policyService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user;
    private PasswordPolicy policy;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setAccountId("testuser");
        user.setPassword("oldHash");

        policy = new PasswordPolicy();
        policy.setMinLength(8);
        policy.setMinHistory(3); // History limit of 3
    }

    @Test
    void updateUser_ShouldFail_WhenPasswordInHistory() {
        String newPassword = TEST_PASSWORD_EXISTING;
        String newPasswordHash = "hash123";

        // Mock Policy — guard against null (CWE-476, issue #87587)
        when(policyService.getActivePolicy()).thenReturn(policy);
        assertNotNull(policyService.getActivePolicy(), "Policy must not be null");
        when(policyService.validatePassword(any())).thenReturn(java.util.Collections.emptyList());

        // Mock Finding User
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Mock History (Latest 3 passwords)
        UserPasswordHistory h1 = new UserPasswordHistory();
        h1.setPassword("hash1");
        UserPasswordHistory h2 = new UserPasswordHistory();
        h2.setPassword("hash2");
        UserPasswordHistory h3 = new UserPasswordHistory();
        h3.setPassword("hash123"); // Same as new!
        List<UserPasswordHistory> history = List.of(h1, h2, h3);

        when(historyRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(history);

        // Mock Encoder Matches
        when(passwordEncoder.matches(TEST_PASSWORD_EXISTING, "hash1")).thenReturn(false);
        when(passwordEncoder.matches(TEST_PASSWORD_EXISTING, "hash2")).thenReturn(false);
        when(passwordEncoder.matches(TEST_PASSWORD_EXISTING, "hash123")).thenReturn(true); // Match found!

        User updateDetails = new User();
        updateDetails.setPassword(newPassword);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            userService.updateUser(1L, updateDetails);
        });

        assertTrue(exception.getMessage().contains("Password has been used recently"));
    }

    @Test
    void updateUser_ShouldPruneHistory_WhenLimitExceeded() {
        String newPassword = TEST_PASSWORD_NEW;

        // Mock Policy — guard against null (CWE-476, issue #87672)
        when(policyService.getActivePolicy()).thenReturn(policy);
        assertNotNull(policyService.getActivePolicy(), "Policy must not be null");
        when(policyService.validatePassword(any())).thenReturn(java.util.Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(newPassword)).thenReturn("newHash");

        // Mock History (Existing 3, Limit is 3. Adding one more should trigger prune)
        UserPasswordHistory h1 = new UserPasswordHistory();
        h1.setPassword("hash1");
        UserPasswordHistory h2 = new UserPasswordHistory();
        h2.setPassword("hash2");
        UserPasswordHistory h3 = new UserPasswordHistory();
        h3.setPassword("hash3");
        List<UserPasswordHistory> history = new ArrayList<>(List.of(h1, h2, h3));

        // Mock repository calls
        // After save in savePasswordHistory, it calls findByUserIdOrderByCreatedAtDesc
        // again to prune
        // Let's assume we return a list of 4 items now
        UserPasswordHistory hNew = new UserPasswordHistory();
        hNew.setPassword("newHash");
        List<UserPasswordHistory> historyAfterSave = new ArrayList<>(List.of(hNew, h1, h2, h3)); // 4 items

        // We need to handle multiple calls to findBy... 1st for check, 2nd for prune
        doReturn(history).doReturn(historyAfterSave).when(historyRepository).findByUserIdOrderByCreatedAtDesc(1L);

        User updateDetails = new User();
        updateDetails.setPassword(newPassword);

        userService.updateUser(1L, updateDetails);

        // Verify save was called
        verify(historyRepository, times(1)).save(any(UserPasswordHistory.class));

        // Verify delete was called on the surplus (sublist)
        verify(historyRepository, times(1)).deleteAll(anyList());
    }
}
