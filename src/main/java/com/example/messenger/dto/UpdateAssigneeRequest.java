package com.example.messenger.dto;

public class UpdateAssigneeRequest {

    // Username to assign the task to, or null/blank to unassign.
    private String assigneeUsername;

    public String getAssigneeUsername() { return assigneeUsername; }
    public void setAssigneeUsername(String assigneeUsername) { this.assigneeUsername = assigneeUsername; }
}
