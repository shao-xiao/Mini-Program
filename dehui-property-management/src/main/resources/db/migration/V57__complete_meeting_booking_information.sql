-- 补齐会议预约部门快照。仅新增字段，不回写或删除历史预约。
ALTER TABLE meeting_booking
    ADD COLUMN department_name VARCHAR(100) NULL COMMENT '申请部门快照' AFTER contact_phone;
