-- DHIC 财务闭环：统一计费依据 -> 草稿账单 -> 调整/审核锁定 -> 受保护发票。
-- 本迁移只做加法、兼容回填和菜单文案收敛，不删除历史业务数据。

ALTER TABLE asset_charge_basis
    ADD COLUMN source_business_no VARCHAR(128) NULL COMMENT '来源业务编号快照' AFTER source_id,
    ADD COLUMN charge_item_type VARCHAR(32) NULL
        COMMENT 'RENT/PROPERTY_FEE/WATER/ELECTRICITY/GAS/PARKING/MEETING/OTHER' AFTER source_business_no,
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种快照' AFTER amount,
    ADD COLUMN calculation_formula VARCHAR(1000) NULL COMMENT '计算公式快照' AFTER currency,
    ADD COLUMN source_created_at DATETIME NULL COMMENT '来源业务发生时间快照' AFTER snapshot_json;

UPDATE asset_charge_basis basis
LEFT JOIN energy_reading reading_record
       ON basis.source_type = 'ENERGY' AND reading_record.id = basis.source_id
LEFT JOIN energy_meter meter_record
       ON meter_record.id = basis.meter_id
LEFT JOIN parking_assignment parking_record
       ON basis.source_type = 'PARKING' AND parking_record.id = basis.source_id
LEFT JOIN meeting_booking meeting_record
       ON basis.source_type = 'MEETING' AND meeting_record.id = basis.source_id
LEFT JOIN billing_plan_item plan_item
       ON basis.source_type IN ('PROPERTY', 'RENT') AND plan_item.id = basis.source_id
LEFT JOIN fee_rule_version rule_version
       ON rule_version.id = plan_item.fee_rule_version_id
LEFT JOIN fee_rule rule_record
       ON rule_record.id = rule_version.fee_rule_id
SET basis.source_business_no = COALESCE(
        basis.source_business_no,
        reading_record.code,
        parking_record.code,
        meeting_record.code,
        CONCAT('PLAN-ITEM-', plan_item.id),
        basis.code
    ),
    basis.charge_item_type = COALESCE(
        basis.charge_item_type,
        CASE
            WHEN basis.source_type = 'PROPERTY' THEN 'PROPERTY_FEE'
            WHEN basis.source_type = 'RENT' THEN 'RENT'
            WHEN basis.source_type = 'ENERGY' AND meter_record.meter_type = 'WATER' THEN 'WATER'
            WHEN basis.source_type = 'ENERGY' AND meter_record.meter_type IN ('ELECTRIC', 'ELECTRICITY') THEN 'ELECTRICITY'
            WHEN basis.source_type = 'ENERGY' AND meter_record.meter_type = 'GAS' THEN 'GAS'
            WHEN basis.source_type = 'PARKING' THEN 'PARKING'
            WHEN basis.source_type = 'MEETING' THEN 'MEETING'
            ELSE COALESCE(rule_record.fee_type, 'OTHER')
        END
    ),
    basis.calculation_formula = COALESCE(
        basis.calculation_formula,
        CONCAT(
            CAST(basis.quantity AS CHAR), ' ',
            basis.unit, ' × ',
            CAST(basis.unit_price_snapshot AS CHAR), ' = ',
            CAST(basis.amount AS CHAR)
        )
    ),
    basis.source_created_at = COALESCE(
        basis.source_created_at,
        reading_record.created_at,
        parking_record.created_at,
        meeting_record.created_at,
        plan_item.created_at,
        basis.created_at
    )
WHERE basis.deleted = 0;

ALTER TABLE bill
    ADD COLUMN billing_period VARCHAR(16) NULL COMMENT '统一计费周期' AFTER period_end,
    ADD COLUMN bill_date DATE NULL COMMENT '制单日期' AFTER billing_period,
    ADD COLUMN calculated_total DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '系统计算金额合计' AFTER amount,
    ADD COLUMN adjustment_total DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '调整金额合计' AFTER calculated_total,
    ADD COLUMN final_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '最终应收金额' AFTER adjustment_total,
    ADD COLUMN workflow_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/PENDING_REVIEW/APPROVED/CANCELLED/VOID' AFTER audit_status,
    ADD COLUMN invoice_status VARCHAR(32) NOT NULL DEFAULT 'NOT_INVOICED'
        COMMENT 'NOT_INVOICED/PARTIALLY_INVOICED/INVOICED' AFTER pay_status,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER invoice_status,
    ADD COLUMN reviewed_by BIGINT NULL COMMENT '审核人' AFTER version,
    ADD COLUMN reviewed_at DATETIME NULL COMMENT '审核时间' AFTER reviewed_by,
    ADD COLUMN review_opinion VARCHAR(500) NULL COMMENT '审核意见' AFTER reviewed_at,
    ADD COLUMN voided_by BIGINT NULL COMMENT '作废人' AFTER review_opinion,
    ADD COLUMN voided_at DATETIME NULL COMMENT '作废时间' AFTER voided_by,
    ADD COLUMN void_reason VARCHAR(500) NULL COMMENT '作废原因' AFTER voided_at,
    ADD COLUMN adjustment_of_bill_id BIGINT NULL COMMENT '关联原账单ID，冲减/调整单使用' AFTER void_reason,
    ADD KEY idx_bill_workflow (workflow_status, bill_date, deleted),
    ADD KEY idx_bill_billing_period (billing_period, tenant_id, deleted),
    ADD KEY idx_bill_invoice_status (invoice_status, workflow_status, deleted),
    ADD KEY idx_bill_adjustment_of (adjustment_of_bill_id);

UPDATE bill
SET billing_period = COALESCE(
        billing_period,
        DATE_FORMAT(period_start, '%Y-%m'),
        DATE_FORMAT(created_at, '%Y-%m')
    ),
    bill_date = COALESCE(bill_date, DATE(created_at)),
    calculated_total = CASE WHEN calculated_total = 0 THEN amount ELSE calculated_total END,
    final_amount = CASE WHEN final_amount = 0 THEN amount ELSE final_amount END,
    workflow_status = CASE
        WHEN status IN ('DELETED', 'CANCELLED') OR pay_status = 'CANCELLED' THEN 'CANCELLED'
        WHEN audit_status = 'APPROVED' THEN 'APPROVED'
        WHEN audit_status = 'REJECTED' THEN 'DRAFT'
        ELSE 'PENDING_REVIEW'
    END,
    invoice_status = CASE
        WHEN EXISTS (
            SELECT 1
            FROM bill_invoice invoice_record
            WHERE invoice_record.bill_id = bill.id
              AND invoice_record.deleted = 0
              AND invoice_record.invoice_status IN ('INVOICED', 'ISSUED', 'ACTIVE')
        ) THEN 'INVOICED'
        ELSE 'NOT_INVOICED'
    END
WHERE deleted = 0;

CREATE TABLE bill_line_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    bill_id BIGINT NOT NULL COMMENT '账单ID',
    charge_basis_id BIGINT NULL COMMENT '统一计费依据ID；人工费用可为空',
    charge_item_type VARCHAR(32) NOT NULL COMMENT '费用类型',
    source_type VARCHAR(32) NOT NULL COMMENT '来源模块',
    source_id BIGINT NULL COMMENT '来源业务ID',
    source_snapshot JSON NULL COMMENT '不可变来源快照',
    description VARCHAR(500) NOT NULL COMMENT '明细说明',
    quantity_snapshot DECIMAL(18,4) NOT NULL DEFAULT 1 COMMENT '数量/面积/用量/时长快照',
    unit VARCHAR(32) NOT NULL DEFAULT '项' COMMENT '计量单位',
    unit_price_snapshot DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '单价快照',
    calculated_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '系统计算金额',
    adjustment_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '调整金额',
    final_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '最终金额',
    adjustment_reason VARCHAR(500) NULL COMMENT '调整原因',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REMOVED/VOID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    active_charge_basis_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN deleted = 0 AND status = 'ACTIVE' THEN charge_basis_id
            ELSE NULL
        END
    ) STORED COMMENT '有效计费依据唯一消费键',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bill_line_active_basis (active_charge_basis_id),
    KEY idx_bill_line_bill (bill_id, status, sort_order, id),
    KEY idx_bill_line_source (source_type, source_id),
    CONSTRAINT chk_bill_line_amount
        CHECK (final_amount = ROUND(calculated_amount + adjustment_amount, 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='财务账单明细与三金额快照';

INSERT INTO bill_line_item (
    bill_id, charge_basis_id, charge_item_type, source_type, source_id,
    source_snapshot, description, quantity_snapshot, unit, unit_price_snapshot,
    calculated_amount, adjustment_amount, final_amount, adjustment_reason,
    sort_order, status, created_by, updated_by
)
SELECT bill_record.id,
       basis_record.id,
       COALESCE(basis_record.charge_item_type, bill_record.bill_type, 'OTHER'),
       COALESCE(basis_record.source_type, bill_record.source_type, 'LEGACY'),
       COALESCE(basis_record.source_id, bill_record.source_id),
       basis_record.snapshot_json,
       COALESCE(bill_record.title, CONCAT('历史账单 ', bill_record.code)),
       COALESCE(basis_record.quantity, 1),
       COALESCE(basis_record.unit, '项'),
       COALESCE(basis_record.unit_price_snapshot, bill_record.amount),
       bill_record.amount,
       0,
       bill_record.amount,
       NULL,
       0,
       'ACTIVE',
       COALESCE(bill_record.created_by, 0),
       COALESCE(bill_record.updated_by, 0)
FROM bill bill_record
LEFT JOIN asset_charge_basis basis_record
       ON basis_record.bill_id = bill_record.id
      AND basis_record.deleted = 0
WHERE bill_record.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM bill_line_item existing_line
      WHERE existing_line.bill_id = bill_record.id
        AND existing_line.deleted = 0
  );

ALTER TABLE bill_invoice
    ADD COLUMN invoice_type VARCHAR(32) NOT NULL DEFAULT 'ELECTRONIC' COMMENT '发票类型' AFTER invoice_title,
    ADD COLUMN invoice_date DATE NULL COMMENT '发票日期' AFTER tax_no,
    ADD COLUMN tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '税额' AFTER amount,
    ADD COLUMN file_id BIGINT NULL COMMENT '受保护文件ID' AFTER tax_amount,
    ADD COLUMN version_no INT NOT NULL DEFAULT 1 COMMENT '发票版本号' AFTER file_id,
    ADD COLUMN previous_invoice_id BIGINT NULL COMMENT '上一版本发票ID' AFTER version_no,
    ADD COLUMN uploaded_by BIGINT NULL COMMENT '上传人' AFTER previous_invoice_id,
    ADD COLUMN uploaded_at DATETIME NULL COMMENT '上传时间' AFTER uploaded_by,
    ADD COLUMN voided_by BIGINT NULL COMMENT '作废人' AFTER uploaded_at,
    ADD COLUMN voided_at DATETIME NULL COMMENT '作废时间' AFTER voided_by,
    ADD COLUMN void_reason VARCHAR(500) NULL COMMENT '作废原因' AFTER voided_at,
    ADD KEY idx_invoice_bill_version (bill_id, version_no),
    ADD KEY idx_invoice_file (file_id),
    ADD KEY idx_invoice_status_version (invoice_status, bill_id, version_no);

UPDATE bill_invoice invoice_record
LEFT JOIN bill_invoice_attachment attachment_record
       ON attachment_record.invoice_id = invoice_record.id
      AND attachment_record.deleted = 0
SET invoice_record.invoice_date = COALESCE(invoice_record.invoice_date, DATE(invoice_record.issued_at), DATE(invoice_record.created_at)),
    invoice_record.file_id = COALESCE(invoice_record.file_id, attachment_record.file_id),
    invoice_record.uploaded_by = COALESCE(invoice_record.uploaded_by, invoice_record.created_by),
    invoice_record.uploaded_at = COALESCE(invoice_record.uploaded_at, invoice_record.created_at)
WHERE invoice_record.deleted = 0;

CREATE TABLE bill_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    bill_id BIGINT NOT NULL COMMENT '账单ID',
    action VARCHAR(64) NOT NULL COMMENT '操作类型',
    before_snapshot JSON NULL COMMENT '修改前摘要',
    after_snapshot JSON NULL COMMENT '修改后摘要',
    reason VARCHAR(500) NULL COMMENT '操作或调整原因',
    operator_id BIGINT NOT NULL COMMENT '操作人',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_bill_audit_bill (bill_id, occurred_at, id),
    KEY idx_bill_audit_operator (operator_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='财务账单操作审计';

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
    ('bill:update', '编辑财务草稿账单', 'bill', 'update', 'ENABLED', 0, 0, '编辑草稿、调整明细和备注'),
    ('bill:submit', '提交财务账单审核', 'bill', 'submit', 'ENABLED', 0, 0, '草稿提交审核'),
    ('bill:void', '作废财务账单', 'bill', 'void', 'ENABLED', 0, 0, '按状态作废并保留审计'),
    ('bill:adjustment', '创建冲减或调整单', 'bill', 'adjustment', 'ENABLED', 0, 0, '审核后通过关联单调整'),
    ('finance:invoice:void', '作废发票', 'finance', 'invoice:void', 'ENABLED', 0, 0, '保留发票历史版本'),
    ('finance:dashboard:view', '查看财务看板', 'finance', 'dashboard:view', 'ENABLED', 0, 0, '查看后端聚合财务指标')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    module = VALUES(module),
    action = VALUES(action),
    status = 'ENABLED',
    deleted = 0,
    updated_by = 0,
    remark = VALUES(remark);

UPDATE sys_menu
SET name = '财务账单',
    path = '/bills',
    status = 'ENABLED',
    deleted = 0,
    updated_by = 0,
    remark = 'V53 财务闭环统一账单工作台'
WHERE code = 'finance:bill';

UPDATE sys_menu
SET name = '财务看板',
    path = '/finance/dashboard',
    status = 'ENABLED',
    deleted = 0,
    updated_by = 0,
    remark = 'V53 后端聚合财务看板'
WHERE code = 'finance:dashboard';

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id,
       target_permission.id,
       'ENABLED',
       0,
       0
FROM sys_role_permission role_permission
JOIN sys_permission source_permission
  ON source_permission.id = role_permission.permission_id
 AND source_permission.deleted = 0
JOIN sys_permission target_permission
  ON target_permission.deleted = 0
 AND (
      (source_permission.code IN ('bill:add', 'bill:audit', 'bill:pay', 'finance:bill:create', 'finance:bill:audit')
       AND target_permission.code IN ('bill:update', 'bill:submit', 'bill:void', 'bill:adjustment', 'finance:invoice:void'))
      OR
      (source_permission.code IN ('bill:view', 'finance:bill:view')
       AND target_permission.code = 'finance:dashboard:view')
 )
WHERE role_permission.deleted = 0
  AND role_permission.status = 'ENABLED'
ON DUPLICATE KEY UPDATE
    status = 'ENABLED',
    deleted = 0,
    updated_by = 0;
