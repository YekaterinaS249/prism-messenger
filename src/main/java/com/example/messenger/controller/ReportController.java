package com.example.messenger.controller;

import com.example.messenger.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Any authenticated user can report another user; reviewing reports is admin-only (see AdminController). */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    public static class SubmitReportRequest {
        public String targetUsername;
        public String reason;
        public String messageSnippet;
    }

    @PostMapping
    public ResponseEntity<?> submit(Authentication authentication, @RequestBody SubmitReportRequest request) {
        try {
            reportService.submit(authentication.getName(), request.targetUsername, request.reason, request.messageSnippet);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
