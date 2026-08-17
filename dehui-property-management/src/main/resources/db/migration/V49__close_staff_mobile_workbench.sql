-- DHIC 小程序第三阶段：物业 STAFF 移动工作台闭环。
-- 本迁移仅补充移动操作审计和内部附件访问元数据，不删除或覆盖历史业务记录。

ALTER TABLE inspection_record
    ADD COLUMN inspector_user_id BIGINT NULL
        COMMENT '巡检物业员工ID，移动端由当前STAFF身份派生' AFTER inspector,
    ADD KEY idx_inspection_staff_scope (inspector_user_id, inspection_date, inspection_status, deleted);

UPDATE inspection_record inspection
INNER JOIN sys_user staff
        ON staff.id = inspection.created_by
       AND staff.deleted = 0
SET inspection.inspector_user_id = staff.id
WHERE inspection.inspector_user_id IS NULL
  AND inspection.created_by IS NOT NULL
  AND inspection.created_by > 0;

CREATE TABLE inspection_record_history (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    inspection_id BIGINT NOT NULL COMMENT '巡检记录ID',
    action VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/CLOSE',
    content VARCHAR(1000) NULL COMMENT '追加处理反馈',
    operator_id BIGINT NOT NULL COMMENT '操作物业员工ID',
    operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT NOT NULL COMMENT '创建人',
    updated_by BIGINT NOT NULL COMMENT '更新人',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_inspection_history_record (inspection_id, operated_at, id),
    KEY idx_inspection_history_operator (operator_id, operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='巡检追加处理与状态历史';

INSERT INTO inspection_record_history (
    inspection_id, action, content, operator_id, operated_at,
    status, created_at, updated_at, created_by, updated_by, deleted
)
SELECT inspection.id,
       'CREATE',
       inspection.remark,
       COALESCE(inspection.inspector_user_id, inspection.created_by, 0),
       inspection.created_at,
       'ACTIVE',
       inspection.created_at,
       inspection.updated_at,
       COALESCE(inspection.inspector_user_id, inspection.created_by, 0),
       COALESCE(inspection.inspector_user_id, inspection.updated_by, 0),
       0
FROM inspection_record inspection
WHERE inspection.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM inspection_record_history history
      WHERE history.inspection_id = inspection.id
        AND history.deleted = 0
  );

ALTER TABLE work_order_attachment
    ADD COLUMN work_order_record_id BIGINT NULL
        COMMENT '处理记录ID；为空表示工单级附件' AFTER work_order_id,
    ADD KEY idx_workorder_attachment_record (work_order_record_id, deleted);

ALTER TABLE file_upload
    ADD COLUMN access_scope VARCHAR(32) NOT NULL DEFAULT 'PUBLIC'
        COMMENT 'PUBLIC/INTERNAL；第三阶段内部业务图片使用INTERNAL' AFTER storage_path,
    ADD COLUMN owner_sys_user_id BIGINT NULL
        COMMENT '内部移动文件上传物业员工ID' AFTER access_scope,
    ADD KEY idx_file_internal_owner (access_scope, owner_sys_user_id, created_at, deleted);
