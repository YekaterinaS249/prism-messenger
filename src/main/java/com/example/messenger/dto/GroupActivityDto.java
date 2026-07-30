package com.example.messenger.dto;

public class GroupActivityDto {
    private Long groupId;
    private String groupName;
    private long messageCount;
    private long memberCount;

    public GroupActivityDto(Long groupId, String groupName, long messageCount, long memberCount) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.messageCount = messageCount;
        this.memberCount = memberCount;
    }

    public Long getGroupId() { return groupId; }
    public String getGroupName() { return groupName; }
    public long getMessageCount() { return messageCount; }
    public long getMemberCount() { return memberCount; }
}
