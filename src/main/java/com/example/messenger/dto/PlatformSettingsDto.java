package com.example.messenger.dto;

public class PlatformSettingsDto {
    private boolean registrationEnabled;
    private boolean groupCreationEnabled;
    private boolean maintenanceMode;

    public PlatformSettingsDto(boolean registrationEnabled, boolean groupCreationEnabled, boolean maintenanceMode) {
        this.registrationEnabled = registrationEnabled;
        this.groupCreationEnabled = groupCreationEnabled;
        this.maintenanceMode = maintenanceMode;
    }

    public boolean isRegistrationEnabled() { return registrationEnabled; }
    public boolean isGroupCreationEnabled() { return groupCreationEnabled; }
    public boolean isMaintenanceMode() { return maintenanceMode; }
}
