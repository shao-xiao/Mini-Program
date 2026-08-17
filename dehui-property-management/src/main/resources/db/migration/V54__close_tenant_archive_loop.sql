-- DHIC 租户档案闭环：企业资料、物业对接人、紧急联系人和唯一性约束。
-- 仅新增字段、索引和兼容回填；不删除租户、联系人或历史业务数据。

ALTER TABLE tenant
    ADD COLUMN short_name VARCHAR(128) NULL COMMENT '企业简称' AFTER name,
    ADD COLUMN invoice_title VARCHAR(128) NULL COMMENT '当前开票抬头' AFTER credit_code,
    ADD COLUMN tax_number VARCHAR(64) NULL COMMENT '当前纳税人识别号' AFTER invoice_title,
    ADD COLUMN service_manager_user_id BIGINT NULL COMMENT '物业对接内部用户ID' AFTER tax_number,
    ADD KEY idx_tenant_service_manager (service_manager_user_id, deleted),
    ADD KEY idx_tenant_short_name (short_name);

UPDATE tenant
SET invoice_title = COALESCE(NULLIF(invoice_title, ''), name),
    tax_number = COALESCE(NULLIF(tax_number, ''), credit_code)
WHERE deleted = 0;

ALTER TABLE tenant_contact
    ADD COLUMN is_emergency TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否当前紧急联系人' AFTER is_primary;

-- 兼容旧 OWNER/MANAGER/PRIMARY 等负责人值，并在每个租户中保留最近维护的一位当前负责人。
UPDATE tenant_contact contact_record
LEFT JOIN (
    SELECT ranked_contact.id,
           ranked_contact.row_no
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY tenant_id
                   ORDER BY is_primary DESC, updated_at DESC, id DESC
               ) AS row_no
        FROM tenant_contact
        WHERE deleted = 0
          AND status IN ('ACTIVE', 'ENABLED')
          AND (
              is_primary = 1
              OR UPPER(COALESCE(role, '')) IN ('OWNER', 'MANAGER', 'ADMIN', 'PRIMARY', 'RESPONSIBLE')
          )
    ) ranked_contact
) responsible_rank
  ON responsible_rank.id = contact_record.id
SET contact_record.role = CASE
        WHEN responsible_rank.row_no = 1 THEN 'RESPONSIBLE'
        ELSE 'EMPLOYEE'
    END,
    contact_record.is_primary = CASE
        WHEN responsible_rank.row_no = 1 THEN 1
        ELSE 0
    END
WHERE contact_record.deleted = 0
  AND (
      contact_record.is_primary = 1
      OR UPPER(COALESCE(contact_record.role, '')) IN ('OWNER', 'MANAGER', 'ADMIN', 'PRIMARY', 'RESPONSIBLE')
  );

UPDATE tenant_contact
SET role = 'EMPLOYEE',
    is_primary = 0
WHERE deleted = 0
  AND UPPER(COALESCE(role, '')) NOT IN ('RESPONSIBLE', 'EMPLOYEE');

ALTER TABLE tenant_contact
    ADD COLUMN active_responsible_tenant_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN deleted = 0
             AND status IN ('ACTIVE', 'ENABLED')
             AND role = 'RESPONSIBLE'
             AND is_primary = 1
            THEN tenant_id
            ELSE NULL
        END
    ) STORED COMMENT '当前负责人唯一键',
    ADD COLUMN active_emergency_tenant_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN deleted = 0
             AND status IN ('ACTIVE', 'ENABLED')
             AND is_emergency = 1
            THEN tenant_id
            ELSE NULL
        END
    ) STORED COMMENT '当前紧急联系人唯一键',
    ADD UNIQUE KEY uk_tenant_contact_active_responsible (active_responsible_tenant_id),
    ADD UNIQUE KEY uk_tenant_contact_active_emergency (active_emergency_tenant_id),
    ADD KEY idx_tenant_contact_archive (tenant_id, status, deleted, role, is_emergency);
