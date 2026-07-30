package com.example.messenger.dto;

public class AdminDashboardDto {
    private long totalUsers;
    private int onlineNow;
    private long totalGroups;
    private long newUsersToday;
    private long storageBytes;

    public AdminDashboardDto(long totalUsers, int onlineNow, long totalGroups, long newUsersToday, long storageBytes) {
        this.totalUsers = totalUsers;
        this.onlineNow = onlineNow;
        this.totalGroups = totalGroups;
        this.newUsersToday = newUsersToday;
        this.storageBytes = storageBytes;
    }

    public long getTotalUsers() { return totalUsers; }
    public int getOnlineNow() { return onlineNow; }
    public long getTotalGroups() { return totalGroups; }
    public long getNewUsersToday() { return newUsersToday; }
    public long getStorageBytes() { return storageBytes; }
}
