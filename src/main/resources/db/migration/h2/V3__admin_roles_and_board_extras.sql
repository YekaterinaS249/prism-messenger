-- See postgresql/V3__admin_roles_and_board_extras.sql for rationale.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS is_admin boolean NOT NULL DEFAULT false;

UPDATE app_user SET is_admin = true WHERE lower(username) = 'yekaterina' OR lower(display_name) = 'yekaterina';

ALTER TABLE group_member ADD COLUMN IF NOT EXISTS role varchar(20) NOT NULL DEFAULT 'MEMBER';

ALTER TABLE board_post ALTER COLUMN description VARCHAR(4000);

CREATE TABLE IF NOT EXISTS board_post_view (
    post_id     bigint      NOT NULL REFERENCES board_post (id) ON DELETE CASCADE,
    username    varchar(50) NOT NULL,
    viewed_at   timestamp   NOT NULL,
    PRIMARY KEY (post_id, username)
);
