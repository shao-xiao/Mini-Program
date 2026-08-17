-- Add native inspection type dictionary and inspection-photo attachment relation.
CREATE TABLE IF NOT EXISTS inspection_type (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  code VARCHAR(64) NOT NULL COMMENT '类型编码',
  name VARCHAR(64) NOT NULL COMMENT '类型名称',
  description VARCHAR(500) DEFAULT NULL COMMENT '描述',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inspection_type_code (code),
  KEY idx_inspection_type_status (status),
  KEY idx_inspection_type_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检类型字典';

INSERT INTO inspection_type (code, name, description, status, sort_order, created_by, updated_by, deleted)
SELECT x.code,
       x.name,
       NULL,
       'ENABLED',
       x.sort_order,
       0,
       0,
       0
FROM (
  SELECT
    grouped_type.code,
    CASE
      WHEN UPPER(grouped_type.code) = 'DAILY' THEN '日常巡检'
      WHEN UPPER(grouped_type.code) = 'FIRE' THEN '消防巡检'
      WHEN UPPER(grouped_type.code) = 'SECURITY' THEN '安全巡检'
      WHEN UPPER(grouped_type.code) = 'CLEAN' THEN '清洁巡检'
      WHEN UPPER(grouped_type.code) = 'EQUIPMENT' THEN '设备巡检'
      WHEN UPPER(grouped_type.code) = 'OTHER' THEN '其他'
      ELSE grouped_type.code
    END AS name,
    ROW_NUMBER() OVER (ORDER BY grouped_type.first_id) AS sort_order
  FROM (
    SELECT TRIM(inspection_type) AS code,
           MIN(id) AS first_id
    FROM inspection_record
    WHERE deleted = 0
      AND inspection_type IS NOT NULL
      AND TRIM(inspection_type) <> ''
    GROUP BY TRIM(inspection_type)
  ) grouped_type
) x
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  status = 'ENABLED',
  deleted = 0,
  updated_by = VALUES(updated_by);

INSERT INTO inspection_type (code, name, description, status, sort_order, created_by, updated_by, deleted)
VALUES
  ('DAILY', '日常巡检', '日常巡检', 'ENABLED', 10, 0, 0, 0),
  ('FIRE', '消防巡检', '消防巡检', 'ENABLED', 20, 0, 0, 0),
  ('SECURITY', '安全巡检', '安全巡检', 'ENABLED', 30, 0, 0, 0),
  ('CLEAN', '清洁巡检', '清洁巡检', 'ENABLED', 40, 0, 0, 0),
  ('EQUIPMENT', '设备巡检', '设备巡检', 'ENABLED', 50, 0, 0, 0),
  ('OTHER', '其他', '其他', 'ENABLED', 60, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  updated_by = VALUES(updated_by),
  deleted = 0;

CREATE TABLE IF NOT EXISTS inspection_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  inspection_id BIGINT NOT NULL COMMENT '巡检ID',
  file_id BIGINT NOT NULL COMMENT '文件ID',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inspection_attachment_file (inspection_id, file_id, deleted),
  KEY idx_inspection_attachment_inspection (inspection_id, deleted),
  KEY idx_inspection_attachment_file (file_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡检附件关系';
