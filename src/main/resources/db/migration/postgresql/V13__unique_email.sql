-- Email becomes a real second login identifier (alongside username), so it needs a genuine
-- DB-level uniqueness guarantee, not just an application-layer check. NULLs (existing accounts
-- that haven't set an email yet) are unaffected — both Postgres and H2 allow multiple NULLs in
-- a unique index. Values are always stored lower-cased by the application (see UserService),
-- so a plain (non-expression) unique index is sufficient here.
CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_email_unique ON app_user (email);
