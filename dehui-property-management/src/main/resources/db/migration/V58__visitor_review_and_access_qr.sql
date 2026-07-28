-- 访客预约审核与门禁二维码闭环。
-- 复用 appointment_status：PENDING=待审核、REGISTERED=审核通过、REJECTED=已驳回。

ALTER TABLE visitor_appointment
    ADD COLUMN reviewed_by BIGINT NULL
        COMMENT '审核人系统用户ID' AFTER appointment_status,
    ADD COLUMN reviewed_at DATETIME NULL
        COMMENT '审核时间' AFTER reviewed_by,
    ADD COLUMN review_remark VARCHAR(500) NULL
        COMMENT '审核意见或驳回原因' AFTER reviewed_at,
    ADD COLUMN qr_code_file_id BIGINT NULL
        COMMENT '当前门禁二维码文件ID' AFTER review_remark,
    ADD COLUMN qr_uploaded_by BIGINT NULL
        COMMENT '二维码上传人系统用户ID' AFTER qr_code_file_id,
    ADD COLUMN qr_uploaded_at DATETIME NULL
        COMMENT '二维码上传时间' AFTER qr_uploaded_by,
    ADD KEY idx_visitor_review_queue (appointment_status, visit_time, deleted),
    ADD KEY idx_visitor_qr_file (qr_code_file_id);
