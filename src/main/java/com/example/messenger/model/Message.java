package com.example.messenger.model;

import javax.persistence.*;
import java.time.Instant;

/**
 * Persisted chat message — added so chat history survives reconnects/reloads. Before this,
 * messages were relayed live over WebSocket only and never stored anywhere (see
 * ChatWebSocketController/ChatService doc comments predating this entity).
 *
 * Scope: only the core message content is persisted here. Reactions, edits, deletions, poll
 * votes, pins and typing indicators remain live-only, exactly as before — this table stores
 * messages as they were originally sent, not their live-edited state.
 *
 * Self-destructing messages (expiresInSeconds set) are intentionally never saved here at all —
 * persisting them would defeat the point of "disappears after N seconds".
 */
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String senderUsername;

    // Denormalized snapshot of the sender's name/avatar at send time — avoids an extra lookup
    // per historical message when loading a page of history, at the cost of not reflecting later
    // profile changes (consistent with "stores messages as they were originally sent" above).
    @Column(length = 100)
    private String senderDisplayName;

    @Column(length = 500)
    private String senderAvatarUrl;

    // Null for group messages.
    @Column(length = 50)
    private String recipientUsername;

    // Null for direct messages.
    @Column
    private Long groupId;

    @Column(columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 20)
    private String type = "TEXT";

    @Column(length = 500)
    private String mediaUrl;

    @Column(length = 255)
    private String mediaName;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(length = 100)
    private String clientId;

    @Column(length = 100)
    private String replyToClientId;

    @Column(length = 100)
    private String replyToSenderName;

    @Column(length = 500)
    private String replyToSnippet;

    @Column(columnDefinition = "text")
    private String pollQuestion;

    // JSON-serialized List<String> (kept as plain text — no need for a separate table for MVP).
    @Column(columnDefinition = "text")
    private String pollOptionsJson;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean action;

    // True if `content` is E2E ciphertext (direct chats only) — server never decrypts it.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean encrypted;

    @Column(length = 100)
    private String iv;

    @Column(length = 50)
    private String forwardedFrom;

    private Double lat;
    private Double lng;

    public Message() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public String getSenderDisplayName() { return senderDisplayName; }
    public void setSenderDisplayName(String senderDisplayName) { this.senderDisplayName = senderDisplayName; }

    public String getSenderAvatarUrl() { return senderAvatarUrl; }
    public void setSenderAvatarUrl(String senderAvatarUrl) { this.senderAvatarUrl = senderAvatarUrl; }

    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getMediaName() { return mediaName; }
    public void setMediaName(String mediaName) { this.mediaName = mediaName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getReplyToClientId() { return replyToClientId; }
    public void setReplyToClientId(String replyToClientId) { this.replyToClientId = replyToClientId; }

    public String getReplyToSenderName() { return replyToSenderName; }
    public void setReplyToSenderName(String replyToSenderName) { this.replyToSenderName = replyToSenderName; }

    public String getReplyToSnippet() { return replyToSnippet; }
    public void setReplyToSnippet(String replyToSnippet) { this.replyToSnippet = replyToSnippet; }

    public String getPollQuestion() { return pollQuestion; }
    public void setPollQuestion(String pollQuestion) { this.pollQuestion = pollQuestion; }

    public String getPollOptionsJson() { return pollOptionsJson; }
    public void setPollOptionsJson(String pollOptionsJson) { this.pollOptionsJson = pollOptionsJson; }

    public boolean isAction() { return action; }
    public void setAction(boolean action) { this.action = action; }

    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }

    public String getForwardedFrom() { return forwardedFrom; }
    public void setForwardedFrom(String forwardedFrom) { this.forwardedFrom = forwardedFrom; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
}
