package com.example.messenger.dto;

import javax.validation.constraints.NotBlank;

public class UpdatePriorityRequest {

    @NotBlank
    private String priority; // "LOW" / "MEDIUM" / "HIGH"

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}
