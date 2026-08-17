SET @menu_system = (SELECT id FROM sys_menu WHERE code = 'system' AND deleted = 0 LIMIT 1);

INSERT INTO sys_menu (code, parent_id, name, path, component, icon, sort_order, status, created_by, updated_by, remark)
SELECT 'system:mobile-user', @menu_system, '小程序用户', '/system/mobile-users', NULL, NULL, 103, 'ENABLED', 0, 0, '手机号快捷登录用户管理'
WHERE @menu_system IS NOT NULL
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  name = VALUES(name),
  path = VALUES(path),
  sort_order = VALUES(sort_order),
  status = 'ENABLED',
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED' AND m.code = 'system:mobile-user'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ADMIN', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;
