package com.example.messenger.dto;

/**
 * Live-only event broadcast over the same channels as chat messages (STOMP /app/chat.edit),
 * same pattern as PinPayload/ReactionPayload. Nothing is persisted; it tells everyone currently
 * viewing the conversation to update or remove a message (identified by its client-generated id)
 * in place. Only the original sender may issue this — enforced server-side.
 */
public class EditPayload {

    private String senderUsername;
    private String recipientUsername; // direct chats only
    private Long groupId;             // group/channel chats only
    private String targetClientId;
    private String newContent;        // null when action == "delete"
    private String action;            // "edit" | "delete"
    private final String kind = "edit";

    public String getKind() { return kind; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getTargetClientId() { return targetClientId; }
    public void setTargetClientId(String targetClientId) { this.targetClientId = targetClientId; }
    public String getNewContent() { return newContent; }
    public void setNewContent(String newContent) { this.newContent = newContent; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
