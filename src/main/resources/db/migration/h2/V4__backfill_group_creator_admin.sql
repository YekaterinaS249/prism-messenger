-- V3 added group_member.role defaulting every existing row to 'MEMBER', which left
-- pre-existing groups with no admin at all (nobody able to kick/promote/delete).
-- Backfill: the original creator of each group becomes its admin.
UPDATE group_member gm
SET role = 'ADMIN'
WHERE gm.role = 'MEMBER'
  AND gm.username = (SELECT cg.created_by FROM chat_group cg WHERE cg.id = gm.group_id);
