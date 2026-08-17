ALTER TABLE tenant_contact
  ADD COLUMN password_hash VARCHAR(255) DEFAULT NULL COMMENT '租户联系人小程序登录密码哈希' AFTER email;

UPDATE tenant_contact
SET password_hash = '$2a$10$XqhZR.pObyI//tr.gLRq7.Joa1SjTQRO0USIJcDEo4UtUarcndRBW'
WHERE deleted = 0
  AND (password_hash IS NULL OR password_hash = '');
