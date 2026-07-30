package com.example.messenger.model;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt hash

    @Column(nullable = false, length = 100)
    private String displayName;

    private String avatarUrl;

    @Column(length = 140)
    private String status = "Привет! Я использую Messenger";

    private boolean online = false;

    private Instant lastSeen = Instant.now();

    // columnDefinition supplies a DB-level DEFAULT so Hibernate's ddl-auto=update can ALTER the
    // already-populated app_user table without violating NOT NULL on existing rows.
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean showOnlineStatus = true;

    // Base64-encoded raw ECDH (P-256) public key, uploaded by the client for end-to-end
    // encryption. The server never sees the matching private key or any decrypted content.
    @Column(length = 255)
    private String publicKey;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isAdmin = false;

    // Source of truth for authorization (USER/MODERATOR/ADMIN/SUPER_ADMIN). isAdmin above is kept
    // in sync automatically (true for ADMIN and SUPER_ADMIN) so existing "admin-only" checks that
    // read isAdmin keep working unchanged; new, more granular checks should use role/isSuperAdmin.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'USER'")
    private Role role = Role.USER;

    // Fixed-vocabulary presence status shown under the display name (e.g. "ON_CALL", "BUSY").
    // Null means no status set. The client owns the list of valid codes and their labels/icons.
    @Column(name = "presence_status", length = 20)
    private String presenceStatus;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean banned = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean verified = false;

    // Optional; set by the user in profile settings. Only used so "forgot password" has
    // somewhere to send a reset link — never shown to other users.
    @Column(length = 150)
    private String email;

    // Brute-force protection: consecutive failed login attempts, and (once the threshold is
    // hit) the timestamp until which login is refused regardless of password correctness.
    // Reset to 0/null on any successful login. See UserService.recordFailedLogin/recordSuccessfulLogin.
    @Column(name = "failed_login_attempts", nullable = false, columnDefinition = "integer default 0")
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public User() {}

    public User(String username, String password, String displayName) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public boolean isShowOnlineStatus() { return showOnlineStatus; }
    public void setShowOnlineStatus(boolean showOnlineStatus) { this.showOnlineStatus = showOnlineStatus; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }

    public Role getRole() { return role; }
    public void setRole(Role role) {
        this.role = role;
        this.isAdmin = (role == Role.ADMIN || role == Role.SUPER_ADMIN);
    }
    public boolean isSuperAdmin() { return role == Role.SUPER_ADMIN; }
    public boolean isModeratorOrAbove() { return role != Role.USER; }

    public String getPresenceStatus() { return presenceStatus; }
    public void setPresenceStatus(String presenceStatus) { this.presenceStatus = presenceStatus; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isBanned() { return banned; }
    public void setBanned(boolean banned) { this.banned = banned; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }

    public boolean isLocked() { return lockedUntil != null && Instant.now().isBefore(lockedUntil); }
}
