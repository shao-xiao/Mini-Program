-- Bind the real RBAC test account to the existing engineer role.
SET @tech_user_id = (
  SELECT id
  FROM sys_user
  WHERE username = 'tech' AND deleted = 0
  LIMIT 1
);

SET @engineer_role_id = (
  SELECT id
  FROM sys_role
  WHERE code = 'ENGINEER' AND deleted = 0
  LIMIT 1
);

DELETE ur
FROM sys_user_role ur
WHERE ur.user_id = @tech_user_id
  AND ur.role_id <> @engineer_role_id;

INSERT INTO sys_user_role (user_id, role_id, status, created_by, updated_by)
SELECT @tech_user_id, @engineer_role_id, 'ENABLED', 0, 0
WHERE @tech_user_id IS NOT NULL AND @engineer_role_id IS NOT NULL
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

-- Preserve each business role's effective access by expanding wildcard grants
-- to the enabled concrete permission codes that already exist in the catalog.
INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT wildcard_grant.role_id, concrete_permission.id, 'ENABLED', 0, 0
FROM (
  SELECT rp.role_id, p.code AS wildcard_code
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id AND r.deleted = 0
  JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
  WHERE rp.deleted = 0
    AND rp.status = 'ENABLED'
    AND r.code <> 'SUPER_ADMIN'
    AND p.code LIKE '%:*'
) wildcard_grant
JOIN sys_permission concrete_permission
  ON concrete_permission.code LIKE CONCAT(LEFT(wildcard_grant.wildcard_code, LENGTH(wildcard_grant.wildcard_code) - 1), '%')
 AND concrete_permission.code NOT LIKE '%:*'
 AND concrete_permission.deleted = 0
 AND concrete_permission.status = 'ENABLED'
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id AND r.deleted = 0
JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0
WHERE r.code <> 'SUPER_ADMIN'
  AND p.code LIKE '%:*';
