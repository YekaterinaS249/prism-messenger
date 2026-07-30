package com.example.messenger.dto;

public class ReportDto {
    private Long id;
    private String reporterUsername;
    private String targetUsername;
    private String reason;
    private String messageSnippet;
    private String status;
    private String createdAt;

    public ReportDto(Long id, String reporterUsername, String targetUsername, String reason,
                      String messageSnippet, String status, String createdAt) {
        this.id = id;
        this.reporterUsername = reporterUsername;
        this.targetUsername = targetUsername;
        this.reason = reason;
        this.messageSnippet = messageSnippet;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getReporterUsername() { return reporterUsername; }
    public String getTargetUsername() { return targetUsername; }
    public String getReason() { return reason; }
    public String getMessageSnippet() { return messageSnippet; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
}
