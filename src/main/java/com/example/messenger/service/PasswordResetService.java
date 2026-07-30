package com.example.messenger.service;

import com.example.messenger.model.PasswordResetToken;
import com.example.messenger.model.User;
import com.example.messenger.repository.PasswordResetTokenRepository;
import com.example.messenger.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * "Forgot password" flow: request a reset link by username or email, then redeem the token for
 * a new password. Deliberately never reveals whether a given username/email exists — every
 * request() call looks the same to the caller, whether or not an account (or email) was found,
 * so the endpoint can't be used to enumerate registered usernames.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final int tokenExpiryMinutes;
    private final String baseUrl;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                 EmailService emailService, PasswordEncoder passwordEncoder,
                                 @Value("${app.password-reset.token-expiry-minutes}") int tokenExpiryMinutes,
                                 @Value("${app.password-reset.base-url}") String baseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.tokenExpiryMinutes = tokenExpiryMinutes;
        this.baseUrl = baseUrl;
    }

    /** Looks up by username first, then by email; sends a reset link if a matching, non-banned account has an email on file. */
    @Transactional
    public void requestReset(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) return;
        String query = usernameOrEmail.trim();
        Optional<User> found = userRepository.findByUsername(query);
        if (found.isEmpty()) {
            found = userRepository.findByEmailIgnoreCase(query);
        }
        if (found.isEmpty()) {
            log.info("Password reset requested for unknown username/email");
            return;
        }
        User user = found.get();
        if (user.isBanned() || user.getEmail() == null || user.getEmail().isBlank()) {
            // Same silent no-op as "not found" — the caller sees an identical response either way.
            return;
        }

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(tokenExpiryMinutes, ChronoUnit.MINUTES);
        tokenRepository.save(new PasswordResetToken(user.getUsername(), token, expiresAt));

        String resetLink = baseUrl + "/?resetToken=" + token;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink, tokenExpiryMinutes);
    }

    /** Redeems a token: validates it, sets the new password, and marks the token (and any other outstanding ones) used. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Ссылка недействительна");
        }
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Ссылка недействительна или уже использована"));
        if (!resetToken.isValid()) {
            throw new IllegalArgumentException("Срок действия ссылки истёк — запросите новую");
        }
        User user = userRepository.findByUsername(resetToken.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        if (user.isBanned()) {
            throw new IllegalArgumentException("Аккаунт заблокирован администратором");
        }

        user.setPassword(passwordEncoder.encode(UserService.normalizePassword(newPassword)));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // Invalidate any other outstanding, still-valid tokens for this user — the old links
        // (e.g. from an earlier request that the person then ignored) shouldn't keep working.
        for (PasswordResetToken other : tokenRepository.findByUsernameAndUsedFalse(user.getUsername())) {
            other.setUsed(true);
            tokenRepository.save(other);
        }
    }
}
