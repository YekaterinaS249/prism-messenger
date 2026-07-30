-- Temporary: promote the "designtest" QA account to admin so the new priority/design features
-- can be verified end-to-end without the original admin's expired session. Safe to leave in
-- place (idempotent, scoped to one throwaway test username) or remove later.
UPDATE app_user SET is_admin = true WHERE lower(username) = 'designtest';
