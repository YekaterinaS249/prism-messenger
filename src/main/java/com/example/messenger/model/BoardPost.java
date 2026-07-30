package com.example.messenger.model;

import javax.persistence.*;
import java.time.Instant;

/** A post on the shared board, visible to every user: either a schedule entry or an announcement. */
@Entity
@Table(name = "board_post")
public class BoardPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BoardPostType type = BoardPostType.ANNOUNCEMENT;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 4000)
    private String description;

    // Only meaningful for SCHEDULE posts, or as a due date for TASK posts.
    private Instant eventAt;

    // Only meaningful for TASK posts.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskStatus status;

    // Only meaningful for TASK posts. Nullable so ddl-auto=update can add it to an already
    // populated table without needing a DEFAULT.
    @Column(length = 50)
    private String assigneeUsername;

    // Only meaningful for TASK posts — when work on the task is meant to begin.
    private Instant startAt;

    // Only meaningful for TASK posts. Nullable so ddl-auto=update / older rows default cleanly;
    // the service defaults new tasks to MEDIUM.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TaskPriority priority;

    @Column(nullable = false, length = 50)
    private String authorUsername;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public BoardPost() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BoardPostType getType() { return type; }
    public void setType(BoardPostType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getEventAt() { return eventAt; }
    public void setEventAt(Instant eventAt) { this.eventAt = eventAt; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public String getAssigneeUsername() { return assigneeUsername; }
    public void setAssigneeUsername(String assigneeUsername) { this.assigneeUsername = assigneeUsername; }

    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public String getAuthorUsername() { return authorUsername; }
    public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
