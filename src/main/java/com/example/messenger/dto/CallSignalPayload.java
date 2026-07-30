package com.example.messenger.dto;

/**
 * Live-only WebRTC signaling message, broadcast over the same /queue/messages channel as chat
 * (STOMP /app/call.signal), discriminated by kind="call" like reactions/votes. The server never
 * looks at sdp/candidate — it's just a relay between the two peers of a 1:1 call. Only direct
 * (non-group) calls are supported.
 */
public class CallSignalPayload {

    private String callId;               // client-generated, correlates all messages of one call
    private String senderUsername;
    private String senderDisplayName;
    private String recipientUsername;    // set for direct 1:1 signaling, or group mesh point-to-point relay
    private Long groupId;                // set when this call belongs to a group (mesh call)
    private String signalType;           // "offer" | "answer" | "ice-candidate" | "hangup" | "reject" | "busy" | "ringing" | "join" | "leave"
    private String sdp;                  // offer/answer SDP, JSON-free plain string
    private String candidate;            // ICE candidate, JSON-stringified RTCIceCandidateInit
    private boolean video;               // audio-only vs. video call
    private final String kind = "call";

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }
    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
    public String getSenderDisplayName() { return senderDisplayName; }
    public void setSenderDisplayName(String senderDisplayName) { this.senderDisplayName = senderDisplayName; }
    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getSignalType() { return signalType; }
    public void setSignalType(String signalType) { this.signalType = signalType; }
    public String getSdp() { return sdp; }
    public void setSdp(String sdp) { this.sdp = sdp; }
    public String getCandidate() { return candidate; }
    public void setCandidate(String candidate) { this.candidate = candidate; }
    public boolean isVideo() { return video; }
    public void setVideo(boolean video) { this.video = video; }
    public String getKind() { return kind; }
}
