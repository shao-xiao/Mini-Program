-- Split coarse investment and meeting permissions into independent feature permissions.
-- Existing role grants are copied so old roles keep their previous access after backend checks are split.

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
  ('investment-content:*', '招商内容全部权限', 'investment-content', '*', 'ENABLED', 0, 0, '招商内容查看、新增、编辑、删除、发布停用'),
  ('investment-content:view', '查看招商内容', 'investment-content', 'view', 'ENABLED', 0, 0, '查看招商内容'),
  ('investment-content:create', '新增招商内容', 'investment-content', 'create', 'ENABLED', 0, 0, '新增招商内容'),
  ('investment-content:update', '编辑招商内容', 'investment-content', 'update', 'ENABLED', 0, 0, '编辑招商内容'),
  ('investment-content:delete', '删除招商内容', 'investment-content', 'delete', 'ENABLED', 0, 0, '删除招商内容'),
  ('investment-content:publish', '发布招商内容', 'investment-content', 'publish', 'ENABLED', 0, 0, '发布或停用招商内容'),
  ('investment-listing:*', '招商房源全部权限', 'investment-listing', '*', 'ENABLED', 0, 0, '招商房源查看、新增、编辑、删除、上下架、图片管理'),
  ('investment-listing:view', '查看招商房源', 'investment-listing', 'view', 'ENABLED', 0, 0, '查看招商房源'),
  ('investment-listing:create', '新增招商房源', 'investment-listing', 'create', 'ENABLED', 0, 0, '新增招商房源'),
  ('investment-listing:update', '编辑招商房源', 'investment-listing', 'update', 'ENABLED', 0, 0, '编辑招商房源'),
  ('investment-listing:delete', '删除招商房源', 'investment-listing', 'delete', 'ENABLED', 0, 0, '删除招商房源'),
  ('investment-listing:publish', '发布招商房源', 'investment-listing', 'publish', 'ENABLED', 0, 0, '上架或下架招商房源'),
  ('investment-listing:image', '管理招商房源图片', 'investment-listing', 'image', 'ENABLED', 0, 0, '上传、删除或设置招商房源封面'),
  ('investment-lead:*', '招商线索全部权限', 'investment-lead', '*', 'ENABLED', 0, 0, '招商线索查看、跟进、转化、删除'),
  ('investment-lead:view', '查看招商线索', 'investment-lead', 'view', 'ENABLED', 0, 0, '查看招商线索'),
  ('investment-lead:follow', '跟进招商线索', 'investment-lead', 'follow', 'ENABLED', 0, 0, '更新招商线索状态'),
  ('investment-lead:convert', '转化招商线索', 'investment-lead', 'convert', 'ENABLED', 0, 0, '转化招商线索为租户或合同'),
  ('investment-lead:delete', '删除招商线索', 'investment-lead', 'delete', 'ENABLED', 0, 0, '删除招商线索'),
  ('meeting-room:*', '会议室管理全部权限', 'meeting-room', '*', 'ENABLED', 0, 0, '会议室查看、新增、编辑、删除'),
  ('meeting-room:view', '查看会议室', 'meeting-room', 'view', 'ENABLED', 0, 0, '查看会议室'),
  ('meeting-room:create', '新增会议室', 'meeting-room', 'create', 'ENABLED', 0, 0, '新增会议室'),
  ('meeting-room:update', '编辑会议室', 'meeting-room', 'update', 'ENABLED', 0, 0, '编辑或启停会议室'),
  ('meeting-room:delete', '删除会议室', 'meeting-room', 'delete', 'ENABLED', 0, 0, '删除会议室'),
  ('meeting-booking:*', '会议预约全部权限', 'meeting-booking', '*', 'ENABLED', 0, 0, '会议预约查看、新增、确认、取消、完成'),
  ('meeting-booking:view', '查看会议预约', 'meeting-booking', 'view', 'ENABLED', 0, 0, '查看会议预约'),
  ('meeting-booking:create', '新增会议预约', 'meeting-booking', 'create', 'ENABLED', 0, 0, '新增会议预约'),
  ('meeting-booking:confirm', '确认会议预约', 'meeting-booking', 'confirm', 'ENABLED', 0, 0, '确认会议预约'),
  ('meeting-booking:cancel', '取消会议预约', 'meeting-booking', 'cancel', 'ENABLED', 0, 0, '取消会议预约'),
  ('meeting-booking:complete', '完成会议预约', 'meeting-booking', 'complete', 'ENABLED', 0, 0, '完成会议预约')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  module = VALUES(module),
  action = VALUES(action),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ADMIN', 'SUPER_ADMIN')
  AND p.code IN (
    'investment-content:*', 'investment-content:view', 'investment-content:create', 'investment-content:update', 'investment-content:delete', 'investment-content:publish',
    'investment-listing:*', 'investment-listing:view', 'investment-listing:create', 'investment-listing:update', 'investment-listing:delete', 'investment-listing:publish', 'investment-listing:image',
    'investment-lead:*', 'investment-lead:view', 'investment-lead:follow', 'investment-lead:convert', 'investment-lead:delete',
    'meeting-room:*', 'meeting-room:view', 'meeting-room:create', 'meeting-room:update', 'meeting-room:delete',
    'meeting-booking:*', 'meeting-booking:view', 'meeting-booking:create', 'meeting-booking:confirm', 'meeting-booking:cancel', 'meeting-booking:complete'
  )
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT old_rp.role_id, new_p.id, 'ENABLED', 0, 0
FROM sys_role_permission old_rp
JOIN sys_permission old_p ON old_p.id = old_rp.permission_id
JOIN sys_permission new_p ON (
     (old_p.code = 'investment:*' AND new_p.code IN (
       'investment-content:*', 'investment-content:view', 'investment-content:create', 'investment-content:update', 'investment-content:delete', 'investment-content:publish',
       'investment-listing:*', 'investment-listing:view', 'investment-listing:create', 'investment-listing:update', 'investment-listing:delete', 'investment-listing:publish', 'investment-listing:image',
       'investment-lead:*', 'investment-lead:view', 'investment-lead:follow', 'investment-lead:convert', 'investment-lead:delete'
     ))
  OR (old_p.code = 'investment:view' AND new_p.code IN ('investment-content:view', 'investment-listing:view', 'investment-lead:view'))
  OR (old_p.code = 'investment:create' AND new_p.code IN ('investment-content:create', 'investment-content:publish', 'investment-listing:create', 'investment-listing:publish', 'investment-listing:image', 'investment-lead:convert'))
  OR (old_p.code = 'investment:update' AND new_p.code IN ('investment-content:update', 'investment-listing:update'))
  OR (old_p.code = 'investment:delete' AND new_p.code IN ('investment-content:delete', 'investment-listing:delete', 'investment-lead:delete'))
  OR (old_p.code = 'investment:follow' AND new_p.code = 'investment-lead:follow')
)
WHERE old_rp.deleted = 0
  AND old_rp.status = 'ENABLED'
  AND old_p.deleted = 0
  AND old_p.status = 'ENABLED'
  AND new_p.deleted = 0
  AND new_p.status = 'ENABLED'
  AND old_p.code IN ('investment:*', 'investment:view', 'investment:create', 'investment:update', 'investment:delete', 'investment:follow')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT old_rp.role_id, new_p.id, 'ENABLED', 0, 0
FROM sys_role_permission old_rp
JOIN sys_permission old_p ON old_p.id = old_rp.permission_id
JOIN sys_permission new_p ON (
     (old_p.code = 'meeting:*' AND new_p.code IN (
       'meeting-room:*', 'meeting-room:view', 'meeting-room:create', 'meeting-room:update', 'meeting-room:delete',
       'meeting-booking:*', 'meeting-booking:view', 'meeting-booking:create', 'meeting-booking:confirm', 'meeting-booking:cancel', 'meeting-booking:complete'
     ))
  OR (old_p.code = 'meeting:view' AND new_p.code IN ('meeting-room:view', 'meeting-booking:view'))
  OR (old_p.code = 'meeting:create' AND new_p.code IN ('meeting-room:create', 'meeting-booking:create', 'meeting-booking:confirm', 'meeting-booking:cancel', 'meeting-booking:complete'))
  OR (old_p.code = 'meeting:update' AND new_p.code = 'meeting-room:update')
  OR (old_p.code = 'meeting:delete' AND new_p.code = 'meeting-room:delete')
)
WHERE old_rp.deleted = 0
  AND old_rp.status = 'ENABLED'
  AND old_p.deleted = 0
  AND old_p.status = 'ENABLED'
  AND new_p.deleted = 0
  AND new_p.status = 'ENABLED'
  AND old_p.code IN ('meeting:*', 'meeting:view', 'meeting:create', 'meeting:update', 'meeting:delete')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;
