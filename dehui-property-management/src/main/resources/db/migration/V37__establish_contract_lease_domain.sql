-- Contract and lease domain V2 foundation.
-- Additive only: legacy contract.room_id and contract_room are intentionally retained.

ALTER TABLE contract
    ADD COLUMN effective_date DATE DEFAULT NULL COMMENT '合同生效日期',
    ADD COLUMN signed_at DATETIME DEFAULT NULL COMMENT '签署时间',
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    ADD COLUMN current_revision_no INT NOT NULL DEFAULT 1 COMMENT '当前条款版本',
    ADD COLUMN renewal_of_contract_id BIGINT DEFAULT NULL COMMENT '续签来源合同ID',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    ADD KEY idx_contract_status (status),
    ADD KEY idx_contract_renewal (renewal_of_contract_id);

CREATE TABLE contract_revision (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  contract_id BIGINT NOT NULL COMMENT '合同ID',
  revision_no INT NOT NULL COMMENT '版本号',
  rent_terms_snapshot JSON DEFAULT NULL COMMENT '租金条款快照',
  deposit_terms_snapshot JSON DEFAULT NULL COMMENT '押金条款快照',
  billing_terms_snapshot JSON DEFAULT NULL COMMENT '计费条款快照',
  change_reason VARCHAR(500) DEFAULT NULL COMMENT '变更原因',
  effective_from DATE NOT NULL COMMENT '版本生效日期',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (id),
  UNIQUE KEY uk_contract_revision_no (contract_id, revision_no),
  KEY idx_contract_revision_contract (contract_id),
  KEY idx_contract_revision_effective (effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同条款版本';

CREATE TABLE contract_status_history (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  contract_id BIGINT NOT NULL COMMENT '合同ID',
  from_status VARCHAR(32) DEFAULT NULL COMMENT '原状态',
  to_status VARCHAR(32) NOT NULL COMMENT '新状态',
  reason VARCHAR(500) DEFAULT NULL COMMENT '变更原因',
  operator_id BIGINT NOT NULL COMMENT '操作人',
  aggregate_version BIGINT NOT NULL COMMENT '合同聚合版本',
  request_id VARCHAR(128) DEFAULT NULL COMMENT '请求标识',
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_contract_status_history_version (contract_id, aggregate_version),
  KEY idx_contract_status_history_contract (contract_id, occurred_at),
  KEY idx_contract_status_history_operator (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同状态历史';

CREATE TABLE lease (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  lease_no VARCHAR(64) NOT NULL COMMENT '租约编号',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  contract_id BIGINT NOT NULL COMMENT '合同ID',
  contract_revision_id BIGINT DEFAULT NULL COMMENT '合同条款版本ID',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_CHECKIN' COMMENT '租约状态',
  planned_start_date DATE NOT NULL COMMENT '计划入住日期',
  planned_end_date DATE NOT NULL COMMENT '计划结束日期',
  actual_checkin_at DATETIME DEFAULT NULL COMMENT '首次实际入住时间',
  actual_checkout_at DATETIME DEFAULT NULL COMMENT '全部实际退租时间',
  checkout_reason VARCHAR(500) DEFAULT NULL COMMENT '退租原因',
  renewal_of_lease_id BIGINT DEFAULT NULL COMMENT '续租来源租约ID',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lease_no (lease_no),
  KEY idx_lease_contract (contract_id),
  KEY idx_lease_tenant (tenant_id),
  KEY idx_lease_status (status, deleted),
  KEY idx_lease_renewal (renewal_of_lease_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租约履约聚合';

CREATE TABLE lease_room (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  lease_id BIGINT NOT NULL COMMENT '租约ID',
  room_id BIGINT NOT NULL COMMENT '房间ID',
  occupancy_start_at DATETIME DEFAULT NULL COMMENT '实际占用开始时间',
  occupancy_end_at DATETIME DEFAULT NULL COMMENT '实际占用结束时间',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_CHECKIN' COMMENT '房间履约状态',
  room_code_snapshot VARCHAR(64) DEFAULT NULL COMMENT '房间编码快照',
  room_name_snapshot VARCHAR(128) DEFAULT NULL COMMENT '房间名称快照',
  building_name_snapshot VARCHAR(128) DEFAULT NULL COMMENT '楼宇名称快照',
  floor_name_snapshot VARCHAR(128) DEFAULT NULL COMMENT '楼层名称快照',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  active_room_id BIGINT GENERATED ALWAYS AS (
    CASE
      WHEN deleted = 0 AND status = 'ACTIVE' AND occupancy_end_at IS NULL THEN room_id
      ELSE NULL
    END
  ) STORED COMMENT '有效占用房间唯一键',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lease_room (lease_id, room_id, deleted),
  UNIQUE KEY uk_lease_room_active (active_room_id),
  KEY idx_lease_room_lease (lease_id, status),
  KEY idx_lease_room_room (room_id, status),
  KEY idx_lease_room_occupancy (room_id, occupancy_start_at, occupancy_end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租约房间履约事实';

CREATE TABLE lease_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  event_id CHAR(36) NOT NULL COMMENT '事件标识',
  lease_id BIGINT NOT NULL COMMENT '租约ID',
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  event_payload JSON DEFAULT NULL COMMENT '事件载荷',
  operator_id BIGINT NOT NULL COMMENT '操作人',
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lease_event_id (event_id),
  KEY idx_lease_event_lease (lease_id, occurred_at),
  KEY idx_lease_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租约领域事件';

CREATE TABLE lease_status_history (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  lease_id BIGINT NOT NULL COMMENT '租约ID',
  from_status VARCHAR(32) DEFAULT NULL COMMENT '原状态',
  to_status VARCHAR(32) NOT NULL COMMENT '新状态',
  reason VARCHAR(500) DEFAULT NULL COMMENT '变更原因',
  operator_id BIGINT NOT NULL COMMENT '操作人',
  aggregate_version BIGINT NOT NULL COMMENT '租约聚合版本',
  request_id VARCHAR(128) DEFAULT NULL COMMENT '请求标识',
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lease_status_history_version (lease_id, aggregate_version),
  KEY idx_lease_status_history_lease (lease_id, occurred_at),
  KEY idx_lease_status_history_operator (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租约状态历史';

CREATE TABLE domain_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  aggregate_type VARCHAR(64) NOT NULL COMMENT '聚合类型',
  aggregate_id BIGINT NOT NULL COMMENT '聚合ID',
  action VARCHAR(64) NOT NULL COMMENT '业务动作',
  before_snapshot JSON DEFAULT NULL COMMENT '操作前快照',
  after_snapshot JSON DEFAULT NULL COMMENT '操作后快照',
  operator_id BIGINT NOT NULL COMMENT '操作人',
  request_id VARCHAR(128) DEFAULT NULL COMMENT '请求标识',
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_domain_audit_aggregate (aggregate_type, aggregate_id, occurred_at),
  KEY idx_domain_audit_request (request_id),
  KEY idx_domain_audit_operator (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域审计日志';

CREATE TABLE outbox_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  event_id CHAR(36) NOT NULL COMMENT '事件标识',
  aggregate_type VARCHAR(64) NOT NULL COMMENT '聚合类型',
  aggregate_id BIGINT NOT NULL COMMENT '聚合ID',
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  payload JSON NOT NULL COMMENT '事件载荷',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '发布状态',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '可发布时间',
  published_at DATETIME DEFAULT NULL COMMENT '发布时间',
  last_error VARCHAR(1000) DEFAULT NULL COMMENT '最后错误',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_outbox_event_id (event_id),
  KEY idx_outbox_event_status (status, available_at),
  KEY idx_outbox_event_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务消息发件箱';

-- Seed a first immutable snapshot for historical contracts without changing business facts.
INSERT INTO contract_revision (
  contract_id, revision_no, rent_terms_snapshot, deposit_terms_snapshot,
  billing_terms_snapshot, change_reason, effective_from, created_by, updated_by
)
SELECT c.id,
       1,
       JSON_OBJECT('rentAmount', c.rent_amount, 'currency', c.currency),
       JSON_OBJECT('depositAmount', c.deposit_amount, 'currency', c.currency),
       JSON_OBJECT(),
       'V37 baseline snapshot',
       COALESCE(c.effective_date, c.start_date),
       COALESCE(c.created_by, 0),
       COALESCE(c.updated_by, 0)
FROM contract c
WHERE NOT EXISTS (
  SELECT 1 FROM contract_revision cr WHERE cr.contract_id = c.id AND cr.revision_no = 1
);

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
  ('contract:approve', '审批合同', 'contract', 'approve', 'ENABLED', 0, 0, '审批待审合同'),
  ('contract:activate', '生效合同', 'contract', 'activate', 'ENABLED', 0, 0, '使合同进入履约状态'),
  ('contract:terminate', '终止合同', 'contract', 'terminate', 'ENABLED', 0, 0, '终止履约中的合同'),
  ('contract:cancel', '取消合同', 'contract', 'cancel', 'ENABLED', 0, 0, '取消未生效合同'),
  ('lease:checkin', '办理入住', 'lease', 'checkin', 'ENABLED', 0, 0, '办理租约房间入住'),
  ('lease:checkout', '办理退租', 'lease', 'checkout', 'ENABLED', 0, 0, '办理租约房间退租'),
  ('lease:renew', '办理续租', 'lease', 'renew', 'ENABLED', 0, 0, '基于原租约创建续租事实'),
  ('lease:terminate', '终止租约', 'lease', 'terminate', 'ENABLED', 0, 0, '终止租约并释放房间')
ON DUPLICATE KEY UPDATE
  name = VALUES(name), module = VALUES(module), action = VALUES(action),
  status = VALUES(status), deleted = 0, updated_by = 0, remark = VALUES(remark);

-- Preserve the effective access of existing business roles while splitting old
-- coarse permissions into dedicated lifecycle permissions.
INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT legacy.role_id, target.id, 'ENABLED', 0, 0
FROM (
  SELECT rp.role_id, 'contract:approve' AS target_code
  FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND p.deleted = 0 AND p.code = 'contract:create'
  UNION ALL
  SELECT rp.role_id, 'contract:activate'
  FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND p.deleted = 0 AND p.code = 'contract:create'
  UNION ALL
  SELECT rp.role_id, 'contract:terminate'
  FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND p.deleted = 0 AND p.code = 'contract:create'
  UNION ALL
  SELECT rp.role_id, 'contract:cancel'
  FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND p.deleted = 0 AND p.code = 'contract:create'
  UNION ALL
  SELECT rp.role_id, 'lease:checkin'
  FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND p.deleted = 0 AND p.code = 'room:lease'
  UNION ALL
  SELECT rp.role_id, 'lease:checkout'
  FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND p.deleted = 0 AND p.code = 'room:checkout'
  UNION ALL
  SELECT rp.role_id, 'lease:terminate'
  FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND p.deleted = 0 AND p.code = 'room:checkout'
  UNION ALL
  SELECT rp.role_id, 'lease:renew'
  FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND p.deleted = 0 AND p.code = 'lease:create'
) legacy
JOIN sys_permission target ON target.code = legacy.target_code AND target.deleted = 0
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

-- Administrators receive every newly introduced permission. Existing non-admin
-- assignments remain untouched and can be granted through the RBAC workspace.
INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ADMIN', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;
