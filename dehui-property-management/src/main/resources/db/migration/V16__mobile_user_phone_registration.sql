SET @has_mobile_user_real_name := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'mobile_user'
    AND column_name = 'real_name'
);
SET @sql := IF(
  @has_mobile_user_real_name = 0,
  'ALTER TABLE mobile_user ADD COLUMN real_name VARCHAR(64) DEFAULT NULL COMMENT ''真实姓名'' AFTER nickname',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_mobile_user_password_hash := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'mobile_user'
    AND column_name = 'password_hash'
);
SET @sql := IF(
  @has_mobile_user_password_hash = 0,
  'ALTER TABLE mobile_user ADD COLUMN password_hash VARCHAR(255) DEFAULT NULL COMMENT ''手机号注册密码哈希'' AFTER phone',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
