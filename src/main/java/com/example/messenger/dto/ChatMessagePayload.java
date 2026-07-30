package com.example.messenger.dto;

import com.example.messenger.model.MessageType;

import java.util.List;

/** Payload exchanged live over STOMP (/app/chat.send). Never persisted — relayed and discarded. */
public class ChatMessagePayload {
    private String senderUsername;
    private String senderDisplayName;
    private String senderAvatarUrl;
    private String recipientUsername;
    private Long groupId;
    private String content;
    private MessageType type = MessageType.TEXT;
    private String mediaUrl;
    private String mediaName;
    private String createdAt;

    // Client-generated id so replies/reactions can reference this exact message even
    // though nothing is persisted server-side; the server just relays it unchanged.
    private String clientId;
    private String replyToClientId;
    private String replyToSenderName;
    private String replyToSnippet;

    // Only meaningful for POLL messages; votes are tracked live via /app/chat.vote, not persisted.
    private String pollQuestion;
    private List<String> pollOptions;

    // "/me" style action message: rendered as "* Sender did something" instead of a normal bubble.
    private boolean action;

    // Self-destruct: client-enforced only (nothing is persisted anyway), tells recipients'
    // clients to remove this message from view after N seconds.
    private Integer expiresInSeconds;

    // End-to-end encryption (direct chats only): when true, `content` holds base64 AES-GCM
    // ciphertext and `iv` holds the base64 nonce used to produce it. The server never decrypts
    // or inspects either field — it's a pure relay, same as everything else here.
    private boolean encrypted;
    private String iv;

    // Set when this message is a forward of another one; holds the original sender's display
    // name so the UI can show "Переслано от <name>". Purely client-set and relayed, like everything else here.
    private String forwardedFrom;

    // Only meaningful for LOCATION messages: a one-shot lat/lng shared from the browser's
    // Geolocation API. Purely relayed, like everything else here.
    private Double lat;
    private Double lng;

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
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getMediaName() { return mediaName; }
    public void setMediaName(String mediaName) { this.mediaName = mediaName; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

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
    public List<String> getPollOptions() { return pollOptions; }
    public void setPollOptions(List<String> pollOptions) { this.pollOptions = pollOptions; }

    public boolean isAction() { return action; }
    public void setAction(boolean action) { this.action = action; }

    public Integer getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Integer expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }

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
