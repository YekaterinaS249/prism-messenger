package com.example.messenger.dto;

import com.example.messenger.model.GroupType;

public class GroupDto {
    private Long id;
    private String name;
    private String avatarUrl;
    private GroupType type;
    private String createdBy;
    private long memberCount;
    private boolean member;

    public GroupDto(Long id, String name, String avatarUrl, GroupType type, String createdBy, long memberCount, boolean member) {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.type = type;
        this.createdBy = createdBy;
        this.memberCount = memberCount;
        this.member = member;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAvatarUrl() { return avatarUrl; }
    public GroupType getType() { return type; }
    public String getCreatedBy() { return createdBy; }
    public long getMemberCount() { return memberCount; }
    public boolean isMember() { return member; }
}
