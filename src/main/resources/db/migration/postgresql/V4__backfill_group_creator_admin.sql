-- V3 added group_member.role defaulting every existing row to 'MEMBER', which left
-- pre-existing groups with no admin at all (nobody able to kick/promote/delete).
-- Backfill: the original creator of each group becomes its admin.
UPDATE group_member gm
SET role = 'ADMIN'
FROM chat_group cg
WHERE gm.group_id = cg.id
  AND gm.username = cg.created_by
  AND gm.role = 'MEMBER';
