-- 小程序租户报修表单快照字段。
-- reporter_name / reported_company_name 仅保存本次报修展示快照；
-- tenant_id、submitter_mobile_user_id 和 reporter_phone 继续由服务端身份派生。

ALTER TABLE work_order
    ADD COLUMN reported_company_name VARCHAR(128) NULL
        COMMENT '本次报修填写的公司名称快照' AFTER reporter_phone,
    ADD COLUMN reported_at DATETIME NULL
        COMMENT '报修人填写或确认的问题发生时间' AFTER reported_company_name;

UPDATE work_order work_record
LEFT JOIN tenant tenant_record
  ON tenant_record.id = work_record.tenant_id
 AND tenant_record.deleted = 0
SET work_record.reported_company_name = COALESCE(
        NULLIF(work_record.reported_company_name, ''),
        tenant_record.name
    ),
    work_record.reported_at = COALESCE(work_record.reported_at, work_record.created_at)
WHERE work_record.reported_company_name IS NULL
   OR work_record.reported_company_name = ''
   OR work_record.reported_at IS NULL;

ALTER TABLE work_order
    MODIFY COLUMN reported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '报修人填写或确认的问题发生时间';

-- 固定租户移动端报修表单的信息架构，同时保留其他 surface 的已有配置。
INSERT INTO module_field_setting (
    module_key, field_key, surfaces_json, version, status,
    created_by, updated_by, deleted
)
VALUES
    ('workorders', 'title', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', FALSE, 'editable', FALSE, 'order', 20)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'orderType', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', FALSE, 'editable', FALSE, 'order', 21)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'priority', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', FALSE, 'editable', FALSE, 'order', 22)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'location', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', FALSE, 'editable', FALSE, 'order', 23)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'attachments', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', FALSE, 'editable', FALSE, 'order', 24)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'contactName', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', TRUE, 'editable', TRUE, 'order', 1)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'companyName', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', TRUE, 'editable', TRUE, 'order', 2)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'reportedAt', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', TRUE, 'editable', FALSE, 'order', 3)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'problemCategory', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', TRUE, 'editable', TRUE, 'order', 4)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'description', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', TRUE, 'editable', TRUE, 'order', 5)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'remark', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', TRUE, 'editable', TRUE, 'order', 6)
    ), 0, 'ENABLED', 0, 0, 0),
    ('workorders', 'images', JSON_OBJECT(
        'miniProgramForm', JSON_OBJECT('visible', TRUE, 'editable', TRUE, 'order', 7)
    ), 0, 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    surfaces_json = JSON_MERGE_PATCH(
        COALESCE(module_field_setting.surfaces_json, JSON_OBJECT()),
        VALUES(surfaces_json)
    ),
    version = module_field_setting.version + 1,
    updated_by = VALUES(updated_by),
    updated_at = CURRENT_TIMESTAMP,
    deleted = 0,
    status = 'ENABLED';
