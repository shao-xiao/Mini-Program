-- The role editor formerly stored finance:bill:view for the finance dashboard,
-- while the dashboard API has always required finance:dashboard:view. Preserve
-- existing role selections by adding the dedicated permission to affected roles.

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id,
       dashboard_permission.id,
       'ENABLED',
       0,
       0
FROM sys_role_permission role_permission
JOIN sys_permission legacy_permission
  ON legacy_permission.id = role_permission.permission_id
 AND legacy_permission.code = 'finance:bill:view'
 AND legacy_permission.deleted = 0
JOIN sys_permission dashboard_permission
  ON dashboard_permission.code = 'finance:dashboard:view'
 AND dashboard_permission.deleted = 0
 AND dashboard_permission.status = 'ENABLED'
WHERE role_permission.deleted = 0
  AND role_permission.status = 'ENABLED'
ON DUPLICATE KEY UPDATE
    status = 'ENABLED',
    deleted = 0,
    updated_by = 0;
