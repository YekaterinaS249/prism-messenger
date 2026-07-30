package com.example.messenger.controller;

import com.example.messenger.dto.CreateNewsPostRequest;
import com.example.messenger.service.AnalyticsService;
import com.example.messenger.service.AuditLogService;
import com.example.messenger.service.NewsService;
import com.example.messenger.service.PlatformSettingsService;
import com.example.messenger.service.ReportService;
import com.example.messenger.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin-only dashboard/analytics/moderation endpoints. Basic user list/delete lives in UserController's /admin/* routes. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AnalyticsService analyticsService;
    private final ReportService reportService;
    private final PlatformSettingsService platformSettingsService;
    private final AuditLogService auditLogService;
    private final NewsService newsService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    public AdminController(AnalyticsService analyticsService, ReportService reportService,
                            PlatformSettingsService platformSettingsService, AuditLogService auditLogService,
                            NewsService newsService, UserService userService, SimpMessagingTemplate messagingTemplate) {
        this.analyticsService = analyticsService;
        this.reportService = reportService;
        this.platformSettingsService = platformSettingsService;
        this.auditLogService = auditLogService;
        this.newsService = newsService;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(Authentication authentication) {
        try {
            return ResponseEntity.ok(analyticsService.dashboard(authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics/trend")
    public ResponseEntity<?> trend(Authentication authentication, @RequestParam(defaultValue = "14") int days) {
        try {
            return ResponseEntity.ok(analyticsService.trend(authentication.getName(), days));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics/top-groups")
    public ResponseEntity<?> topGroups(Authentication authentication,
                                        @RequestParam(defaultValue = "7") int days,
                                        @RequestParam(defaultValue = "5") int limit) {
        try {
            return ResponseEntity.ok(analyticsService.topGroups(authentication.getName(), days, limit));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ---------- Жалобы ----------

    @GetMapping("/reports")
    public ResponseEntity<?> listReports(Authentication authentication) {
        try {
            return ResponseEntity.ok(reportService.listOpen(authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/reports/{id}/dismiss")
    public ResponseEntity<?> dismissReport(Authentication authentication, @PathVariable Long id) {
        try {
            reportService.dismiss(authentication.getName(), id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/reports/{id}/ban")
    public ResponseEntity<?> actionReportBan(Authentication authentication, @PathVariable Long id) {
        try {
            reportService.actionBan(authentication.getName(), id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ---------- Рассылка ----------

    public static class BroadcastRequest {
        public String message;
    }

    /** Posts an announcement to the News feed (title prefixed so the client can badge it specially). */
    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcast(Authentication authentication, @RequestBody BroadcastRequest request) {
        if (!userService.isAdmin(authentication.getName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin only"));
        }
        if (request.message == null || request.message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Текст объявления не может быть пустым"));
        }
        CreateNewsPostRequest post = new CreateNewsPostRequest();
        post.setTitle("Объявление");
        post.setContent(request.message.trim());
        newsService.create(authentication.getName(), post);
        auditLogService.record(authentication.getName(), "BROADCAST", null,
                request.message.trim().substring(0, Math.min(100, request.message.trim().length())));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ---------- Настройки платформы ----------

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(Authentication authentication) {
        try {
            return ResponseEntity.ok(platformSettingsService.get(authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    public static class UpdateSettingsRequest {
        public Boolean registrationEnabled;
        public Boolean groupCreationEnabled;
        public Boolean maintenanceMode;
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(Authentication authentication, @RequestBody UpdateSettingsRequest request) {
        try {
            return ResponseEntity.ok(platformSettingsService.update(authentication.getName(),
                    request.registrationEnabled, request.groupCreationEnabled, request.maintenanceMode));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ---------- Журнал действий ----------

    @GetMapping("/audit-log")
    public ResponseEntity<?> auditLog(Authentication authentication,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        try {
            return ResponseEntity.ok(auditLogService.recent(authentication.getName(), page, size));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ---------- Сессии ----------

    /**
     * Asks a specific user's connected client(s) to log themselves out. There's no server-side
     * WebSocket "kick" API in Spring's simple broker, so this relays a request to the user's own
     * private queue; the client listens for it and calls its local logout. Combined with banning
     * (which rejects the JWT on their very next request), this covers "remove someone's access now".
     */
    @PutMapping("/users/{username}/force-logout")
    public ResponseEntity<?> forceLogout(Authentication authentication, @PathVariable String username) {
        if (!userService.isAdmin(authentication.getName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin only"));
        }
        messagingTemplate.convertAndSendToUser(username, "/queue/admin", Map.of("action", "FORCE_LOGOUT"));
        auditLogService.record(authentication.getName(), "FORCE_LOGOUT", username, null);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
