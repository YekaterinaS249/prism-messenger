package com.example.messenger.repository;

import com.example.messenger.model.Role;
import com.example.messenger.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByCreatedAtGreaterThanEqual(Instant from);
    long countByRole(Role role);

    /** Admin users list: paged, optionally filtered by username/display name (case-insensitive substring). */
    @Query("SELECT u FROM User u WHERE (:search IS NULL OR :search = '' " +
            "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchForAdmin(@Param("search") String search, Pageable pageable);
}
