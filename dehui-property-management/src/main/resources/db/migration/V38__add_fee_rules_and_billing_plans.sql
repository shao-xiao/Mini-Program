-- Stage 2: versioned fee rules and auditable billing-plan snapshots.
-- This migration is additive. Legacy /feerules metadata remains available for compatibility.

CREATE TABLE fee_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  rule_code VARCHAR(64) NOT NULL COMMENT '规则业务编号',
  rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
  fee_type VARCHAR(32) NOT NULL COMMENT '费用类型',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/DISABLED',
  current_version_id BIGINT DEFAULT NULL COMMENT '最新版本ID',
  current_version_no INT NOT NULL DEFAULT 1 COMMENT '最新版本号',
  active_version_id BIGINT DEFAULT NULL COMMENT '当前生效版本ID',
  active_version_no INT DEFAULT NULL COMMENT '当前生效版本号',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  description VARCHAR(500) DEFAULT NULL COMMENT '说明',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL COMMENT '创建人',
  updated_by BIGINT NOT NULL COMMENT '更新人',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fee_rule_code (rule_code, deleted),
  KEY idx_fee_rule_status (status, deleted),
  KEY idx_fee_rule_fee_type (fee_type, status),
  KEY idx_fee_rule_name (rule_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则聚合';

CREATE TABLE fee_rule_version (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  fee_rule_id BIGINT NOT NULL COMMENT '收费规则ID',
  version_no INT NOT NULL COMMENT '规则版本号',
  calculation_type VARCHAR(32) NOT NULL COMMENT '金额计算类型',
  billing_cycle VARCHAR(32) NOT NULL COMMENT '计费周期',
  effective_from DATE NOT NULL COMMENT '生效开始日',
  effective_to DATE DEFAULT NULL COMMENT '生效结束日',
  fixed_amount DECIMAL(18,2) DEFAULT NULL COMMENT '固定金额快照',
  unit_price DECIMAL(18,6) DEFAULT NULL COMMENT '单价快照',
  currency CHAR(3) NOT NULL COMMENT '币种',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/RETIRED',
  activated_at DATETIME DEFAULT NULL COMMENT '生效时间',
  activated_by BIGINT DEFAULT NULL COMMENT '生效操作人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  created_by BIGINT NOT NULL COMMENT '创建人',
  active_rule_id BIGINT GENERATED ALWAYS AS (
    CASE WHEN status = 'ACTIVE' THEN fee_rule_id ELSE NULL END
  ) STORED COMMENT '同一规则仅一个当前生效版本',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fee_rule_version (fee_rule_id, version_no),
  UNIQUE KEY uk_fee_rule_version_active (active_rule_id),
  KEY idx_fee_rule_version_effective (fee_rule_id, effective_from, effective_to),
  KEY idx_fee_rule_version_status (status, effective_from),
  CONSTRAINT chk_fee_rule_version_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
  CONSTRAINT chk_fee_rule_version_fixed CHECK (fixed_amount IS NULL OR fixed_amount > 0),
  CONSTRAINT chk_fee_rule_version_unit CHECK (unit_price IS NULL OR unit_price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变收费规则版本';

CREATE TABLE billing_plan (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  plan_no VARCHAR(64) NOT NULL COMMENT '计费计划业务编号',
  plan_name VARCHAR(128) NOT NULL COMMENT '计费计划名称',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  contract_id BIGINT NOT NULL COMMENT '合同ID',
  lease_id BIGINT DEFAULT NULL COMMENT '租约ID，可在入住前为空',
  effective_from DATE NOT NULL COMMENT '计划开始日',
  effective_to DATE NOT NULL COMMENT '计划结束日',
  billing_cycle VARCHAR(32) NOT NULL COMMENT '计费周期',
  billing_lead_days INT NOT NULL DEFAULT 0 COMMENT '提前生成天数',
  due_rule VARCHAR(32) NOT NULL COMMENT '到期日规则',
  currency CHAR(3) NOT NULL COMMENT '币种',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/TERMINATED',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  activated_at DATETIME DEFAULT NULL COMMENT '激活时间',
  activated_by BIGINT DEFAULT NULL COMMENT '激活人',
  terminated_at DATETIME DEFAULT NULL COMMENT '终止时间',
  terminated_by BIGINT DEFAULT NULL COMMENT '终止人',
  termination_reason VARCHAR(500) DEFAULT NULL COMMENT '终止原因',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL COMMENT '创建人',
  updated_by BIGINT NOT NULL COMMENT '更新人',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_billing_plan_no (plan_no, deleted),
  KEY idx_billing_plan_tenant (tenant_id, status, effective_from),
  KEY idx_billing_plan_contract (contract_id, status),
  KEY idx_billing_plan_lease (lease_id, status),
  KEY idx_billing_plan_status (status, effective_from, effective_to),
  CONSTRAINT chk_billing_plan_dates CHECK (effective_to >= effective_from),
  CONSTRAINT chk_billing_plan_lead_days CHECK (billing_lead_days BETWEEN 0 AND 365)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费计划聚合';

CREATE TABLE billing_plan_item (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  billing_plan_id BIGINT NOT NULL COMMENT '计费计划ID',
  fee_rule_version_id BIGINT NOT NULL COMMENT '不可变收费规则版本ID',
  item_name VARCHAR(128) NOT NULL COMMENT '条目名称快照',
  quantity DECIMAL(18,4) DEFAULT NULL COMMENT '面积或数量',
  unit_price DECIMAL(18,6) DEFAULT NULL COMMENT '计划单价快照',
  fixed_amount DECIMAL(18,2) DEFAULT NULL COMMENT '计划固定金额快照',
  amount DECIMAL(18,2) NOT NULL COMMENT '单周期金额快照',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  created_by BIGINT NOT NULL COMMENT '创建人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_billing_plan_item_rule (billing_plan_id, fee_rule_version_id),
  KEY idx_billing_plan_item_plan (billing_plan_id, sort_order),
  KEY idx_billing_plan_item_rule_version (fee_rule_version_id),
  CONSTRAINT chk_billing_plan_item_amount CHECK (amount > 0),
  CONSTRAINT chk_billing_plan_item_quantity CHECK (quantity IS NULL OR quantity > 0),
  CONSTRAINT chk_billing_plan_item_unit_price CHECK (unit_price IS NULL OR unit_price > 0),
  CONSTRAINT chk_billing_plan_item_fixed_amount CHECK (fixed_amount IS NULL OR fixed_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费计划条目快照';

SET @lease_menu_id = (SELECT id FROM sys_menu WHERE code = 'lease' AND deleted = 0 LIMIT 1);

INSERT INTO sys_menu (
  code, parent_id, name, path, component, icon, sort_order,
  status, created_by, updated_by, remark
)
VALUES
  ('lease:fee-rule-v2', @lease_menu_id, '收费规则', '/fee-rules', NULL, NULL, 36,
   'ENABLED', 0, 0, '版本化收费规则工作台'),
  ('lease:billing-plan', @lease_menu_id, '计费计划', '/billing-plans', NULL, NULL, 37,
   'ENABLED', 0, 0, '合同与租约计费计划工作台')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), name = VALUES(name), path = VALUES(path),
  sort_order = VALUES(sort_order), status = 'ENABLED', deleted = 0,
  updated_by = 0, remark = VALUES(remark);

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
  ('fee-rule:view', '查看收费规则', 'fee-rule', 'view', 'ENABLED', 0, 0, '查看收费规则与不可变版本'),
  ('fee-rule:create', '新增收费规则', 'fee-rule', 'create', 'ENABLED', 0, 0, '新增收费规则草稿'),
  ('fee-rule:update', '修订收费规则', 'fee-rule', 'update', 'ENABLED', 0, 0, '通过新增版本修订收费规则'),
  ('fee-rule:activate', '生效收费规则', 'fee-rule', 'activate', 'ENABLED', 0, 0, '生效最新收费规则版本'),
  ('billing-plan:view', '查看计费计划', 'billing-plan', 'view', 'ENABLED', 0, 0, '查看计费计划及条目'),
  ('billing-plan:create', '新增计费计划', 'billing-plan', 'create', 'ENABLED', 0, 0, '新增计费计划草稿'),
  ('billing-plan:update', '维护计费计划', 'billing-plan', 'update', 'ENABLED', 0, 0, '维护或激活计费计划草稿'),
  ('billing-plan:terminate', '终止计费计划', 'billing-plan', 'terminate', 'ENABLED', 0, 0, '终止生效中的计费计划')
ON DUPLICATE KEY UPDATE
  name = VALUES(name), module = VALUES(module), action = VALUES(action),
  status = VALUES(status), deleted = 0, updated_by = 0, remark = VALUES(remark);

-- Preserve legacy FeeRule role access while keeping the new workspace codes explicit.
INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT legacy.role_id, target.id, 'ENABLED', 0, 0
FROM (
  SELECT rp.role_id, 'fee-rule:view' AS target_code
  FROM sys_role_permission rp JOIN sys_permission old_permission ON old_permission.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND old_permission.deleted = 0 AND old_permission.code = 'feerule:view'
  UNION ALL
  SELECT rp.role_id, 'fee-rule:create'
  FROM sys_role_permission rp JOIN sys_permission old_permission ON old_permission.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND old_permission.deleted = 0 AND old_permission.code = 'feerule:add'
  UNION ALL
  SELECT rp.role_id, 'fee-rule:update'
  FROM sys_role_permission rp JOIN sys_permission old_permission ON old_permission.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND old_permission.deleted = 0 AND old_permission.code = 'feerule:add'
  UNION ALL
  SELECT rp.role_id, 'fee-rule:activate'
  FROM sys_role_permission rp JOIN sys_permission old_permission ON old_permission.id = rp.permission_id
  WHERE rp.deleted = 0 AND rp.status = 'ENABLED' AND old_permission.deleted = 0 AND old_permission.code = 'feerule:add'
) legacy
JOIN sys_permission target ON target.code = legacy.target_code AND target.deleted = 0
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT role_record.id, permission_record.id, 'ENABLED', 0, 0
FROM sys_role role_record
JOIN sys_permission permission_record
  ON permission_record.deleted = 0
 AND permission_record.status = 'ENABLED'
 AND permission_record.module IN ('fee-rule', 'billing-plan')
WHERE role_record.deleted = 0
  AND role_record.status = 'ENABLED'
  AND role_record.code IN ('ADMIN', 'SUPER_ADMIN')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

-- A role that can view a V2 workspace must also receive its menu; this keeps
-- menu, router and API access aligned for non-administrator roles.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, menu_record.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
JOIN sys_permission permission_record
  ON permission_record.id = role_permission.permission_id
 AND permission_record.deleted = 0
JOIN sys_menu menu_record
  ON menu_record.deleted = 0
 AND menu_record.code = CASE permission_record.code
       WHEN 'fee-rule:view' THEN 'lease:fee-rule-v2'
       WHEN 'billing-plan:view' THEN 'lease:billing-plan'
     END
WHERE role_permission.deleted = 0
  AND role_permission.status = 'ENABLED'
  AND permission_record.code IN ('fee-rule:view', 'billing-plan:view')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, parent_menu.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
JOIN sys_permission permission_record
  ON permission_record.id = role_permission.permission_id
 AND permission_record.deleted = 0
JOIN sys_menu parent_menu ON parent_menu.code = 'lease' AND parent_menu.deleted = 0
WHERE role_permission.deleted = 0
  AND role_permission.status = 'ENABLED'
  AND permission_record.code IN ('fee-rule:view', 'billing-plan:view')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;
