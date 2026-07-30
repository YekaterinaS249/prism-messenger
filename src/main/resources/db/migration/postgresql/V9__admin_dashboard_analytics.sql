-- Registration timestamp, needed for "new users today"/"new users per day" on the admin dashboard.
-- Backfilled with now() for existing rows since the column never existed before.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
UPDATE app_user SET created_at = now() WHERE created_at IS NULL;
ALTER TABLE app_user ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE app_user ALTER COLUMN created_at SET DEFAULT now();

-- Aggregate-only message counters for the admin analytics chart. No message content or sender
-- identity is ever written here — just a per-day tally — so this doesn't reintroduce message
-- persistence, it only tracks volume over time for the admin dashboard.
CREATE TABLE IF NOT EXISTS daily_message_stats (
    stat_date DATE PRIMARY KEY,
    message_count BIGINT NOT NULL DEFAULT 0
);

-- Same idea, broken down per group, so the admin panel can show which groups are most active
-- over a given window (e.g. last 7 days). Direct messages aren't attributed to any group here.
CREATE TABLE IF NOT EXISTS daily_group_message_stats (
    id BIGSERIAL PRIMARY KEY,
    stat_date DATE NOT NULL,
    group_id BIGINT NOT NULL,
    message_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_daily_group_message_stats UNIQUE (stat_date, group_id)
);
