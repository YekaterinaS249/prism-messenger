-- Presence status dropdown ("На созвоне", "Занят(а)", ...) and job title, both optional/free-form
-- on the server side — the client controls the fixed vocabulary for presence_status.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS presence_status varchar(20);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS job_title varchar(100);

-- One-directional block: when blocker_username has blocked blocked_username, direct messages
-- from blocked_username are dropped before delivery to blocker_username.
CREATE TABLE IF NOT EXISTS blocked_user (
    id BIGSERIAL PRIMARY KEY,
    blocker_username varchar(50) NOT NULL,
    blocked_username varchar(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_blocked_user UNIQUE (blocker_username, blocked_username)
);

-- User-uploaded custom stickers, shown in a "Мои" tab alongside the built-in sticker set.
CREATE TABLE IF NOT EXISTS user_sticker (
    id BIGSERIAL PRIMARY KEY,
    owner_username varchar(50) NOT NULL,
    url varchar(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
