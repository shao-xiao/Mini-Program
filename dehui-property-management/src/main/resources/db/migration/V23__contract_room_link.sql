CREATE TABLE contract_room (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  contract_id BIGINT NOT NULL COMMENT '合同ID',
  room_id BIGINT NOT NULL COMMENT '房间ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_contract_room_contract (contract_id),
  KEY idx_contract_room_room (room_id),
  KEY idx_contract_room_active (contract_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同房间明细表';

INSERT IGNORE INTO contract_room (contract_id, room_id, created_by, updated_by)
SELECT id, room_id, created_by, updated_by
FROM contract
WHERE deleted = 0
  AND room_id IS NOT NULL;
