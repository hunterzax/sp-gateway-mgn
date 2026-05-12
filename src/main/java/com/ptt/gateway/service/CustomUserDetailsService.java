package com.ptt.gateway.service;

import com.ptt.gateway.model.User;
import com.ptt.gateway.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Here, "username" is the accountId
        User user = userRepository.findByAccountId(username)
                .orElseGet(() -> userRepository.findByEmail(username)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found with accountId or email: " + username)));

        if (!user.getActive()) {
            throw new UsernameNotFoundException("User is inactive: " + username);
        }

        // Add Role Authority (ROLE_ADMIN or ROLE_USER)
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        return new org.springframework.security.core.userdetails.User(
                user.getAccountId(),
                user.getPassword() != null ? user.getPassword() : "", // Handle potential null for AD users if needed
                Collections.singletonList(authority));
    }
}
