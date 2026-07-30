package com.example.messenger.service;

import com.example.messenger.dto.PlatformSettingsDto;
import com.example.messenger.model.PlatformSettings;
import com.example.messenger.repository.PlatformSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class PlatformSettingsService {

    private final PlatformSettingsRepository repository;
    private final UserService userService;
    private final AuditLogService auditLogService;

    public PlatformSettingsService(PlatformSettingsRepository repository, UserService userService,
                                    AuditLogService auditLogService) {
        this.repository = repository;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    /** The migration seeds row id=1, but fall back to in-memory defaults if it's somehow missing. */
    private PlatformSettings current() {
        return repository.findById((short) 1).orElseGet(PlatformSettings::new);
    }

    public boolean isRegistrationEnabled() { return current().isRegistrationEnabled(); }
    public boolean isGroupCreationEnabled() { return current().isGroupCreationEnabled(); }
    public boolean isMaintenanceMode() { return current().isMaintenanceMode(); }

    public PlatformSettingsDto get(String requester) {
        requireAdmin(requester);
        PlatformSettings s = current();
        return new PlatformSettingsDto(s.isRegistrationEnabled(), s.isGroupCreationEnabled(), s.isMaintenanceMode());
    }

    public PlatformSettingsDto update(String requester, Boolean registrationEnabled, Boolean groupCreationEnabled,
                                       Boolean maintenanceMode) {
        if (!userService.isSuperAdmin(requester)) throw new SecurityException("Super admin only");
        PlatformSettings s = current();
        StringBuilder changes = new StringBuilder();
        if (registrationEnabled != null && registrationEnabled != s.isRegistrationEnabled()) {
            s.setRegistrationEnabled(registrationEnabled);
            changes.append("registration=").append(registrationEnabled).append(' ');
        }
        if (groupCreationEnabled != null && groupCreationEnabled != s.isGroupCreationEnabled()) {
            s.setGroupCreationEnabled(groupCreationEnabled);
            changes.append("groupCreation=").append(groupCreationEnabled).append(' ');
        }
        if (maintenanceMode != null && maintenanceMode != s.isMaintenanceMode()) {
            s.setMaintenanceMode(maintenanceMode);
            changes.append("maintenance=").append(maintenanceMode).append(' ');
        }
        repository.save(s);
        if (changes.length() > 0) {
            auditLogService.record(requester, "UPDATE_SETTINGS", null, changes.toString().trim());
        }
        return new PlatformSettingsDto(s.isRegistrationEnabled(), s.isGroupCreationEnabled(), s.isMaintenanceMode());
    }

    private void requireAdmin(String requester) {
        if (!userService.isAdmin(requester)) throw new SecurityException("Admin only");
    }
}
