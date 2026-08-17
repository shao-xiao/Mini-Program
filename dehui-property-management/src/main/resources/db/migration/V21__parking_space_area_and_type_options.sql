ALTER TABLE parking_space
  ADD COLUMN area VARCHAR(64) NOT NULL DEFAULT 'A' COMMENT '停车区域' AFTER space_no;

UPDATE parking_space
SET area = CASE
  WHEN location LIKE 'A%' THEN 'A'
  WHEN location LIKE 'B%' THEN 'B'
  WHEN location LIKE 'C%' THEN 'C'
  WHEN location LIKE 'D%' THEN 'D'
  ELSE 'A'
END
WHERE deleted = 0 AND (area IS NULL OR area = '');

CREATE TABLE parking_space_type_option (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  code VARCHAR(64) NOT NULL COMMENT '类型编码',
  label VARCHAR(100) NOT NULL COMMENT '类型名称',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_parking_space_type_code (code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车位类型选项';

INSERT INTO parking_space_type_option (code, label, sort_order, status, created_by, updated_by, remark)
VALUES
  ('NORMAL', '普通车位', 10, 'ENABLED', 0, 0, '系统预置'),
  ('TRUCK', '货车车位', 20, 'ENABLED', 0, 0, '系统预置'),
  ('DEDICATED', '专用车位', 30, 'ENABLED', 0, 0, '系统预置'),
  ('MONTHLY', '月租车位', 40, 'ENABLED', 0, 0, '系统预置'),
  ('TEMPORARY', '临停车位', 50, 'ENABLED', 0, 0, '系统预置'),
  ('CHARGING_FAST', '充电车位（快充）', 60, 'ENABLED', 0, 0, '系统预置'),
  ('CHARGING_SLOW', '充电车位（慢充）', 70, 'ENABLED', 0, 0, '系统预置'),
  ('MECHANICAL', '机械车位', 80, 'ENABLED', 0, 0, '系统预置')
ON DUPLICATE KEY UPDATE
  label = VALUES(label),
  sort_order = VALUES(sort_order),
  status = VALUES(status),
  updated_by = VALUES(updated_by);
