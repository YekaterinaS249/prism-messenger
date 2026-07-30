package com.example.messenger.dto;

/**
 * Live-only event broadcast over the same channels as chat messages (STOMP /app/chat.vote).
 * Nothing is persisted; it tells everyone currently viewing the conversation that a user picked
 * (or un-picked) one option of a poll message, identified by the poll's client-generated id.
 */
public class VotePayload {

    private String senderUsername;
    private String recipientUsername; // direct chats only
    private Long groupId;             // group/channel chats only
    private String targetClientId;
    private int optionIndex;
    private String action; // "add" | "remove"
    private final String kind = "vote";

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getTargetClientId() { return targetClientId; }
    public void setTargetClientId(String targetClientId) { this.targetClientId = targetClientId; }
    public int getOptionIndex() { return optionIndex; }
    public void setOptionIndex(int optionIndex) { this.optionIndex = optionIndex; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getKind() { return kind; }
}
