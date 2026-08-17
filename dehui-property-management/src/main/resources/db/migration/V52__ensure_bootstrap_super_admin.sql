-- Avoid locking a legacy installation out of protected system-role operations.
-- Only bootstrap when no enabled SUPER_ADMIN assignment exists.
SET @super_role_id = (
  SELECT id
  FROM sys_role
  WHERE code = 'SUPER_ADMIN' AND deleted = 0 AND status = 'ENABLED'
  ORDER BY id
  LIMIT 1
);

SET @needs_super_admin = (
  SELECT CASE WHEN EXISTS (
    SELECT 1
    FROM sys_user user_record
    INNER JOIN sys_user_role user_role
      ON user_role.user_id = user_record.id
     AND user_role.deleted = 0
     AND user_role.status = 'ENABLED'
    WHERE user_role.role_id = @super_role_id
      AND user_record.deleted = 0
      AND user_record.status = 'ENABLED'
  ) THEN 0 ELSE 1 END
);

SET @bootstrap_user_id = COALESCE(
  (
    SELECT id
    FROM sys_user
    WHERE username = 'admin' AND deleted = 0 AND status = 'ENABLED'
    ORDER BY id
    LIMIT 1
  ),
  (
    SELECT user_record.id
    FROM sys_user user_record
    INNER JOIN sys_user_role user_role
      ON user_role.user_id = user_record.id
     AND user_role.deleted = 0
     AND user_role.status = 'ENABLED'
    INNER JOIN sys_role role_record
      ON role_record.id = user_role.role_id
     AND role_record.code = 'ADMIN'
     AND role_record.deleted = 0
     AND role_record.status = 'ENABLED'
    WHERE user_record.deleted = 0
      AND user_record.status = 'ENABLED'
    ORDER BY user_record.id
    LIMIT 1
  )
);

INSERT INTO sys_user_role
  (user_id, role_id, status, deleted, created_by, updated_by)
SELECT @bootstrap_user_id, @super_role_id, 'ENABLED', 0, 0, 0
WHERE @needs_super_admin = 1
  AND @bootstrap_user_id IS NOT NULL
  AND @super_role_id IS NOT NULL
ON DUPLICATE KEY UPDATE
  status = 'ENABLED',
  deleted = 0,
  updated_by = 0;

UPDATE sys_user
SET primary_role_id = @super_role_id,
    updated_by = 0
WHERE id = @bootstrap_user_id
  AND @needs_super_admin = 1
  AND @super_role_id IS NOT NULL;
