CREATE TABLE energy_meter (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '业务编号',
    meter_no VARCHAR(64) NOT NULL COMMENT '表具编号',
    meter_name VARCHAR(128) NOT NULL COMMENT '表具名称',
    meter_type VARCHAR(32) NOT NULL COMMENT '表具类型 ELECTRIC/WATER/GAS',
    building_id BIGINT DEFAULT NULL COMMENT '楼宇ID',
    floor_id BIGINT DEFAULT NULL COMMENT '楼层ID',
    room_id BIGINT DEFAULT NULL COMMENT '房间ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    unit VARCHAR(32) NOT NULL DEFAULT 'kWh' COMMENT '计量单位',
    multiplier DECIMAL(12,4) NOT NULL DEFAULT 1.0000 COMMENT '倍率',
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    updated_by BIGINT DEFAULT NULL COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_energy_meter_code (code, deleted),
    UNIQUE KEY uk_energy_meter_no (meter_no, deleted),
    KEY idx_energy_meter_type (meter_type),
    KEY idx_energy_meter_building (building_id),
    KEY idx_energy_meter_floor (floor_id),
    KEY idx_energy_meter_room (room_id),
    KEY idx_energy_meter_tenant (tenant_id),
    KEY idx_energy_meter_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能耗表具表';

CREATE TABLE energy_reading (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '业务编号',
    meter_id BIGINT NOT NULL COMMENT '表具ID',
    period_month VARCHAR(7) NOT NULL COMMENT '账期月份 YYYY-MM',
    reading_date DATE NOT NULL COMMENT '抄表日期',
    previous_reading DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '上期读数',
    current_reading DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '本次读数',
    usage_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '本期用量',
    multiplier DECIMAL(12,4) NOT NULL DEFAULT 1.0000 COMMENT '倍率',
    unit_price DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '单价',
    settlement_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT '结算金额',
    operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    bill_status VARCHAR(32) NOT NULL DEFAULT 'NOT_GENERATED' COMMENT '账单状态',
    bill_id BIGINT DEFAULT NULL COMMENT '关联账单ID',
    abnormal_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否异常',
    abnormal_reason VARCHAR(64) DEFAULT NULL COMMENT '异常原因',
    abnormal_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '异常处理状态',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    updated_by BIGINT DEFAULT NULL COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_energy_reading_code (code, deleted),
    UNIQUE KEY uk_energy_reading_meter_period (meter_id, period_month, deleted),
    KEY idx_energy_reading_meter (meter_id),
    KEY idx_energy_reading_period (period_month),
    KEY idx_energy_reading_date (reading_date),
    KEY idx_energy_reading_bill_status (bill_status),
    KEY idx_energy_reading_abnormal (abnormal_flag, abnormal_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能耗抄表记录表';

SET @menu_energy = (SELECT id FROM sys_menu WHERE code = 'energy' AND deleted = 0 LIMIT 1);

INSERT INTO sys_menu (code, parent_id, name, path, component, icon, sort_order, status, created_by, updated_by, remark)
SELECT 'energy:meter', @menu_energy, '表具管理', '/energy/meters', NULL, NULL, 70, 'ENABLED', 0, 0, '水电气表具档案'
WHERE @menu_energy IS NOT NULL
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  name = VALUES(name),
  path = VALUES(path),
  sort_order = VALUES(sort_order),
  status = 'ENABLED',
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
  ('energy:meter:view', '查看表具', 'energy', 'meter:view', 'ENABLED', 0, 0, '查看能耗表具'),
  ('energy:meter:create', '新增表具', 'energy', 'meter:create', 'ENABLED', 0, 0, '新增能耗表具'),
  ('energy:meter:update', '编辑表具', 'energy', 'meter:update', 'ENABLED', 0, 0, '编辑能耗表具'),
  ('energy:meter:delete', '删除表具', 'energy', 'meter:delete', 'ENABLED', 0, 0, '删除能耗表具'),
  ('energy:reading:update', '编辑抄表', 'energy', 'reading:update', 'ENABLED', 0, 0, '编辑抄表记录'),
  ('energy:reading:delete', '删除抄表', 'energy', 'reading:delete', 'ENABLED', 0, 0, '删除抄表记录')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  module = VALUES(module),
  action = VALUES(action),
  status = 'ENABLED',
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT r.id, m.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_menu m ON m.deleted = 0 AND m.status = 'ENABLED' AND m.code = 'energy:meter'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ADMIN', 'SUPER_ADMIN', 'OPERATION_MANAGER', 'PROPERTY_MANAGER', 'MANAGER', 'OPERATION', 'ASSET_MANAGER', 'ENGINEER', 'MAINTENANCE')
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p ON p.deleted = 0 AND p.status = 'ENABLED' AND p.module = 'energy'
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('ADMIN', 'SUPER_ADMIN', 'OPERATION_MANAGER', 'PROPERTY_MANAGER', 'MANAGER', 'OPERATION', 'ASSET_MANAGER', 'ENGINEER', 'MAINTENANCE')
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;
