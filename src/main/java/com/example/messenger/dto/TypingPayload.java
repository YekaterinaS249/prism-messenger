package com.example.messenger.dto;

public class TypingPayload {
    private String senderUsername;
    private String senderDisplayName;
    private String recipientUsername; // direct chats only
    private Long groupId;             // group chats only
    private boolean typing;
    private final String kind = "typing";

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getSenderDisplayName() { return senderDisplayName; }
    public void setSenderDisplayName(String senderDisplayName) { this.senderDisplayName = senderDisplayName; }
    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public boolean isTyping() { return typing; }
    public void setTyping(boolean typing) { this.typing = typing; }
    public String getKind() { return kind; }
}
