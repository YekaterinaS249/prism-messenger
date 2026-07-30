package com.example.messenger.controller;

import com.example.messenger.dto.UpdateProfileRequest;
import com.example.messenger.dto.UserDto;
import com.example.messenger.model.Role;
import com.example.messenger.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/contacts")
    public List<UserDto> contacts(Authentication authentication) {
        return userService.listContacts(authentication.getName());
    }

    /** Current user's own profile, for the personal cabinet / settings screen. */
    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        return userService.getMe(authentication.getName());
    }

    /** Update display name and/or status text from the personal cabinet. */
    @PutMapping("/me")
    public UserDto updateMe(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(authentication.getName(), request);
    }

    /**
     * Publishes this browser's ECDH public key so contacts can derive an end-to-end-encryption
     * key for direct chats. The server stores and relays it only — it never sees private keys
     * or decrypted message content.
     */
    @PutMapping("/me/public-key")
    public UserDto updatePublicKey(Authentication authentication, @RequestBody Map<String, String> body) {
        return userService.updatePublicKey(authentication.getName(), body.get("publicKey"));
    }

    /** Upload/replace the profile photo shown to other users. */
    @PostMapping("/me/avatar")
    public ResponseEntity<?> updateAvatar(Authentication authentication, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Avatar must be an image"));
        }

        Path dir = Paths.get(uploadsDir);
        Files.createDirectories(dir);

        String original = Path.of(file.getOriginalFilename() == null ? "avatar" : file.getOriginalFilename()).getFileName().toString();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String storedName = "avatar-" + UUID.randomUUID() + ext;

        Path target = dir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        UserDto updated = userService.updateAvatar(authentication.getName(), "/media/" + storedName);
        return ResponseEntity.ok(updated);
    }

    /** Admin-only user management panel: paged user list, optionally filtered by username/display name. */
    @GetMapping("/admin/all")
    public ResponseEntity<?> adminListUsers(Authentication authentication,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String search) {
        try {
            return ResponseEntity.ok(userService.listAllForAdmin(authentication.getName(), page, size, search));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin-only: delete a user account. */
    @DeleteMapping("/admin/{username}")
    public ResponseEntity<?> adminDeleteUser(Authentication authentication, @PathVariable String username) {
        try {
            userService.deleteUser(authentication.getName(), username);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin-only: ban a user account (blocks login, force-logs-out any active session). */
    @PutMapping("/admin/{username}/ban")
    public ResponseEntity<?> adminBanUser(Authentication authentication, @PathVariable String username,
                                           @RequestParam(required = false) String reason) {
        try {
            userService.banUser(authentication.getName(), username, reason);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin-only: unban a user account. */
    @DeleteMapping("/admin/{username}/ban")
    public ResponseEntity<?> adminUnbanUser(Authentication authentication, @PathVariable String username) {
        try {
            userService.unbanUser(authentication.getName(), username);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin-only: ban multiple accounts at once (bulk action from the users table). */
    @PutMapping("/admin/ban-bulk")
    public ResponseEntity<?> adminBanUsersBulk(Authentication authentication, @RequestBody List<String> usernames) {
        try {
            userService.banUsers(authentication.getName(), usernames);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /** Admin-only: toggle the "verified" badge on a user account. */
    @PutMapping("/admin/{username}/verified")
    public ResponseEntity<?> adminSetVerified(Authentication authentication, @PathVariable String username,
                                               @RequestParam boolean value) {
        try {
            userService.setVerified(authentication.getName(), username, value);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Super-admin-only: change a user's site-wide role (USER/MODERATOR/ADMIN/SUPER_ADMIN). */
    @PutMapping("/admin/{username}/role")
    public ResponseEntity<?> adminSetRole(Authentication authentication, @PathVariable String username,
                                           @RequestParam String value) {
        Role newRole;
        try {
            newRole = Role.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown role: " + value));
        }
        try {
            userService.setRole(authentication.getName(), username, newRole);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me/blocked")
    public List<String> listBlocked(Authentication authentication) {
        return userService.listBlocked(authentication.getName());
    }

    @PutMapping("/{username}/block")
    public ResponseEntity<?> block(Authentication authentication, @PathVariable String username) {
        try {
            userService.blockUser(authentication.getName(), username);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{username}/block")
    public ResponseEntity<?> unblock(Authentication authentication, @PathVariable String username) {
        userService.unblockUser(authentication.getName(), username);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/me/stickers")
    public List<String> myStickers(Authentication authentication) {
        return userService.listStickers(authentication.getName());
    }

    /** Upload a personal sticker image; stored the same way as avatars, added to the caller's set. */
    @PostMapping("/me/stickers")
    public ResponseEntity<?> uploadSticker(Authentication authentication, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sticker must be an image"));
        }

        Path dir = Paths.get(uploadsDir);
        Files.createDirectories(dir);

        String original = Path.of(file.getOriginalFilename() == null ? "sticker" : file.getOriginalFilename()).getFileName().toString();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String storedName = "sticker-" + UUID.randomUUID() + ext;

        Path target = dir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        userService.addSticker(authentication.getName(), "/media/" + storedName);
        return ResponseEntity.ok(userService.listStickers(authentication.getName()));
    }
}
