-- DHIC 小程序第二阶段：租户业务闭环。
-- 仅增加 canonical 字段和移动查询索引；不删除或覆盖历史业务记录。

ALTER TABLE work_order
    ADD COLUMN order_type VARCHAR(32) NOT NULL DEFAULT 'REPAIR'
        COMMENT '工单类型 canonical 字段' AFTER title,
    ADD COLUMN location VARCHAR(255) NULL
        COMMENT '报修位置 canonical 字段' AFTER reporter_phone,
    ADD COLUMN description TEXT NULL
        COMMENT '问题描述 canonical 字段' AFTER location;

UPDATE work_order
SET location = NULLIF(TRIM(SUBSTRING_INDEX(COALESCE(remark, ''), '\n', 1)), ''),
    description = NULLIF(
        TRIM(
            CASE
                WHEN LOCATE('\n', COALESCE(remark, '')) > 0
                    THEN SUBSTRING(COALESCE(remark, ''), LOCATE('\n', COALESCE(remark, '')) + 1)
                ELSE COALESCE(remark, '')
            END
        ),
        ''
    )
WHERE location IS NULL
  AND description IS NULL
  AND remark IS NOT NULL
  AND remark <> '';

ALTER TABLE visitor_appointment
    ADD COLUMN visit_company VARCHAR(128) NULL
        COMMENT '来访单位 canonical 字段' AFTER visitor_phone,
    ADD COLUMN visit_reason VARCHAR(500) NULL
        COMMENT '来访事由 canonical 字段' AFTER visit_time;

UPDATE visitor_appointment
SET visit_reason = remark
WHERE visit_reason IS NULL
  AND remark IS NOT NULL
  AND remark <> '';

ALTER TABLE bill
    ADD KEY idx_bill_mobile_tenant_due (tenant_id, due_date, id);

ALTER TABLE contract
    ADD KEY idx_contract_mobile_tenant_updated (tenant_id, updated_at, id);

ALTER TABLE work_order
    ADD KEY idx_workorder_mobile_scope (tenant_id, submitter_mobile_user_id, updated_at, id);

ALTER TABLE visitor_appointment
    ADD KEY idx_visitor_mobile_scope (tenant_id, applicant_mobile_user_id, visit_time, id);
