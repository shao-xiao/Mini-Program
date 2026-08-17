CREATE TABLE IF NOT EXISTS custom_field_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    module VARCHAR(64) NOT NULL,
    field_key VARCHAR(96) NOT NULL,
    label VARCHAR(100) NOT NULL,
    field_type VARCHAR(32) NOT NULL DEFAULT 'text',
    options_json TEXT NULL,
    default_value TEXT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    sortable TINYINT(1) NOT NULL DEFAULT 1,
    groupable TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_custom_field_definition_code (code),
    UNIQUE KEY uk_custom_field_definition_module_key (module, field_key),
    KEY idx_custom_field_definition_module (module, deleted, sort_order)
);

CREATE TABLE IF NOT EXISTS custom_field_value (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module VARCHAR(64) NOT NULL,
    record_id BIGINT NOT NULL,
    field_key VARCHAR(96) NOT NULL,
    value_json TEXT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_custom_field_value_record_field (module, record_id, field_key),
    KEY idx_custom_field_value_record (module, record_id, deleted)
);
