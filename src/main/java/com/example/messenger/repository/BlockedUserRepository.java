package com.example.messenger.repository;

import com.example.messenger.model.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
    List<BlockedUser> findByBlockerUsername(String blockerUsername);
    boolean existsByBlockerUsernameAndBlockedUsername(String blockerUsername, String blockedUsername);
    Optional<BlockedUser> findByBlockerUsernameAndBlockedUsername(String blockerUsername, String blockedUsername);
    void deleteByBlockerUsernameAndBlockedUsername(String blockerUsername, String blockedUsername);
}
