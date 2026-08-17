-- A legacy phone timestamp is not proof that WeChat verified the phone.
-- Existing rows intentionally remain UNVERIFIED and must pass the official
-- WeChat phone flow before they may restore a STAFF identity.
ALTER TABLE mobile_user
    ADD COLUMN phone_verification_source VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED'
        COMMENT '手机号验证来源：UNVERIFIED/WECHAT_API'
        AFTER phone_verified_at;

-- One live business identity may belong to only one mobile user. Generated
-- nullable guard columns keep disabled/history rows while preventing two
-- concurrently enabled bindings, including concurrent requests.
ALTER TABLE mobile_user_bind
    ADD COLUMN active_tenant_contact_guard BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN deleted = 0
                 AND status = 'ENABLED'
                 AND bind_type = 'TENANT'
                THEN tenant_contact_id
                ELSE NULL
            END
        ) STORED,
    ADD COLUMN active_sys_user_guard BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN deleted = 0
                 AND status = 'ENABLED'
                 AND bind_type = 'INTERNAL'
                THEN sys_user_id
                ELSE NULL
            END
        ) STORED,
    ADD UNIQUE KEY uk_mobile_bind_active_tenant_contact (active_tenant_contact_guard),
    ADD UNIQUE KEY uk_mobile_bind_active_sys_user (active_sys_user_guard);
