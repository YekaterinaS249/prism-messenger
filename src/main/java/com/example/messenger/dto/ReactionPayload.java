package com.example.messenger.dto;

/**
 * Live-only event broadcast over the same channels as chat messages (STOMP /app/chat.react).
 * Nothing is persisted; it just tells everyone currently viewing the conversation to add or
 * remove one emoji reaction from one message, identified by the message's client-generated id.
 */
public class ReactionPayload {

    private String senderUsername;
    private String recipientUsername; // direct chats only
    private Long groupId;             // group/channel chats only
    private String targetClientId;
    private String emoji;
    private String action; // "add" | "remove"
    private final String kind = "reaction";

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getTargetClientId() { return targetClientId; }
    public void setTargetClientId(String targetClientId) { this.targetClientId = targetClientId; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getKind() { return kind; }
}
