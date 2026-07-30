-- Ban/unban and verified-badge flags for the admin users panel.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS banned boolean NOT NULL DEFAULT false;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS verified boolean NOT NULL DEFAULT false;

-- User-submitted reports, reviewed by admins in the moderation queue. target_username is who's
-- being reported; message_snippet is an optional short excerpt the reporter chose to include
-- (never auto-collected, since messages aren't persisted anywhere else in this app).
CREATE TABLE IF NOT EXISTS report (
    id BIGSERIAL PRIMARY KEY,
    reporter_username varchar(50) NOT NULL,
    target_username varchar(50) NOT NULL,
    reason varchar(500) NOT NULL,
    message_snippet varchar(300),
    status varchar(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    resolved_by varchar(50),
    resolved_at TIMESTAMP
);

-- Single-row table of platform-wide toggles the admin panel controls.
CREATE TABLE IF NOT EXISTS platform_settings (
    id smallint PRIMARY KEY DEFAULT 1,
    registration_enabled boolean NOT NULL DEFAULT true,
    group_creation_enabled boolean NOT NULL DEFAULT true,
    maintenance_mode boolean NOT NULL DEFAULT false,
    CONSTRAINT chk_platform_settings_singleton CHECK (id = 1)
);
INSERT INTO platform_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- Append-only log of admin actions (ban/unban/delete/settings change/broadcast/verify), shown in
-- the admin panel's "Журнал" section for accountability.
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_username varchar(50) NOT NULL,
    action varchar(50) NOT NULL,
    target varchar(100),
    details varchar(500),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
