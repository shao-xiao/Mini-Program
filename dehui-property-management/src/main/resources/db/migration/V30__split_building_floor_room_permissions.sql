-- Split coarse building permissions into independent building / floor / room permissions.
-- Keep existing building:* compatible for the building ledger, and copy old grants
-- to floor/room so existing roles do not lose access after the backend checks are split.

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
  ('floor:*', '楼层管理全部权限', 'floor', '*', 'ENABLED', 0, 0, '楼层查看、新增、编辑、删除'),
  ('floor:view', '查看楼层', 'floor', 'view', 'ENABLED', 0, 0, '查看楼层台账'),
  ('floor:create', '新增楼层', 'floor', 'create', 'ENABLED', 0, 0, '新增楼层或批量生成楼层'),
  ('floor:update', '编辑楼层', 'floor', 'update', 'ENABLED', 0, 0, '编辑楼层'),
  ('floor:delete', '删除楼层', 'floor', 'delete', 'ENABLED', 0, 0, '删除楼层'),
  ('room:*', '房间管理全部权限', 'room', '*', 'ENABLED', 0, 0, '房间查看、新增、编辑、删除、出租、退租'),
  ('room:view', '查看房间', 'room', 'view', 'ENABLED', 0, 0, '查看房间台账'),
  ('room:create', '新增房间', 'room', 'create', 'ENABLED', 0, 0, '新增房间'),
  ('room:update', '编辑房间', 'room', 'update', 'ENABLED', 0, 0, '编辑房间'),
  ('room:delete', '删除房间', 'room', 'delete', 'ENABLED', 0, 0, '删除房间'),
  ('room:lease', '房间出租', 'room', 'lease', 'ENABLED', 0, 0, '办理房间出租'),
  ('room:checkout', '房间退租', 'room', 'checkout', 'ENABLED', 0, 0, '办理房间退租')
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
    'floor:*', 'floor:view', 'floor:create', 'floor:update', 'floor:delete',
    'room:*', 'room:view', 'room:create', 'room:update', 'room:delete', 'room:lease', 'room:checkout'
  )
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT old_rp.role_id, new_p.id, 'ENABLED', 0, 0
FROM sys_role_permission old_rp
JOIN sys_permission old_p ON old_p.id = old_rp.permission_id
JOIN sys_permission new_p ON new_p.code = CASE old_p.code
  WHEN 'building:*' THEN 'floor:*'
  WHEN 'building:view' THEN 'floor:view'
  WHEN 'building:create' THEN 'floor:create'
  WHEN 'building:update' THEN 'floor:update'
  WHEN 'building:delete' THEN 'floor:delete'
END
WHERE old_rp.deleted = 0
  AND old_rp.status = 'ENABLED'
  AND old_p.deleted = 0
  AND old_p.status = 'ENABLED'
  AND new_p.deleted = 0
  AND new_p.status = 'ENABLED'
  AND old_p.code IN ('building:*', 'building:view', 'building:create', 'building:update', 'building:delete')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT old_rp.role_id, new_p.id, 'ENABLED', 0, 0
FROM sys_role_permission old_rp
JOIN sys_permission old_p ON old_p.id = old_rp.permission_id
JOIN sys_permission new_p ON new_p.code IN ('room:lease', 'room:checkout')
WHERE old_rp.deleted = 0
  AND old_rp.status = 'ENABLED'
  AND old_p.deleted = 0
  AND old_p.status = 'ENABLED'
  AND new_p.deleted = 0
  AND new_p.status = 'ENABLED'
  AND old_p.code = 'building:create'
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT old_rp.role_id, new_p.id, 'ENABLED', 0, 0
FROM sys_role_permission old_rp
JOIN sys_permission old_p ON old_p.id = old_rp.permission_id
JOIN sys_permission new_p ON new_p.code = CASE old_p.code
  WHEN 'building:*' THEN 'room:*'
  WHEN 'building:view' THEN 'room:view'
  WHEN 'building:create' THEN 'room:create'
  WHEN 'building:update' THEN 'room:update'
  WHEN 'building:delete' THEN 'room:delete'
END
WHERE old_rp.deleted = 0
  AND old_rp.status = 'ENABLED'
  AND old_p.deleted = 0
  AND old_p.status = 'ENABLED'
  AND new_p.deleted = 0
  AND new_p.status = 'ENABLED'
  AND old_p.code IN ('building:*', 'building:view', 'building:create', 'building:update', 'building:delete')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;
