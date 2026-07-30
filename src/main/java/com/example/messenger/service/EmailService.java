package com.example.messenger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around JavaMailSender. Sending is gated by app.mail.enabled (default false) so
 * the app keeps working locally before real SMTP credentials (e.g. Mailtrap) are configured —
 * in that case the email is just logged instead of sent, rather than the request failing.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean enabled;

    public EmailService(JavaMailSender mailSender,
                         @Value("${app.mail.from}") String fromAddress,
                         @Value("${app.mail.enabled}") boolean enabled) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.enabled = enabled;
    }

    public void sendPasswordResetEmail(String to, String resetLink, int expiryMinutes) {
        String subject = "Восстановление пароля — Prism";
        String body = "Кто-то (надеемся, что вы) запросил сброс пароля в Prism.\n\n"
                + "Ссылка для установки нового пароля (действует " + expiryMinutes + " минут):\n"
                + resetLink + "\n\n"
                + "Если вы не запрашивали это письмо, просто проигнорируйте его — пароль не изменится.";

        if (!enabled) {
            log.info("[MAIL DISABLED] Would send password reset email to {}: {}", to, resetLink);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send password reset email to {}", to, e);
        }
    }
}
