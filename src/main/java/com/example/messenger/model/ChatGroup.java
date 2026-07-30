package com.example.messenger.model;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_group")
public class ChatGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupType type = GroupType.GROUP;

    @Column(nullable = false, length = 50)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public ChatGroup() {}

    public ChatGroup(String name, GroupType type, String createdBy) {
        this.name = name;
        this.type = type;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public GroupType getType() { return type; }
    public void setType(GroupType type) { this.type = type; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
