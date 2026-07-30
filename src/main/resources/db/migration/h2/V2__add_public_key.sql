-- See postgresql/V2__add_public_key.sql for rationale.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS public_key varchar(255);
