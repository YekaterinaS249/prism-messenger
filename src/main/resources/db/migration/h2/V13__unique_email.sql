-- See postgresql/V13__unique_email.sql for rationale.
CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_email_unique ON app_user (email);
