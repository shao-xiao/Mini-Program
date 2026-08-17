-- DHIC 资产基础数据与统一财务计费依据闭环。
-- 本迁移只做加法和兼容性回填，不删除、不覆盖历史账单或历史业务记录。

ALTER TABLE building_room
    ADD COLUMN property_fee_area DECIMAL(10,2) NULL
        COMMENT '物业费计费面积；历史及新计费计划必须保存快照' AFTER area;

UPDATE building_room
SET property_fee_area = area
WHERE property_fee_area IS NULL;

ALTER TABLE building_room
    MODIFY COLUMN property_fee_area DECIMAL(10,2) NOT NULL DEFAULT 0
        COMMENT '物业费计费面积；历史及新计费计划必须保存快照',
    DROP INDEX uk_room_no,
    ADD UNIQUE KEY uk_room_floor_no (floor_id, room_no, deleted);

ALTER TABLE equipment
    ADD COLUMN serial_no VARCHAR(100) NULL COMMENT '设备序列号' AFTER model,
    ADD COLUMN building_id BIGINT NULL COMMENT '所属楼宇ID' AFTER location,
    ADD COLUMN floor_id BIGINT NULL COMMENT '所属楼层ID' AFTER building_id,
    ADD COLUMN room_id BIGINT NULL COMMENT '所属房间ID' AFTER floor_id,
    ADD COLUMN location_type VARCHAR(32) NOT NULL DEFAULT 'PUBLIC_AREA'
        COMMENT '标准位置类型 BUILDING/FLOOR/ROOM/PUBLIC_AREA' AFTER room_id,
    ADD COLUMN responsible_department VARCHAR(100) NULL COMMENT '责任部门' AFTER install_date,
    ADD COLUMN responsible_user_id BIGINT NULL COMMENT '责任人系统用户ID' AFTER responsible_department,
    ADD COLUMN service_life_years INT NULL COMMENT '使用年限' AFTER responsible_user_id,
    ADD COLUMN image_file_ids JSON NULL COMMENT '统一图片文件ID列表' AFTER service_life_years,
    ADD COLUMN attachment_file_ids JSON NULL COMMENT '统一附件文件ID列表' AFTER image_file_ids,
    ADD KEY idx_equipment_building (building_id),
    ADD KEY idx_equipment_floor (floor_id),
    ADD KEY idx_equipment_room (room_id),
    ADD KEY idx_equipment_responsible_user (responsible_user_id);

ALTER TABLE energy_meter
    ADD COLUMN location_description VARCHAR(255) NULL COMMENT '安装位置补充说明' AFTER room_id,
    ADD COLUMN initial_reading DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '启用初始读数' AFTER multiplier,
    ADD COLUMN commissioning_date DATE NULL COMMENT '启用日期' AFTER initial_reading;

CREATE TABLE energy_meter_binding (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '绑定业务编号',
    meter_id BIGINT NOT NULL COMMENT '表具ID',
    subject_type VARCHAR(32) NOT NULL COMMENT 'PROPERTY/TENANT/ROOM/PUBLIC_AREA',
    tenant_id BIGINT NULL COMMENT '租户ID，仅 TENANT 主体使用',
    room_id BIGINT NULL COMMENT '关联房间ID',
    effective_from DATE NOT NULL COMMENT '生效日期',
    effective_to DATE NULL COMMENT '失效日期，含当日',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ENDED/CANCELLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_energy_meter_binding_code (code, deleted),
    KEY idx_energy_meter_binding_meter_period (meter_id, effective_from, effective_to, deleted),
    KEY idx_energy_meter_binding_tenant_period (tenant_id, effective_from, effective_to, deleted),
    KEY idx_energy_meter_binding_room_period (room_id, effective_from, effective_to, deleted),
    CONSTRAINT chk_energy_meter_binding_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表具计量主体绑定历史';

-- 将当前表具上的租户关系转成首条历史绑定；保留原 tenant_id 供旧接口兼容读取。
INSERT INTO energy_meter_binding (
    code, meter_id, subject_type, tenant_id, room_id, effective_from, effective_to,
    status, created_by, updated_by, remark
)
SELECT CONCAT('EMB-MIG-', m.id),
       m.id,
       CASE WHEN m.tenant_id IS NULL THEN
           CASE WHEN m.room_id IS NULL THEN 'PUBLIC_AREA' ELSE 'PROPERTY' END
           ELSE 'TENANT'
       END,
       m.tenant_id,
       m.room_id,
       COALESCE(m.commissioning_date, DATE(m.created_at)),
       NULL,
       'ACTIVE',
       0,
       0,
       'V45 从 energy_meter 当前关系兼容迁移'
FROM energy_meter m
WHERE m.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM energy_meter_binding b
      WHERE b.meter_id = m.id AND b.deleted = 0
  );

ALTER TABLE energy_reading
    ADD COLUMN reading_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL'
        COMMENT 'MANUAL/IMPORT/AUTO' AFTER operator_name,
    ADD COLUMN attachment_file_id BIGINT NULL COMMENT '统一附件文件ID' AFTER reading_source,
    ADD COLUMN reset_reason VARCHAR(500) NULL COMMENT '读数回退或表具归零原因' AFTER attachment_file_id,
    ADD COLUMN charge_basis_id BIGINT NULL COMMENT '统一资产计费依据ID' AFTER bill_id,
    ADD KEY idx_energy_reading_charge_basis (charge_basis_id);

CREATE TABLE energy_reading_audit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    reading_id BIGINT NOT NULL COMMENT '抄表记录ID',
    action VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/DELETE',
    before_snapshot JSON NULL COMMENT '修改前快照',
    after_snapshot JSON NULL COMMENT '修改后快照',
    reason VARCHAR(500) NULL COMMENT '修改原因',
    operator_id BIGINT NOT NULL COMMENT '操作人',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_energy_reading_audit_reading (reading_id, occurred_at),
    KEY idx_energy_reading_audit_operator (operator_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抄表修改审计';

ALTER TABLE parking_space
    ADD COLUMN monthly_rate DECIMAL(10,2) NULL COMMENT '车位月租标准' AFTER space_type;

ALTER TABLE parking_assignment
    ADD COLUMN tenant_contact_id BIGINT NULL COMMENT '绑定租户联系人ID' AFTER tenant_id,
    ADD COLUMN mobile_user_id BIGINT NULL COMMENT '绑定小程序用户ID' AFTER tenant_contact_id,
    ADD COLUMN vehicle_type VARCHAR(32) NULL COMMENT '车辆类型' AFTER car_plate_no,
    ADD COLUMN billing_rule_id BIGINT NULL COMMENT '收费规则版本或规则引用' AFTER monthly_fee,
    ADD COLUMN unit_price_snapshot DECIMAL(10,2) NULL COMMENT '绑定生效时收费标准快照' AFTER billing_rule_id,
    DROP INDEX uk_parking_active_space,
    ADD COLUMN active_parking_space_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN deleted = 0 AND status IN ('ACTIVE', 'ENABLED') THEN parking_space_id
            ELSE NULL
        END
    ) STORED COMMENT '当前有效车位绑定唯一键',
    ADD UNIQUE KEY uk_parking_assignment_active_space (active_parking_space_id),
    ADD KEY idx_parking_assignment_contact (tenant_contact_id, status, deleted),
    ADD KEY idx_parking_assignment_mobile_user (mobile_user_id, status, deleted),
    ADD KEY idx_parking_assignment_period (parking_space_id, start_date, end_date, deleted);

UPDATE parking_assignment
SET unit_price_snapshot = monthly_fee
WHERE unit_price_snapshot IS NULL;

CREATE TABLE meeting_rate_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '收费规则编号',
    meeting_room_id BIGINT NOT NULL COMMENT '会议室ID',
    charge_type VARCHAR(32) NOT NULL DEFAULT 'HOURLY' COMMENT 'FREE/HOURLY',
    hourly_price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '小时单价',
    minimum_minutes INT NOT NULL DEFAULT 60 COMMENT '最低预约分钟数',
    effective_from DATE NOT NULL COMMENT '生效日期',
    effective_to DATE NULL COMMENT '失效日期',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/RETIRED/DISABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    remark VARCHAR(500) NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meeting_rate_rule_code (code, deleted),
    KEY idx_meeting_rate_room_period (meeting_room_id, effective_from, effective_to, status, deleted),
    CONSTRAINT chk_meeting_rate_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_meeting_rate_price CHECK (hourly_price >= 0),
    CONSTRAINT chk_meeting_rate_minimum CHECK (minimum_minutes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会议室收费规则历史';

ALTER TABLE meeting_room
    ADD COLUMN facilities VARCHAR(1000) NULL COMMENT '设备设施' AFTER location,
    ADD COLUMN description VARCHAR(2000) NULL COMMENT '会议室描述' AFTER facilities,
    ADD COLUMN open_time TIME NULL COMMENT '每日开放开始时间' AFTER description,
    ADD COLUMN close_time TIME NULL COMMENT '每日开放结束时间' AFTER open_time,
    ADD COLUMN image_file_ids JSON NULL COMMENT '统一图片文件ID列表' AFTER close_time;

INSERT INTO meeting_rate_rule (
    code, meeting_room_id, charge_type, hourly_price, minimum_minutes,
    effective_from, effective_to, status, created_by, updated_by, remark
)
SELECT CONCAT('MRR-MIG-', r.id),
       r.id,
       CASE WHEN r.hourly_price > 0 THEN 'HOURLY' ELSE 'FREE' END,
       r.hourly_price,
       60,
       DATE(r.created_at),
       NULL,
       'ACTIVE',
       0,
       0,
       'V45 从 meeting_room.hourly_price 兼容迁移'
FROM meeting_room r
WHERE r.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM meeting_rate_rule rule_record
      WHERE rule_record.meeting_room_id = r.id AND rule_record.deleted = 0
  );

ALTER TABLE meeting_booking
    ADD COLUMN purpose VARCHAR(500) NULL COMMENT '会议用途' AFTER end_time,
    ADD COLUMN participant_count INT NULL COMMENT '参会人数' AFTER purpose,
    ADD COLUMN contact_name VARCHAR(64) NULL COMMENT '联系人' AFTER participant_count,
    ADD COLUMN contact_phone VARCHAR(32) NULL COMMENT '联系电话' AFTER contact_name,
    ADD COLUMN pricing_rule_id BIGINT NULL COMMENT '预约收费规则快照来源ID' AFTER contact_phone,
    ADD COLUMN unit_price_snapshot DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '预约单价快照' AFTER pricing_rule_id,
    ADD COLUMN duration_minutes INT NOT NULL DEFAULT 0 COMMENT '计费时长分钟快照' AFTER unit_price_snapshot,
    ADD COLUMN amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '预约费用快照' AFTER duration_minutes,
    ADD COLUMN charge_basis_id BIGINT NULL COMMENT '统一资产计费依据ID' AFTER amount,
    ADD KEY idx_meeting_booking_applicant (applicant_mobile_user_id, start_time, deleted),
    ADD KEY idx_meeting_booking_charge_basis (charge_basis_id);

UPDATE meeting_booking b
JOIN meeting_room r ON r.id = b.meeting_room_id
SET b.purpose = COALESCE(b.purpose, b.remark),
    b.unit_price_snapshot = r.hourly_price,
    b.duration_minutes = GREATEST(TIMESTAMPDIFF(MINUTE, b.start_time, b.end_time), 0),
    b.amount = ROUND(
        r.hourly_price * GREATEST(TIMESTAMPDIFF(MINUTE, b.start_time, b.end_time), 0) / 60,
        2
    )
WHERE b.deleted = 0;

CREATE TABLE asset_charge_basis (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '计费依据编号',
    source_type VARCHAR(32) NOT NULL COMMENT 'PROPERTY/ENERGY/PARKING/MEETING',
    source_id BIGINT NOT NULL COMMENT '来源业务记录ID',
    tenant_id BIGINT NULL COMMENT '计费租户ID；物业计量主体可为空',
    contract_id BIGINT NULL COMMENT '关联合同ID',
    room_id BIGINT NULL COMMENT '关联房间ID',
    meter_id BIGINT NULL COMMENT '关联表具ID',
    parking_space_id BIGINT NULL COMMENT '关联车位ID',
    meeting_room_id BIGINT NULL COMMENT '关联会议室ID',
    billing_period VARCHAR(16) NOT NULL COMMENT '计费周期 YYYY-MM 或业务日期',
    quantity DECIMAL(18,4) NOT NULL COMMENT '面积、用量、数量或时长',
    unit VARCHAR(32) NOT NULL COMMENT '㎡/kWh/m³/月/小时等',
    unit_price_snapshot DECIMAL(18,6) NOT NULL COMMENT '单价快照',
    amount DECIMAL(18,2) NOT NULL COMMENT '金额快照',
    status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED'
        COMMENT 'DRAFT/CONFIRMED/BILLED/CANCELLED',
    bill_id BIGINT NULL COMMENT '财务统一账单ID',
    snapshot_json JSON NULL COMMENT '来源编码、面积、主体、规则等业务快照',
    confirmed_at DATETIME NULL COMMENT '确认时间',
    billed_at DATETIME NULL COMMENT '生成财务账单时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NULL COMMENT '创建人',
    updated_by BIGINT NULL COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    remark VARCHAR(500) NULL COMMENT '备注',
    active_source_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND status <> 'CANCELLED' THEN source_id ELSE NULL END
    ) STORED COMMENT '未取消来源唯一键',
    active_billing_period VARCHAR(16) GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND status <> 'CANCELLED' THEN billing_period ELSE NULL END
    ) STORED COMMENT '未取消账期唯一键',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_charge_basis_code (code, deleted),
    UNIQUE KEY uk_asset_charge_basis_source_period
        (source_type, active_source_id, active_billing_period),
    KEY idx_asset_charge_basis_status (status, billing_period, deleted),
    KEY idx_asset_charge_basis_tenant (tenant_id, billing_period, deleted),
    KEY idx_asset_charge_basis_bill (bill_id),
    CONSTRAINT chk_asset_charge_basis_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_asset_charge_basis_unit_price CHECK (unit_price_snapshot >= 0),
    CONSTRAINT chk_asset_charge_basis_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产模块向财务提供的统一计费依据';

-- 旧能耗已生成账单建立只读兼容依据，不改动原账单。
INSERT INTO asset_charge_basis (
    code, source_type, source_id, tenant_id, meter_id, billing_period,
    quantity, unit, unit_price_snapshot, amount, status, bill_id,
    snapshot_json, confirmed_at, billed_at, created_by, updated_by, remark
)
SELECT CONCAT('ACB-ENERGY-MIG-', er.id),
       'ENERGY',
       er.id,
       m.tenant_id,
       er.meter_id,
       er.period_month,
       er.usage_amount,
       m.unit,
       er.unit_price,
       er.settlement_amount,
       'BILLED',
       er.bill_id,
       JSON_OBJECT(
           'meterNo', m.meter_no,
           'meterType', m.meter_type,
           'previousReading', er.previous_reading,
           'currentReading', er.current_reading,
           'multiplier', er.multiplier
       ),
       er.created_at,
       er.updated_at,
       COALESCE(er.created_by, 0),
       COALESCE(er.updated_by, 0),
       'V45 兼容既有能耗账单'
FROM energy_reading er
JOIN energy_meter m ON m.id = er.meter_id
WHERE er.deleted = 0
  AND er.bill_id IS NOT NULL
  AND m.tenant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM asset_charge_basis basis
      WHERE basis.source_type = 'ENERGY'
        AND basis.source_id = er.id
        AND basis.billing_period = er.period_month
        AND basis.deleted = 0
  );

UPDATE energy_reading er
JOIN asset_charge_basis basis
  ON basis.source_type = 'ENERGY'
 AND basis.source_id = er.id
 AND basis.billing_period = er.period_month
 AND basis.deleted = 0
SET er.charge_basis_id = basis.id
WHERE er.charge_basis_id IS NULL;

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
    ('asset-charge-basis:view', '查看资产计费依据', 'bill', 'charge-basis:view', 'ENABLED', 0, 0, '财务读取资产模块计费依据'),
    ('asset-charge-basis:generate', '生成统一财务账单', 'bill', 'charge-basis:generate', 'ENABLED', 0, 0, '财务根据已确认依据生成账单')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    module = VALUES(module),
    action = VALUES(action),
    status = 'ENABLED',
    deleted = 0,
    updated_by = 0,
    remark = VALUES(remark);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT r.id, p.id, 'ENABLED', 0, 0
FROM sys_role r
JOIN sys_permission p
  ON p.code IN ('asset-charge-basis:view', 'asset-charge-basis:generate')
 AND p.deleted = 0
WHERE r.deleted = 0
  AND r.status = 'ENABLED'
  AND r.code IN ('SUPER_ADMIN', 'ADMIN', 'FINANCE', 'ACCOUNTANT')
ON DUPLICATE KEY UPDATE
    status = 'ENABLED',
    deleted = 0,
    updated_by = 0;
