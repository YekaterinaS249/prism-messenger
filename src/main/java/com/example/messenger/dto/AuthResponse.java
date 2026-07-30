package com.example.messenger.dto;

public class AuthResponse {
    private String token;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String status;

    public AuthResponse(String token, String username, String displayName, String avatarUrl, String status) {
        this.token = token;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.status = status;
    }

    public String getToken() { return token; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getStatus() { return status; }
}
