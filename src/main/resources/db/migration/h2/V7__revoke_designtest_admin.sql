-- Cleanup: the "designtest" QA account was temporarily promoted (V6) to verify the new
-- priority/design features live. Testing is done — revoke it so "Yekaterina" remains the
-- sole site admin, matching the original design.
UPDATE app_user SET is_admin = false WHERE lower(username) = 'designtest';
