-- V55 translated legacy business grants to preserve existing property-mobile
-- roles. Tenant/owner and finance-only roles must remain outside the property
-- identity boundary unless an administrator explicitly enables them later.

UPDATE sys_role_permission role_permission
INNER JOIN sys_role role_record
  ON role_record.id = role_permission.role_id
 AND role_record.deleted = 0
INNER JOIN sys_permission permission_record
  ON permission_record.id = role_permission.permission_id
 AND permission_record.module = 'staff-mobile'
 AND permission_record.deleted = 0
SET role_permission.status = 'DISABLED',
    role_permission.deleted = 1,
    role_permission.updated_by = 0
WHERE role_permission.deleted = 0
  AND role_record.code IN (
    'TENANT',
    'TENANT_ADMIN',
    'TENANT_STAFF',
    'FINANCE',
    'ACCOUNTANT',
    'CASHIER'
  );
