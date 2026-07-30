package com.example.messenger.dto;

/**
 * Live-only event broadcast over the same channels as chat messages (STOMP /app/chat.pin).
 * Nothing is persisted; it just tells everyone currently viewing the conversation which message
 * (identified by its client-generated id) is pinned, or that the chat's pin was cleared.
 */
public class PinPayload {

    private String senderUsername;
    private String recipientUsername; // direct chats only
    private Long groupId;             // group/channel chats only
    private String targetClientId;    // null when action == "unpin"
    private String snippet;           // short preview text shown in the pinned banner
    private String senderName;        // display name of who sent the pinned message
    private String action;            // "pin" | "unpin"
    private final String kind = "pin";

    public String getKind() { return kind; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getTargetClientId() { return targetClientId; }
    public void setTargetClientId(String targetClientId) { this.targetClientId = targetClientId; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
