ALTER TABLE meeting_booking
    ADD COLUMN review_reply VARCHAR(500) NULL COMMENT '物业审核回复' AFTER department_name,
    ADD COLUMN reviewed_by BIGINT NULL COMMENT '审核人系统用户ID' AFTER review_reply,
    ADD COLUMN reviewed_at DATETIME NULL COMMENT '审核时间' AFTER reviewed_by;
