-- Replaces the flat is_admin boolean with a proper role scale (USER/MODERATOR/ADMIN/SUPER_ADMIN),
-- so moderation duties (bans, verification, reports) can be delegated without also handing out
-- the ability to change platform settings or grant roles to other people.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS role varchar(20) NOT NULL DEFAULT 'USER';

-- Backfill: everyone who was already an admin becomes a super-admin, so nobody loses any
-- capability they already had as part of this migration. New, more limited roles (MODERATOR,
-- plain ADMIN) are assigned going forward via the admin panel, not retroactively.
UPDATE app_user SET role = 'SUPER_ADMIN' WHERE is_admin = true;

-- is_admin is kept in place (and kept in sync by the application whenever role changes) since
-- a lot of existing authorization checks key off it as a simple "is this an ADMIN-or-above"
-- shortcut; it is no longer the source of truth for role assignment.
