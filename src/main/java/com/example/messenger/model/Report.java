package com.example.messenger.model;

import javax.persistence.*;
import java.time.Instant;

/** A user-submitted report reviewed by admins in the moderation queue. */
@Entity
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_username", nullable = false, length = 50)
    private String reporterUsername;

    @Column(name = "target_username", nullable = false, length = 50)
    private String targetUsername;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "message_snippet", length = 300)
    private String messageSnippet;

    @Column(nullable = false, length = 20)
    private String status = "OPEN"; // OPEN, DISMISSED, ACTIONED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_by", length = 50)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Report() {}

    public Report(String reporterUsername, String targetUsername, String reason, String messageSnippet) {
        this.reporterUsername = reporterUsername;
        this.targetUsername = targetUsername;
        this.reason = reason;
        this.messageSnippet = messageSnippet;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReporterUsername() { return reporterUsername; }
    public void setReporterUsername(String reporterUsername) { this.reporterUsername = reporterUsername; }

    public String getTargetUsername() { return targetUsername; }
    public void setTargetUsername(String targetUsername) { this.targetUsername = targetUsername; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getMessageSnippet() { return messageSnippet; }
    public void setMessageSnippet(String messageSnippet) { this.messageSnippet = messageSnippet; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
