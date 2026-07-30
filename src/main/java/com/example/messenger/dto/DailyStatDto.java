package com.example.messenger.dto;

public class DailyStatDto {
    private String date; // yyyy-MM-dd
    private long messageCount;
    private long newUserCount;

    public DailyStatDto(String date, long messageCount, long newUserCount) {
        this.date = date;
        this.messageCount = messageCount;
        this.newUserCount = newUserCount;
    }

    public String getDate() { return date; }
    public long getMessageCount() { return messageCount; }
    public long getNewUserCount() { return newUserCount; }
}
