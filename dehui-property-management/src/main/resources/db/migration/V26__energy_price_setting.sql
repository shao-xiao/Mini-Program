CREATE TABLE IF NOT EXISTS energy_price_setting (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
  code VARCHAR(64) NOT NULL COMMENT 'Business code',
  period_month VARCHAR(7) NOT NULL COMMENT 'Billing period YYYY-MM',
  meter_type VARCHAR(32) NOT NULL COMMENT 'Meter type ELECTRIC/WATER/GAS',
  unit_price DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT 'Monthly unit price',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'Status',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT DEFAULT NULL,
  updated_by BIGINT DEFAULT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  remark VARCHAR(500) DEFAULT NULL COMMENT 'Remark',
  UNIQUE KEY uk_energy_price_code (code),
  UNIQUE KEY uk_energy_price_period_type (period_month, meter_type),
  KEY idx_energy_price_period (period_month),
  KEY idx_energy_price_type (meter_type)
) COMMENT='Energy period unit price setting';
