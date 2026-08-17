ALTER TABLE tenant
    ADD COLUMN logo_file_id BIGINT NULL
        COMMENT '租户企业 Logo 的受保护文件 ID' AFTER service_manager_user_id,
    ADD KEY idx_tenant_logo_file (logo_file_id);
