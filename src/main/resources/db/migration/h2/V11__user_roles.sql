-- See postgresql/V11__user_roles.sql for rationale.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS role varchar(20) NOT NULL DEFAULT 'USER';
UPDATE app_user SET role = 'SUPER_ADMIN' WHERE is_admin = true;
