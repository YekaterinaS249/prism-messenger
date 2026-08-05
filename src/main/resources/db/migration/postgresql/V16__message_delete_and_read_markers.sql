ALTER TABLE message ADD COLUMN IF NOT EXISTS deleted boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS read_marker (
    id BIGSERIAL PRIMARY KEY,
    username varchar(50) NOT NULL,
    peer_username varchar(50),
    group_id bigint,
    last_read_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_read_marker UNIQUE (username, peer_username, group_id)
);

CREATE INDEX IF NOT EXISTS idx_read_marker_username ON read_marker (username);
