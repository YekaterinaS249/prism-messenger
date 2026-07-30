package com.example.messenger.controller;

import com.example.messenger.dto.AuthRequest;
import com.example.messenger.dto.AuthResponse;
import com.example.messenger.dto.RegisterRequest;
import com.example.messenger.model.User;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.security.JwtUtil;
import com.example.messenger.service.PasswordResetService;
import com.example.messenger.service.PlatformSettingsService;
import com.example.messenger.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PlatformSettingsService platformSettingsService;
    private final PasswordResetService passwordResetService;

    public AuthController(UserService userService, UserRepository userRepository,
                           AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                           PlatformSettingsService platformSettingsService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.platformSettingsService = platformSettingsService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (!platformSettingsService.isRegistrationEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "Регистрация новых пользователей временно отключена"));
        }
        try {
            User user = userService.register(request);
            String token = jwtUtil.generateToken(user.getUsername());
            return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getDisplayName(), user.getAvatarUrl(), user.getStatus()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        // The "Логин" field accepts either a username or an email — resolve it to the actual
        // account first so authentication and the JWT subject always use the canonical username.
        String identifier = request.getUsername().trim();
        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
        if (user.isLocked()) {
            return ResponseEntity.status(429).body(Map.of("error",
                    "Слишком много неудачных попыток входа. Попробуйте снова через " + UserService.getLockoutMinutes() + " минут"));
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    user.getUsername(), UserService.normalizePassword(request.getPassword())));
        } catch (Exception e) {
            boolean justLocked = userService.recordFailedLogin(user);
            if (justLocked) {
                return ResponseEntity.status(429).body(Map.of("error",
                        "Слишком много неудачных попыток входа. Аккаунт временно заблокирован на " + UserService.getLockoutMinutes() + " минут"));
            }
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
        userService.recordSuccessfulLogin(user);
        if (user.isBanned()) {
            return ResponseEntity.status(403).body(Map.of("error", "Аккаунт заблокирован администратором"));
        }
        if (platformSettingsService.isMaintenanceMode() && !user.isAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Технические работы, доступ временно только для администраторов"));
        }
        String token = jwtUtil.generateToken(user.getUsername());
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getDisplayName(), user.getAvatarUrl(), user.getStatus()));
    }

    public static class ForgotPasswordRequest {
        @NotBlank
        public String usernameOrEmail;
    }

    /**
     * Always returns the same generic response whether or not the account (or its email) exists —
     * this endpoint must not be usable to enumerate registered usernames/emails.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.usernameOrEmail);
        return ResponseEntity.ok(Map.of("ok", true,
                "message", "Если такой аккаунт с привязанным email существует, на него отправлена ссылка для сброса пароля"));
    }

    public static class ResetPasswordRequest {
        @NotBlank
        public String token;
        @NotBlank
        public String newPassword;
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        if (request.newPassword.length() < 8 || request.newPassword.length() > 64) {
            return ResponseEntity.badRequest().body(Map.of("error", "Пароль должен быть от 8 до 64 символов"));
        }
        try {
            passwordResetService.resetPassword(request.token, request.newPassword);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
