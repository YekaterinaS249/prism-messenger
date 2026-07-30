package com.example.messenger.dto;

import javax.validation.constraints.NotBlank;

public class UpdateTaskStatusRequest {

    @NotBlank
    private String status; // "TODO" | "IN_PROGRESS" | "DONE"

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
