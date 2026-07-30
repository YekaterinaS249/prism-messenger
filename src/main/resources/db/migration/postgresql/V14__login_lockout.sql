-- Brute-force protection: after 5 consecutive failed logins, the account is locked for 15
-- minutes (see UserService.recordFailedLogin/recordSuccessfulLogin). Deliberately account-level
-- only, not IP-based — simpler for this project, at the cost of someone being able to lock an
-- account they don't own by deliberately failing its password a few times.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS failed_login_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS locked_until timestamp;
