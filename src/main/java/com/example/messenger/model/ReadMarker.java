package com.example.messenger.model;

import javax.persistence.*;
import java.time.Instant;

/**
 * Tracks, per user, how far they've read into a direct chat or group — one row per conversation.
 * Exactly one of peerUsername/groupId is set per row (never both, never neither). Used to compute
 * unread counts that survive being offline, now that messages are persisted (see Message.java).
 *
 * Missing row for a conversation the user has actually opened before this feature shipped is
 * treated as "caught up" (see MessageService) — there's no historical backlog to worry about
 * since message persistence itself only just launched.
 */
@Entity
@Table(name = "read_marker", uniqueConstraints = @UniqueConstraint(columnNames = {"username", "peer_username", "group_id"}))
public class ReadMarker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(length = 50)
    private String peerUsername;

    @Column
    private Long groupId;

    @Column(nullable = false)
    private Instant lastReadAt = Instant.now();

    public ReadMarker() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPeerUsername() { return peerUsername; }
    public void setPeerUsername(String peerUsername) { this.peerUsername = peerUsername; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Instant getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Instant lastReadAt) { this.lastReadAt = lastReadAt; }
}
