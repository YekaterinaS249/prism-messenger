package com.example.messenger.model;

import javax.persistence.*;
import java.time.Instant;

/** One row = blockerUsername has blocked blockedUsername (one-directional). */
@Entity
@Table(name = "blocked_user", uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_username", "blocked_username"}))
public class BlockedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_username", nullable = false, length = 50)
    private String blockerUsername;

    @Column(name = "blocked_username", nullable = false, length = 50)
    private String blockedUsername;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public BlockedUser() {}

    public BlockedUser(String blockerUsername, String blockedUsername) {
        this.blockerUsername = blockerUsername;
        this.blockedUsername = blockedUsername;
    }

    public Long getId() { return id; }
    public String getBlockerUsername() { return blockerUsername; }
    public void setBlockerUsername(String blockerUsername) { this.blockerUsername = blockerUsername; }
    public String getBlockedUsername() { return blockedUsername; }
    public void setBlockedUsername(String blockedUsername) { this.blockedUsername = blockedUsername; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
