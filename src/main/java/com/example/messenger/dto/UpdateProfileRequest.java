package com.example.messenger.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class UpdateProfileRequest {

    @Size(min = 1, max = 100, message = "Отображаемое имя не может быть длиннее 100 символов")
    @Pattern(regexp = "^[\\p{L}\\p{M}\\p{N}\\p{P} ]+$",
            message = "Отображаемое имя не может содержать эмодзи и спецсимволы")
    private String displayName;

    @Email(message = "Некорректный email")
    @Size(max = 150, message = "Email должен быть не длиннее 150 символов")
    private String email;

    @Size(max = 140, message = "Статус должен быть не длиннее 140 символов")
    private String status;

    private Boolean showOnlineStatus;

    // Fixed vocabulary enforced client-side (ON_CALL, BUSY, LUNCH, DND, VACATION); empty string clears it.
    @Size(max = 20)
    private String presenceStatus;

    @Size(max = 100, message = "Должность должна быть не длиннее 100 символов")
    private String jobTitle;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getShowOnlineStatus() { return showOnlineStatus; }
    public void setShowOnlineStatus(Boolean showOnlineStatus) { this.showOnlineStatus = showOnlineStatus; }

    public String getPresenceStatus() { return presenceStatus; }
    public void setPresenceStatus(String presenceStatus) { this.presenceStatus = presenceStatus; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
}
