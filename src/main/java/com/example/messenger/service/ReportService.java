package com.example.messenger.service;

import com.example.messenger.dto.ReportDto;
import com.example.messenger.model.Report;
import com.example.messenger.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;

    public ReportService(ReportRepository reportRepository, UserService userService, AuditLogService auditLogService) {
        this.reportRepository = reportRepository;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    /** Any user can report another user, optionally with a short excerpt they chose to include. */
    public void submit(String reporter, String target, String reason, String messageSnippet) {
        if (reporter.equals(target)) throw new IllegalArgumentException("Cannot report yourself");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reason is required");
        String snippet = messageSnippet != null && messageSnippet.length() > 300
                ? messageSnippet.substring(0, 300) : messageSnippet;
        reportRepository.save(new Report(reporter, target, reason.trim(), snippet));
    }

    public List<ReportDto> listOpen(String requester) {
        requireModerator(requester);
        return reportRepository.findByStatusOrderByCreatedAtDesc("OPEN").stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Dismisses a report with no action taken against the reported user. */
    public void dismiss(String requester, Long reportId) {
        requireModerator(requester);
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));
        report.setStatus("DISMISSED");
        report.setResolvedBy(requester);
        report.setResolvedAt(Instant.now());
        reportRepository.save(report);
        auditLogService.record(requester, "DISMISS_REPORT", report.getTargetUsername(),
                "report #" + reportId);
    }

    /** Resolves a report by banning the reported user. */
    public void actionBan(String requester, Long reportId) {
        requireModerator(requester);
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));
        userService.banUser(requester, report.getTargetUsername(), report.getReason());
        report.setStatus("ACTIONED");
        report.setResolvedBy(requester);
        report.setResolvedAt(Instant.now());
        reportRepository.save(report);
        auditLogService.record(requester, "ACTION_REPORT_BAN", report.getTargetUsername(),
                "report #" + reportId);
    }

    private ReportDto toDto(Report r) {
        return new ReportDto(r.getId(), r.getReporterUsername(), r.getTargetUsername(), r.getReason(),
                r.getMessageSnippet(), r.getStatus(),
                DateTimeFormatter.ISO_INSTANT.format(r.getCreatedAt().atZone(ZoneOffset.UTC)));
    }

    private void requireModerator(String requester) {
        if (!userService.isModeratorOrAbove(requester)) throw new SecurityException("Moderator or above only");
    }
}
