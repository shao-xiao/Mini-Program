-- Upgrade the V18 custom-field storage in place. Business tables remain unchanged.
ALTER TABLE custom_field_definition
    ADD COLUMN workspace_key VARCHAR(64) NULL AFTER module,
    ADD COLUMN entity_type VARCHAR(64) NULL AFTER workspace_key,
    ADD COLUMN description VARCHAR(500) NULL AFTER label,
    ADD COLUMN required_flag TINYINT(1) NOT NULL DEFAULT 0 AFTER description,
    ADD COLUMN unique_value TINYINT(1) NOT NULL DEFAULT 0 AFTER required_flag,
    ADD COLUMN config_json JSON NULL AFTER default_value,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER config_json;

UPDATE custom_field_definition
SET workspace_key = module,
    entity_type = CASE module
        WHEN 'parking-spaces' THEN 'parking_space'
        WHEN 'parking-bills' THEN 'parking_bill'
        WHEN 'bills' THEN 'bill'
        WHEN 'equipment' THEN 'equipment'
        WHEN 'tenants' THEN 'tenant'
        WHEN 'contracts' THEN 'contract'
        WHEN 'workorders' THEN 'work_order'
        WHEN 'meeting-rooms' THEN 'meeting_room'
        WHEN 'energy-meters' THEN 'energy_meter'
        WHEN 'energy-records' THEN 'energy_reading'
        ELSE REPLACE(module, '-', '_')
    END,
    field_type = CASE field_type
        WHEN 'text' THEN 'TEXT'
        WHEN 'number' THEN 'NUMBER'
        WHEN 'money' THEN 'CURRENCY'
        WHEN 'singleSelect' THEN 'SINGLE_SELECT'
        WHEN 'multiSelect' THEN 'MULTI_SELECT'
        WHEN 'date' THEN 'DATE'
        WHEN 'datetime' THEN 'DATETIME'
        WHEN 'checkbox' THEN 'BOOLEAN'
        WHEN 'phone' THEN 'PHONE'
        WHEN 'email' THEN 'EMAIL'
        WHEN 'url' THEN 'URL'
        ELSE UPPER(field_type)
    END
WHERE workspace_key IS NULL OR entity_type IS NULL;

ALTER TABLE custom_field_definition
    MODIFY COLUMN workspace_key VARCHAR(64) NOT NULL,
    MODIFY COLUMN entity_type VARCHAR(64) NOT NULL,
    ADD KEY idx_custom_field_definition_workspace (workspace_key, status, deleted, sort_order),
    ADD KEY idx_custom_field_definition_entity (entity_type, status, deleted);

CREATE TABLE custom_field_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    field_id BIGINT NOT NULL,
    option_code VARCHAR(64) NOT NULL,
    option_label VARCHAR(100) NOT NULL,
    color VARCHAR(32) NULL,
    display_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_custom_field_option_code (field_id, option_code),
    KEY idx_custom_field_option_field (field_id, status, deleted, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE custom_field_value
    ADD COLUMN field_id BIGINT NULL AFTER id,
    ADD COLUMN workspace_key VARCHAR(64) NULL AFTER module,
    ADD COLUMN entity_type VARCHAR(64) NULL AFTER workspace_key,
    ADD COLUMN entity_id BIGINT NULL AFTER record_id,
    ADD COLUMN value_text VARCHAR(1024) NULL AFTER value_json,
    ADD COLUMN value_long_text LONGTEXT NULL AFTER value_text,
    ADD COLUMN value_number DECIMAL(20,6) NULL AFTER value_long_text,
    ADD COLUMN value_decimal DECIMAL(18,2) NULL AFTER value_number,
    ADD COLUMN value_date DATE NULL AFTER value_decimal,
    ADD COLUMN value_datetime DATETIME NULL AFTER value_date,
    ADD COLUMN value_boolean TINYINT(1) NULL AFTER value_datetime,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER value_boolean;

UPDATE custom_field_value value_record
JOIN custom_field_definition field_definition
  ON field_definition.module = value_record.module
 AND field_definition.field_key = value_record.field_key
SET value_record.field_id = field_definition.id,
    value_record.workspace_key = field_definition.workspace_key,
    value_record.entity_type = field_definition.entity_type,
    value_record.entity_id = value_record.record_id
WHERE value_record.field_id IS NULL;

-- Legacy orphan rows remain readable through V18 columns. New writes require a registered definition.
ALTER TABLE custom_field_value
    ADD COLUMN active_entity_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN entity_id ELSE NULL END) STORED AFTER entity_id,
    ADD UNIQUE KEY uk_custom_field_value_active_entity (field_id, entity_type, active_entity_id),
    ADD KEY idx_custom_field_value_field_entity (field_id, entity_id),
    ADD KEY idx_custom_field_value_text (field_id, value_text(191)),
    ADD KEY idx_custom_field_value_number (field_id, value_number),
    ADD KEY idx_custom_field_value_decimal (field_id, value_decimal),
    ADD KEY idx_custom_field_value_date (field_id, value_date),
    ADD KEY idx_custom_field_value_datetime (field_id, value_datetime),
    ADD KEY idx_custom_field_value_boolean (field_id, value_boolean);

CREATE TABLE custom_field_definition_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    field_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    before_snapshot LONGTEXT NULL,
    after_snapshot LONGTEXT NULL,
    operator_id BIGINT NOT NULL DEFAULT 0,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_id VARCHAR(96) NULL,
    KEY idx_custom_field_history_field (field_id, occurred_at),
    KEY idx_custom_field_history_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
    ('custom-field:view', '查看自定义字段', 'custom-field', 'view', 'ENABLED', 0, 0, '查看有权业务工作区的自定义字段定义和值'),
    ('custom-field:manage', '管理自定义字段', 'custom-field', 'manage', 'ENABLED', 0, 0, '创建、修改、停用和排序自定义字段定义')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), module = VALUES(module), action = VALUES(action),
    status = 'ENABLED', deleted = 0, updated_by = 0, remark = VALUES(remark);

-- SUPER_ADMIN keeps wildcard access; ADMIN receives these permissions explicitly.
INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT role_record.id, permission_record.id, 'ENABLED', 0, 0
FROM sys_role role_record
JOIN sys_permission permission_record
  ON permission_record.deleted = 0
 AND permission_record.status = 'ENABLED'
 AND permission_record.code IN ('custom-field:view', 'custom-field:manage')
WHERE role_record.deleted = 0
  AND role_record.status = 'ENABLED'
  AND role_record.code IN ('ADMIN', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;
