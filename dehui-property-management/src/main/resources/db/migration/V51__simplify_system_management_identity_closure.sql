ALTER TABLE sys_user
    ADD COLUMN web_login_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许登录管理后台' AFTER mobile_enabled,
    ADD COLUMN primary_role_id BIGINT DEFAULT NULL COMMENT '主要角色ID' AFTER web_login_enabled,
    ADD COLUMN last_login_at DATETIME DEFAULT NULL COMMENT '最近后台登录时间' AFTER primary_role_id,
    ADD KEY idx_sys_user_web_login (web_login_enabled, status, deleted),
    ADD KEY idx_sys_user_primary_role (primary_role_id);

UPDATE sys_user user_record
SET user_record.primary_role_id = (
    SELECT MIN(user_role.role_id)
    FROM sys_user_role user_role
    INNER JOIN sys_role role_record
      ON role_record.id = user_role.role_id
     AND role_record.deleted = 0
     AND role_record.status = 'ENABLED'
    WHERE user_role.user_id = user_record.id
      AND user_role.deleted = 0
      AND user_role.status = 'ENABLED'
)
WHERE user_record.deleted = 0
  AND user_record.primary_role_id IS NULL;

ALTER TABLE tenant_contact
    ADD COLUMN position VARCHAR(64) DEFAULT NULL COMMENT '职位' AFTER email;

ALTER TABLE mobile_user
    ADD COLUMN phone_verified_at DATETIME DEFAULT NULL COMMENT '最近微信手机号验证时间' AFTER phone;

UPDATE mobile_user
SET phone_verified_at = COALESCE(last_login_at, updated_at)
WHERE phone IS NOT NULL
  AND phone <> ''
  AND phone_verified_at IS NULL
  AND deleted = 0;

CREATE TABLE mobile_identity_audit (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  mobile_user_id BIGINT NOT NULL COMMENT '小程序用户ID',
  action VARCHAR(64) NOT NULL COMMENT '操作',
  previous_bind_id BIGINT DEFAULT NULL COMMENT '原身份绑定ID',
  next_bind_id BIGINT DEFAULT NULL COMMENT '新身份绑定ID',
  operator_sys_user_id BIGINT DEFAULT NULL COMMENT '后台操作人',
  detail VARCHAR(500) DEFAULT NULL COMMENT '不含隐私的操作说明',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_mobile_identity_audit_user (mobile_user_id, created_at),
  KEY idx_mobile_identity_audit_operator (operator_sys_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小程序身份绑定审计';

UPDATE sys_menu
SET sort_order = CASE code
    WHEN 'system:user' THEN 101
    WHEN 'system:role' THEN 102
    WHEN 'system:mobile-user' THEN 103
    ELSE sort_order
END,
updated_by = 0
WHERE code IN ('system:user', 'system:role', 'system:mobile-user')
  AND deleted = 0;
