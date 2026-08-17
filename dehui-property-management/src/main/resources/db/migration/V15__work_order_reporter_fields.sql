SET @has_reporter_name := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'work_order'
    AND column_name = 'reporter_name'
);
SET @sql := IF(
  @has_reporter_name = 0,
  'ALTER TABLE work_order ADD COLUMN reporter_name VARCHAR(64) DEFAULT NULL COMMENT ''报修人姓名'' AFTER submitter_mobile_user_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_reporter_phone := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'work_order'
    AND column_name = 'reporter_phone'
);
SET @sql := IF(
  @has_reporter_phone = 0,
  'ALTER TABLE work_order ADD COLUMN reporter_phone VARCHAR(32) DEFAULT NULL COMMENT ''报修人电话'' AFTER reporter_name',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE work_order w
LEFT JOIN sys_user creator ON creator.id = w.created_by AND creator.deleted = 0
LEFT JOIN mobile_user mu ON mu.id = w.submitter_mobile_user_id AND mu.deleted = 0
LEFT JOIN mobile_user_bind mub ON mub.id = (
  SELECT b.id
  FROM mobile_user_bind b
  WHERE b.mobile_user_id = w.submitter_mobile_user_id
    AND b.bind_type = 'TENANT'
    AND b.status = 'ENABLED'
    AND b.deleted = 0
  ORDER BY b.id DESC
  LIMIT 1
)
LEFT JOIN tenant_contact tc ON tc.id = mub.tenant_contact_id AND tc.deleted = 0
LEFT JOIN tenant t ON t.id = w.tenant_id AND t.deleted = 0
SET w.reporter_name = COALESCE(NULLIF(w.reporter_name, ''), tc.name, t.contact_person, mu.nickname, creator.real_name, creator.username),
    w.reporter_phone = COALESCE(NULLIF(w.reporter_phone, ''), tc.phone, t.contact_phone, mu.phone, creator.phone)
WHERE w.deleted = 0
  AND (
    w.reporter_name IS NULL OR w.reporter_name = ''
    OR w.reporter_phone IS NULL OR w.reporter_phone = ''
  );
