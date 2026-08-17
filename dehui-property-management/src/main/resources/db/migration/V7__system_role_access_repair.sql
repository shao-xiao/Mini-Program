-- Repair built-in RBAC defaults so role assignment dialogs can reflect existing access.
-- This migration only inserts or re-enables role-menu / role-permission relations.

-- Platform administrators own every enabled menu and permission.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ADMIN', 'SUPER_ADMIN', 'OPERATION_MANAGER')
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ADMIN', 'SUPER_ADMIN', 'OPERATION_MANAGER')
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

-- Business operation managers own all non-system business menus and permissions.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('PROPERTY_MANAGER', 'MANAGER', 'OPERATION', 'ASSET_MANAGER')
  AND m.code <> 'system'
  AND m.code NOT LIKE 'system:%'
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('PROPERTY_MANAGER', 'MANAGER', 'OPERATION', 'ASSET_MANAGER')
  AND p.module <> 'system'
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

-- Finance roles get finance and billing-related access by default.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('FINANCE', 'FINANCE_MANAGER')
  AND m.code IN ('dashboard', 'finance', 'finance:bill', 'finance:dashboard', 'parking', 'parking:bill')
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('FINANCE', 'FINANCE_MANAGER')
  AND p.module IN ('finance', 'bill', 'parking-bill')
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

-- Engineer roles get workorder, inspection, and energy-meter access by default.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ENGINEER', 'MAINTENANCE')
  AND m.code IN ('dashboard', 'operation', 'operation:workorder', 'operation:inspection', 'energy', 'energy:record', 'energy:stats')
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ENGINEER', 'MAINTENANCE')
  AND p.module IN ('workorder', 'inspection', 'energy')
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;
