ALTER TABLE board_post ADD COLUMN IF NOT EXISTS priority varchar(10);
UPDATE board_post SET priority = 'MEDIUM' WHERE type = 'TASK' AND priority IS NULL;
