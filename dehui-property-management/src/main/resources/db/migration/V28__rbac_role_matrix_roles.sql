-- RBAC role matrix support.
-- Additive and idempotent: inserts missing matrix roles/permissions only and
-- keeps existing role assignments intact.

INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark)
VALUES
  ('SUPER_ADMIN', 'Super Admin', 'SYSTEM', 'ENABLED', 0, 0, 'Full platform menu and operation access'),
  ('INVESTMENT_MANAGER', 'Investment Manager', 'BUSINESS', 'ENABLED', 0, 0, 'Manages listings, leads, contracts and tenants'),
  ('ACCOUNTANT', 'Accountant', 'BUSINESS', 'ENABLED', 0, 0, 'Handles bill checking and finance statistics'),
  ('CASHIER', 'Cashier', 'BUSINESS', 'ENABLED', 0, 0, 'Handles payment confirmation'),
  ('ENGINEER_MANAGER', 'Engineer Manager', 'BUSINESS', 'ENABLED', 0, 0, 'Manages work orders, inspections and energy operations'),
  ('SECURITY_MANAGER', 'Security Manager', 'BUSINESS', 'ENABLED', 0, 0, 'Manages visitor, parking and security operations')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  role_type = VALUES(role_type),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
  ('energy:update', 'Update Energy Price', 'energy', 'update', 'ENABLED', 0, 0, 'Maintain energy billing period prices'),
  ('energy:meter:view', 'View Energy Meter', 'energy', 'meter:view', 'ENABLED', 0, 0, 'View energy meters'),
  ('energy:meter:create', 'Create Energy Meter', 'energy', 'meter:create', 'ENABLED', 0, 0, 'Create energy meters'),
  ('energy:meter:update', 'Update Energy Meter', 'energy', 'meter:update', 'ENABLED', 0, 0, 'Update energy meters'),
  ('energy:meter:delete', 'Delete Energy Meter', 'energy', 'meter:delete', 'ENABLED', 0, 0, 'Delete energy meters'),
  ('energy:reading:update', 'Update Energy Reading', 'energy', 'reading:update', 'ENABLED', 0, 0, 'Update reading records, withdraw bills or mark posted'),
  ('energy:reading:delete', 'Delete Energy Reading', 'energy', 'reading:delete', 'ENABLED', 0, 0, 'Delete reading records'),
  ('inspection:create', 'Create Inspection', 'inspection', 'create', 'ENABLED', 0, 0, 'Create inspection records'),
  ('inspection:update', 'Update Inspection', 'inspection', 'update', 'ENABLED', 0, 0, 'Update or close inspection records'),
  ('mobile:update', 'Update Mini Program User', 'mobile', 'update', 'ENABLED', 0, 0, 'Bind or unbind mini program users'),
  ('parking-bill:update', 'Update Parking Bill', 'parking-bill', 'update', 'ENABLED', 0, 0, 'Update or void parking bills'),
  ('dashboard:view', 'View Dashboard', 'dashboard', 'view', 'ENABLED', 0, 0, 'View admin dashboard')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  module = VALUES(module),
  action = VALUES(action),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

-- Admin roles must never be locked out of newly added modules or operation codes.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ADMIN', 'SUPER_ADMIN')
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
  AND r.code IN ('ADMIN', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;
