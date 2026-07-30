package com.example.messenger.dto;

public class GroupMemberDto {
    private String username;
    private String displayName;
    private String avatarUrl;
    private String role;

    public GroupMemberDto(String username, String displayName, String avatarUrl, String role) {
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getRole() { return role; }
}
