-- Register the concrete permission already used by the work-order UI and interceptor.
INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES ('workorder:update', '编辑工单', 'workorder', 'update', 'ENABLED', 0, 0, '开始处理或调整工单状态')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  module = VALUES(module),
  action = VALUES(action),
  status = 'ENABLED',
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

SET @workorder_update_permission_id = (
  SELECT id
  FROM sys_permission
  WHERE code = 'workorder:update' AND deleted = 0
  LIMIT 1
);

-- Roles that can already assign or close work orders retain status-management access
-- after their legacy workorder:* grant is removed.
INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT rp.role_id, @workorder_update_permission_id, 'ENABLED', 0, 0
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
JOIN sys_role r ON r.id = rp.role_id AND r.deleted = 0 AND r.status = 'ENABLED'
WHERE rp.deleted = 0
  AND rp.status = 'ENABLED'
  AND p.code IN ('workorder:assign', 'workorder:close')
  AND @workorder_update_permission_id IS NOT NULL
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;
