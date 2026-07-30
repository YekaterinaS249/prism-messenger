-- Global site-admin role: only an admin can create/edit/delete board posts and manage users.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS is_admin boolean NOT NULL DEFAULT false;

-- Bootstrap: promote the existing "Yekaterina" account to admin so someone can manage the site
-- after this migration runs. Safe to leave in place — it's a no-op once is_admin is already true.
UPDATE app_user SET is_admin = true WHERE lower(username) = 'yekaterina' OR lower(display_name) = 'yekaterina';

-- Per-group roles (admin/member) so a group creator/admin can promote or kick members.
ALTER TABLE group_member ADD COLUMN IF NOT EXISTS role varchar(20) NOT NULL DEFAULT 'MEMBER';

-- SCHEDULE posts can now embed a small editable table; give description more room.
ALTER TABLE board_post ALTER COLUMN description TYPE varchar(4000);

-- Tracks which users have opened a given board post ("seen by"), visible to admins only.
CREATE TABLE IF NOT EXISTS board_post_view (
    post_id     bigint      NOT NULL REFERENCES board_post (id) ON DELETE CASCADE,
    username    varchar(50) NOT NULL,
    viewed_at   timestamp   NOT NULL,
    PRIMARY KEY (post_id, username)
);
