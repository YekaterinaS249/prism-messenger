package com.example.messenger.model;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "group_member", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "username"}))
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup group;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    // "ADMIN" or "MEMBER" — per-group role, independent of the site-wide admin flag on User.
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'MEMBER'")
    private String role = "MEMBER";

    public GroupMember() {}

    public GroupMember(ChatGroup group, String username) {
        this.group = group;
        this.username = username;
    }

    public GroupMember(ChatGroup group, String username, String role) {
        this.group = group;
        this.username = username;
        this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ChatGroup getGroup() { return group; }
    public void setGroup(ChatGroup group) { this.group = group; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
