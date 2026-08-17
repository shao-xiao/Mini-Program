-- Complete RBAC seed data without touching business data.

INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark)
VALUES
  ('TENANT', '租户账号', 'BUSINESS', 'ENABLED', 0, 0, '租户侧账号，仅开放租户相关自助能力'),
  ('STAFF', '物业员工', 'BUSINESS', 'ENABLED', 0, 0, '普通物业员工'),
  ('SECURITY', '安保人员', 'BUSINESS', 'ENABLED', 0, 0, '安保与访客相关岗位')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  role_type = VALUES(role_type),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
  ('lease:view', '查看租约', 'lease', 'view', 'ENABLED', 0, 0, '查看租户入驻和租约'),
  ('lease:create', '新增租约', 'lease', 'create', 'ENABLED', 0, 0, '新增租户入驻和租约'),
  ('lease:update', '编辑租约', 'lease', 'update', 'ENABLED', 0, 0, '编辑租户入驻和租约'),
  ('lease:delete', '删除租约', 'lease', 'delete', 'ENABLED', 0, 0, '删除租户入驻和租约'),
  ('parking-bill:update', '更新停车账单', 'parking-bill', 'update', 'ENABLED', 0, 0, '作废或更新停车账单'),
  ('visitor:update', '处理访客预约', 'visitor', 'update', 'ENABLED', 0, 0, '处理访客预约状态'),
  ('inspection:create', '新增巡检', 'inspection', 'create', 'ENABLED', 0, 0, '新增巡检记录'),
  ('inspection:update', '编辑巡检', 'inspection', 'update', 'ENABLED', 0, 0, '编辑巡检记录')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  module = VALUES(module),
  action = VALUES(action),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

-- Admin always owns every enabled menu and permission.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0 AND r.status = 'ENABLED' AND r.code IN ('ADMIN', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0 AND r.status = 'ENABLED' AND r.code IN ('ADMIN', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

-- Property operations: all business modules except system administration.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('PROPERTY_MANAGER', 'MANAGER', 'OPERATION', 'STAFF')
  AND m.code <> 'system'
  AND m.code NOT LIKE 'system:%'
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('PROPERTY_MANAGER', 'MANAGER', 'OPERATION', 'STAFF')
  AND p.module <> 'system'
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

-- Engineer: work orders, inspections, energy, and asset lookup.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ENGINEER', 'MAINTENANCE')
  AND (m.code IN ('dashboard', 'operation', 'operation:workorder', 'operation:inspection', 'energy', 'energy:meter', 'energy:record', 'energy:stats')
       OR m.code LIKE 'asset:%'
       OR m.code = 'asset')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ENGINEER', 'MAINTENANCE')
  AND p.module IN ('workorder', 'inspection', 'energy', 'building', 'asset')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

-- Finance: bills, parking bills, finance dashboard.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('FINANCE', 'FINANCE_MANAGER')
  AND m.code IN ('dashboard', 'finance', 'finance:bill', 'finance:dashboard', 'parking', 'parking:bill', 'energy', 'energy:record', 'energy:stats')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('FINANCE', 'FINANCE_MANAGER')
  AND p.module IN ('finance', 'bill', 'parking-bill', 'energy')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

-- Security: visitors, announcements, parking lookup.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code = 'SECURITY'
  AND m.code IN ('dashboard', 'operation', 'operation:visitor', 'operation:announcement', 'parking', 'parking:space')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code = 'SECURITY'
  AND p.module IN ('visitor', 'announcement', 'parking')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

-- Tenant backend role: read-only self-service visibility if assigned to backend users.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code = 'TENANT'
  AND m.code IN ('dashboard', 'finance', 'finance:bill', 'operation', 'operation:workorder')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code = 'TENANT'
  AND p.code IN ('tenant:portal:view', 'bill:view', 'workorder:view', 'workorder:create')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;
