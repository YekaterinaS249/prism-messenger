package com.example.messenger.service;

import com.example.messenger.dto.PageResponse;
import com.example.messenger.dto.RegisterRequest;
import com.example.messenger.dto.UpdateProfileRequest;
import com.example.messenger.dto.UserDto;
import com.example.messenger.model.BlockedUser;
import com.example.messenger.model.Role;
import com.example.messenger.model.User;
import com.example.messenger.model.UserSticker;
import com.example.messenger.repository.BlockedUserRepository;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.repository.UserStickerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Set<String> VALID_PRESENCE_STATUSES = Set.of(
            "ON_CALL", "BUSY", "LUNCH", "DND", "VACATION");

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PresenceService presenceService;
    private final BlockedUserRepository blockedUserRepository;
    private final UserStickerRepository userStickerRepository;
    private final AuditLogService auditLogService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, PresenceService presenceService,
                        BlockedUserRepository blockedUserRepository, UserStickerRepository userStickerRepository,
                        AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.presenceService = presenceService;
        this.blockedUserRepository = blockedUserRepository;
        this.userStickerRepository = userStickerRepository;
        this.auditLogService = auditLogService;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Этот логин уже занят");
        }
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Этот email уже используется");
        }
        User user = new User(
                request.getUsername(),
                passwordEncoder.encode(normalizePassword(request.getPassword())),
                request.getDisplayName()
        );
        user.setEmail(email);
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Two requests can pass the exists-checks above at the same time before either saves;
            // the DB's unique constraints on username/email are the real guard, this just turns the
            // resulting low-level SQL error into a friendly message instead of a raw 500.
            throw new IllegalArgumentException("Этот логин или email уже используется");
        }
    }

    public List<UserDto> listContacts(String excludeUsername) {
        return userRepository.findAll().stream()
                .filter(u -> !u.getUsername().equals(excludeUsername))
                .map(u -> toDto(u, false))
                .collect(Collectors.toList());
    }

    public UserDto getMe(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toDto(user, true);
    }

    public UserDto updateProfile(String username, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus().trim());
        }
        if (request.getEmail() != null) {
            String email = request.getEmail().trim().toLowerCase();
            if (email.isEmpty()) {
                user.setEmail(null);
            } else if (!email.equalsIgnoreCase(user.getEmail()) ) {
                if (userRepository.existsByEmailIgnoreCase(email)) {
                    throw new IllegalArgumentException("Этот email уже используется");
                }
                user.setEmail(email);
            }
        }
        if (request.getShowOnlineStatus() != null) {
            user.setShowOnlineStatus(request.getShowOnlineStatus());
        }
        if (request.getPresenceStatus() != null) {
            String ps = request.getPresenceStatus().trim();
            if (ps.isEmpty()) {
                user.setPresenceStatus(null);
            } else if (VALID_PRESENCE_STATUSES.contains(ps)) {
                user.setPresenceStatus(ps);
            }
        }
        if (request.getJobTitle() != null) {
            String jt = request.getJobTitle().trim();
            user.setJobTitle(jt.isEmpty() ? null : jt);
        }
        userRepository.save(user);
        return toDto(user, true);
    }

    /** Blocks targetUsername for the requester: their direct messages stop being delivered. */
    public void blockUser(String requester, String targetUsername) {
        if (requester.equals(targetUsername)) throw new IllegalArgumentException("Cannot block yourself");
        userRepository.findByUsername(targetUsername).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!blockedUserRepository.existsByBlockerUsernameAndBlockedUsername(requester, targetUsername)) {
            blockedUserRepository.save(new BlockedUser(requester, targetUsername));
        }
    }

    @Transactional
    public void unblockUser(String requester, String targetUsername) {
        blockedUserRepository.deleteByBlockerUsernameAndBlockedUsername(requester, targetUsername);
    }

    public List<String> listBlocked(String requester) {
        return blockedUserRepository.findByBlockerUsername(requester).stream()
                .map(BlockedUser::getBlockedUsername)
                .collect(Collectors.toList());
    }

    public boolean isBlocked(String blocker, String blocked) {
        return blockedUserRepository.existsByBlockerUsernameAndBlockedUsername(blocker, blocked);
    }

    /** Adds a custom sticker (already uploaded to /media by the controller) to the owner's set. */
    public UserSticker addSticker(String owner, String url) {
        return userStickerRepository.save(new UserSticker(owner, url));
    }

    public List<String> listStickers(String owner) {
        return userStickerRepository.findByOwnerUsernameOrderByCreatedAtDesc(owner).stream()
                .map(UserSticker::getUrl)
                .collect(Collectors.toList());
    }

    public UserDto updateAvatar(String username, String avatarUrl) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        return toDto(user, true);
    }

    /**
     * Stores this user's ECDH public key (base64-encoded raw point) so contacts can derive a
     * shared end-to-end-encryption key with them. Called once per device/browser profile.
     */
    public UserDto updatePublicKey(String username, String publicKey) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPublicKey(publicKey);
        userRepository.save(user);
        return toDto(user, true);
    }

    private UserDto toDto(User u, boolean self) {
        boolean online = presenceService.isOnline(u.getUsername());
        String lastSeen = DateTimeFormatter.ISO_INSTANT.format(u.getLastSeen().atZone(ZoneOffset.UTC));
        if (!self && !u.isShowOnlineStatus()) {
            online = false;
            lastSeen = null;
        }
        return new UserDto(u.getUsername(), u.getDisplayName(), u.getAvatarUrl(), u.getStatus(), online, lastSeen,
                u.isShowOnlineStatus(), u.getPublicKey(), u.isAdmin(), u.getPresenceStatus(), u.getJobTitle(),
                u.isBanned(), u.isVerified(), u.getRole().name(), self ? u.getEmail() : null);
    }

    public boolean isAdmin(String username) {
        return userRepository.findByUsername(username).map(User::isAdmin).orElse(false);
    }

    public boolean isSuperAdmin(String username) {
        return userRepository.findByUsername(username).map(User::isSuperAdmin).orElse(false);
    }

    public boolean isModeratorOrAbove(String username) {
        return userRepository.findByUsername(username).map(User::isModeratorOrAbove).orElse(false);
    }

    /** Full user list for the admin management panel (server-side paged, optionally filtered by name/username). */
    public PageResponse<UserDto> listAllForAdmin(String requester, int page, int size, String search) {
        if (!isModeratorOrAbove(requester)) {
            throw new SecurityException("Moderator or above only");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        Page<User> result = userRepository.searchForAdmin(search == null ? "" : search.trim(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "username")));
        List<UserDto> content = result.getContent().stream()
                .map(u -> toDto(u, false))
                .collect(Collectors.toList());
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Deletes a user account. Admins cannot delete themselves or other admins from here. */
    public void deleteUser(String requester, String targetUsername) {
        if (!isAdmin(requester)) {
            throw new SecurityException("Admin only");
        }
        User target = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (target.isAdmin()) {
            throw new SecurityException("Cannot delete an admin account");
        }
        userRepository.delete(target);
        if (auditLogService != null) auditLogService.record(requester, "DELETE_USER", targetUsername, null);
    }

    /** Bans a user: they're immediately signed out (see JwtAuthFilter) and can no longer log in. */
    public void banUser(String requester, String targetUsername, String reason) {
        if (!isModeratorOrAbove(requester)) throw new SecurityException("Moderator or above only");
        User target = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (target.isAdmin()) throw new SecurityException("Cannot ban an admin account");
        target.setBanned(true);
        userRepository.save(target);
        if (auditLogService != null) {
            String details = (reason != null && !reason.isBlank()) ? reason.trim() : null;
            auditLogService.record(requester, "BAN_USER", targetUsername, details);
        }
    }

    public void unbanUser(String requester, String targetUsername) {
        if (!isModeratorOrAbove(requester)) throw new SecurityException("Moderator or above only");
        User target = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        target.setBanned(false);
        userRepository.save(target);
        if (auditLogService != null) auditLogService.record(requester, "UNBAN_USER", targetUsername, null);
    }

    /** Bans every username in the list, skipping ones that don't exist or are admins. */
    public void banUsers(String requester, List<String> targetUsernames) {
        if (!isAdmin(requester)) throw new SecurityException("Admin only");
        for (String targetUsername : targetUsernames) {
            userRepository.findByUsername(targetUsername).ifPresent(target -> {
                if (!target.isAdmin()) {
                    target.setBanned(true);
                    userRepository.save(target);
                }
            });
        }
        if (auditLogService != null) {
            auditLogService.record(requester, "BAN_USER_BULK", null, String.join(", ", targetUsernames));
        }
    }

    public void setVerified(String requester, String targetUsername, boolean verified) {
        if (!isModeratorOrAbove(requester)) throw new SecurityException("Moderator or above only");
        User target = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        target.setVerified(verified);
        userRepository.save(target);
        if (auditLogService != null) {
            auditLogService.record(requester, verified ? "VERIFY_USER" : "UNVERIFY_USER", targetUsername, null);
        }
    }

    /**
     * Changes a user's site-wide role. Only super-admins can do this (moderation staff shouldn't
     * be able to grant themselves or anyone else more power than they were given), a super-admin
     * cannot change their own role (must be done by another super-admin, so nobody can ever
     * accidentally lock everyone out), and the very last super-admin can't be demoted — the
     * platform must always retain at least one person able to manage roles and settings.
     */
    public void setRole(String requester, String targetUsername, Role newRole) {
        if (!isSuperAdmin(requester)) throw new SecurityException("Только супер-администратор может изменять роли");
        User target = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (target.getUsername().equalsIgnoreCase(requester)) {
            throw new SecurityException("Нельзя изменить собственную роль — попросите другого супер-администратора");
        }
        if (newRole != Role.USER && target.isBanned()) {
            throw new IllegalArgumentException("Нельзя выдать роль заблокированному пользователю — сначала разбаньте его");
        }
        if (target.getRole() == Role.SUPER_ADMIN && newRole != Role.SUPER_ADMIN
                && userRepository.countByRole(Role.SUPER_ADMIN) <= 1) {
            throw new SecurityException("Нельзя понизить последнего супер-администратора платформы");
        }
        Role oldRole = target.getRole();
        target.setRole(newRole);
        userRepository.save(target);
        if (auditLogService != null) {
            auditLogService.record(requester, "CHANGE_ROLE", targetUsername, oldRole + " → " + newRole);
        }
    }

    public boolean isBanned(String username) {
        return userRepository.findByUsername(username).map(User::isBanned).orElse(false);
    }

    /**
     * Brute-force protection: called after a wrong password. Once MAX_FAILED_LOGIN_ATTEMPTS is
     * reached, the account is locked for LOCKOUT_MINUTES regardless of subsequent password
     * correctness — see User.isLocked(). Returns true if this call just triggered the lockout
     * (so the caller can show a specific message on this particular attempt).
     */
    public boolean recordFailedLogin(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        boolean justLocked = false;
        if (user.getFailedLoginAttempts() >= MAX_FAILED_LOGIN_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES));
            user.setFailedLoginAttempts(0);
            justLocked = true;
        }
        userRepository.save(user);
        return justLocked;
    }

    /** Clears any failed-attempt count/lockout on a successful login. */
    public void recordSuccessfulLogin(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    public static int getLockoutMinutes() { return LOCKOUT_MINUTES; }

    /**
     * Unicode-normalizes a raw password (NFKC) before it's hashed or compared. Without this, the
     * same visible password (e.g. one containing an emoji or accented letter) can be encoded as
     * different byte sequences depending on the device/keyboard it was typed on, which would make
     * login fail even though the user typed exactly what they set at registration. Applied
     * identically at registration (here) and at login (AuthController) so both sides agree.
     */
    public static String normalizePassword(String rawPassword) {
        return rawPassword == null ? null : java.text.Normalizer.normalize(rawPassword, java.text.Normalizer.Form.NFKC);
    }
}
