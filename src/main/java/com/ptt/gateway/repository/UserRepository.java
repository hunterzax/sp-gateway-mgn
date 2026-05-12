package com.ptt.gateway.repository;

import com.ptt.gateway.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
        Optional<User> findByAccountId(String accountId);

        Optional<User> findByEmail(String email);

        boolean existsByAccountId(String accountId);

        @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE " +
                        "(:query IS NULL OR " +
                        "LOWER(u.accountId) LIKE :query OR " +
                        "LOWER(u.name) LIKE :query OR " +
                        "LOWER(u.surname) LIKE :query OR " +
                        "LOWER(u.email) LIKE :query)")
        org.springframework.data.domain.Page<User> smartSearch(
                        @org.springframework.data.repository.query.Param("query") String query,
                        org.springframework.data.domain.Pageable pageable);

        boolean existsByEmailAndTypeAndIdNot(String email, User.UserType type, Long id);

        boolean existsByEmailAndType(String email, User.UserType type);
}
