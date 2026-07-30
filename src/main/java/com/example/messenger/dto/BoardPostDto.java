package com.example.messenger.dto;

import com.example.messenger.model.BoardPostType;
import com.example.messenger.model.TaskPriority;
import com.example.messenger.model.TaskStatus;

import java.util.List;

public class BoardPostDto {
    private Long id;
    private BoardPostType type;
    private String title;
    private String description;
    private String eventAt;
    private String startAt;
    private TaskStatus status;
    private TaskPriority priority;
    private String authorUsername;
    private String authorDisplayName;
    private String authorAvatarUrl;
    private String assigneeUsername;
    private String assigneeDisplayName;
    private String assigneeAvatarUrl;
    private String createdAt;
    // Populated only when the requester is an admin — who has opened this post.
    private List<UserDto> seenBy;

    public BoardPostDto(Long id, BoardPostType type, String title, String description, String eventAt,
                         String startAt, TaskStatus status, TaskPriority priority, String authorUsername,
                         String authorDisplayName, String authorAvatarUrl, String assigneeUsername,
                         String assigneeDisplayName, String assigneeAvatarUrl, String createdAt, List<UserDto> seenBy) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.description = description;
        this.eventAt = eventAt;
        this.startAt = startAt;
        this.status = status;
        this.priority = priority;
        this.authorUsername = authorUsername;
        this.authorDisplayName = authorDisplayName;
        this.authorAvatarUrl = authorAvatarUrl;
        this.assigneeUsername = assigneeUsername;
        this.assigneeDisplayName = assigneeDisplayName;
        this.assigneeAvatarUrl = assigneeAvatarUrl;
        this.createdAt = createdAt;
        this.seenBy = seenBy;
    }

    public Long getId() { return id; }
    public BoardPostType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getEventAt() { return eventAt; }
    public String getStartAt() { return startAt; }
    public TaskStatus getStatus() { return status; }
    public TaskPriority getPriority() { return priority; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    public String getAssigneeUsername() { return assigneeUsername; }
    public String getAssigneeDisplayName() { return assigneeDisplayName; }
    public String getAssigneeAvatarUrl() { return assigneeAvatarUrl; }
    public String getCreatedAt() { return createdAt; }
    public List<UserDto> getSeenBy() { return seenBy; }
}
