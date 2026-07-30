package com.example.messenger.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class CreateBoardPostRequest {

    @NotBlank
    private String type; // "SCHEDULE" or "ANNOUNCEMENT"

    @NotBlank(message = "Заголовок обязателен")
    @Size(min = 1, max = 150, message = "Заголовок должен быть не длиннее 150 символов")
    private String title;

    @Size(max = 4000, message = "Описание должно быть не длиннее 4000 символов")
    private String description;

    // ISO-8601 datetime string, e.g. from <input type="datetime-local">; only used for SCHEDULE posts.
    private String eventAt;

    // ISO-8601 datetime string; only used for TASK posts (when work is meant to begin).
    private String startAt;

    // Username of the person the task is assigned to; only used for TASK posts. Optional.
    private String assigneeUsername;

    // "LOW" / "MEDIUM" / "HIGH"; only used for TASK posts. Optional — defaults to MEDIUM.
    private String priority;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEventAt() { return eventAt; }
    public void setEventAt(String eventAt) { this.eventAt = eventAt; }

    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }

    public String getAssigneeUsername() { return assigneeUsername; }
    public void setAssigneeUsername(String assigneeUsername) { this.assigneeUsername = assigneeUsername; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}
