package com.example.messenger.dto;

public class AuditLogEntryDto {
    private String actorUsername;
    private String action;
    private String target;
    private String details;
    private String createdAt;

    public AuditLogEntryDto(String actorUsername, String action, String target, String details, String createdAt) {
        this.actorUsername = actorUsername;
        this.action = action;
        this.target = target;
        this.details = details;
        this.createdAt = createdAt;
    }

    public String getActorUsername() { return actorUsername; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getDetails() { return details; }
    public String getCreatedAt() { return createdAt; }
}
