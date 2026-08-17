ALTER TABLE sys_user
    ADD COLUMN mobile_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许使用物业移动工作台' AFTER status,
    ADD KEY idx_sys_user_mobile_enabled (mobile_enabled, status, deleted);

UPDATE sys_user su
SET su.mobile_enabled = 1
WHERE su.deleted = 0
  AND EXISTS (
    SELECT 1
    FROM mobile_user_bind mub
    WHERE mub.sys_user_id = su.id
      AND mub.bind_type = 'INTERNAL'
      AND mub.status = 'ENABLED'
      AND mub.deleted = 0
  );

ALTER TABLE mobile_user
    ADD COLUMN current_bind_id BIGINT DEFAULT NULL COMMENT '当前选择的业务身份绑定ID' AFTER user_type,
    ADD COLUMN last_login_at DATETIME DEFAULT NULL COMMENT '最近微信登录时间' AFTER status,
    ADD KEY idx_mobile_user_current_bind (current_bind_id);

ALTER TABLE mobile_user_bind
    DROP INDEX uk_mub_active_bind,
    ADD KEY idx_mub_identity (mobile_user_id, bind_type, tenant_contact_id, sys_user_id, status, deleted);

UPDATE mobile_user mu
SET mu.current_bind_id = (
    SELECT mub.id
    FROM mobile_user_bind mub
    WHERE mub.mobile_user_id = mu.id
      AND mub.status = 'ENABLED'
      AND mub.deleted = 0
      AND (
        (mu.user_type = 'TENANT' AND mub.bind_type = 'TENANT')
        OR (mu.user_type IN ('INTERNAL', 'STAFF') AND mub.bind_type = 'INTERNAL')
      )
    ORDER BY mub.id DESC
    LIMIT 1
)
WHERE mu.current_bind_id IS NULL
  AND mu.deleted = 0;

CREATE TABLE mobile_identity_candidate (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  mobile_user_id BIGINT NOT NULL COMMENT '小程序用户ID',
  mobile_user_bind_id BIGINT NOT NULL COMMENT '候选业务身份绑定ID',
  token_hash CHAR(64) NOT NULL COMMENT '一次性候选令牌SHA-256',
  candidate_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/USED/EXPIRED',
  expires_at DATETIME NOT NULL COMMENT '令牌过期时间',
  used_at DATETIME DEFAULT NULL COMMENT '使用时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT DEFAULT NULL COMMENT '创建人',
  updated_by BIGINT DEFAULT NULL COMMENT '更新人',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_mobile_candidate_token (token_hash, deleted),
  KEY idx_mobile_candidate_user (mobile_user_id, candidate_status, expires_at),
  KEY idx_mobile_candidate_bind (mobile_user_bind_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小程序一次性身份候选令牌';

ALTER TABLE visitor_appointment
    ADD COLUMN applicant_mobile_user_id BIGINT DEFAULT NULL COMMENT '申请人小程序用户ID' AFTER tenant_id,
    ADD KEY idx_visitor_applicant (applicant_mobile_user_id, visit_time, deleted);
