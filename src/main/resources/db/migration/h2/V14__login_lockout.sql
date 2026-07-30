-- See postgresql/V14__login_lockout.sql for rationale.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS failed_login_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS locked_until timestamp;
