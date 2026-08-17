INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES ('dashboard:view', '查看驾驶舱', 'dashboard', 'view', 'ENABLED', 0, 0, '查看后台驾驶舱')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  module = VALUES(module),
  action = VALUES(action),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

SET @dashboard_permission_id = (
  SELECT id FROM sys_permission
  WHERE code = 'dashboard:view' AND deleted = 0
  LIMIT 1
);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT rm.role_id, @dashboard_permission_id, 'ENABLED', 0, 0
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND m.code = 'dashboard'
  AND @dashboard_permission_id IS NOT NULL
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;
