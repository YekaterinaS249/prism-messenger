package com.example.messenger.model;

import javax.persistence.*;

/** Singleton row (id always 1) of platform-wide toggles controlled from the admin panel. */
@Entity
@Table(name = "platform_settings")
public class PlatformSettings {

    @Id
    private Short id = 1;

    @Column(name = "registration_enabled", nullable = false)
    private boolean registrationEnabled = true;

    @Column(name = "group_creation_enabled", nullable = false)
    private boolean groupCreationEnabled = true;

    @Column(name = "maintenance_mode", nullable = false)
    private boolean maintenanceMode = false;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }

    public boolean isRegistrationEnabled() { return registrationEnabled; }
    public void setRegistrationEnabled(boolean registrationEnabled) { this.registrationEnabled = registrationEnabled; }

    public boolean isGroupCreationEnabled() { return groupCreationEnabled; }
    public void setGroupCreationEnabled(boolean groupCreationEnabled) { this.groupCreationEnabled = groupCreationEnabled; }

    public boolean isMaintenanceMode() { return maintenanceMode; }
    public void setMaintenanceMode(boolean maintenanceMode) { this.maintenanceMode = maintenanceMode; }
}
