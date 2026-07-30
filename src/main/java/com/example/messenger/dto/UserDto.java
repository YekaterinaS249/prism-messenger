package com.example.messenger.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UserDto {
    private String username;
    private String displayName;
    private String avatarUrl;
    private String status;
    private boolean online;
    private String lastSeen;
    private boolean showOnlineStatus;
    private String publicKey;
    private boolean isAdmin;
    private String presenceStatus;
    private String jobTitle;
    private boolean banned;
    private boolean verified;
    private String role;
    private String email;

    public UserDto(String username, String displayName, String avatarUrl, String status, boolean online, String lastSeen) {
        this(username, displayName, avatarUrl, status, online, lastSeen, true, null, false, null, null, false, false, "USER", null);
    }

    public UserDto(String username, String displayName, String avatarUrl, String status, boolean online,
                    String lastSeen, boolean showOnlineStatus, String publicKey, boolean isAdmin) {
        this(username, displayName, avatarUrl, status, online, lastSeen, showOnlineStatus, publicKey, isAdmin, null, null, false, false, "USER", null);
    }

    public UserDto(String username, String displayName, String avatarUrl, String status, boolean online,
                    String lastSeen, boolean showOnlineStatus, String publicKey, boolean isAdmin,
                    String presenceStatus, String jobTitle, boolean banned, boolean verified, String role, String email) {
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.status = status;
        this.online = online;
        this.lastSeen = lastSeen;
        this.showOnlineStatus = showOnlineStatus;
        this.publicKey = publicKey;
        this.isAdmin = isAdmin;
        this.presenceStatus = presenceStatus;
        this.jobTitle = jobTitle;
        this.banned = banned;
        this.verified = verified;
        this.role = role;
        this.email = email;
    }

    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getStatus() { return status; }
    public boolean isOnline() { return online; }
    public String getLastSeen() { return lastSeen; }
    public boolean isShowOnlineStatus() { return showOnlineStatus; }
    public String getPublicKey() { return publicKey; }
    @JsonProperty("isAdmin")
    public boolean isAdmin() { return isAdmin; }
    public String getPresenceStatus() { return presenceStatus; }
    public String getJobTitle() { return jobTitle; }
    public boolean isBanned() { return banned; }
    public boolean isVerified() { return verified; }
    public String getRole() { return role; }
    public String getEmail() { return email; }
}
