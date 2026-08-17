-- Isolated local-only data for Phase 1 mobile identity/RBAC HTTP verification.
-- Prefix: CODX-P1 / codx_p1. Never run against production.

SET @prefix = 'CODX-P1';

DELETE FROM mobile_identity_candidate
WHERE mobile_user_id IN (
  SELECT id FROM mobile_user WHERE code LIKE 'CODX-P1-MU-%'
);
DELETE FROM mobile_user_bind
WHERE mobile_user_id IN (
  SELECT id FROM mobile_user WHERE code LIKE 'CODX-P1-MU-%'
);
DELETE FROM mobile_user WHERE code LIKE 'CODX-P1-MU-%';
DELETE FROM tenant_contact WHERE code LIKE 'CODX-P1-TC-%';
DELETE FROM tenant WHERE code = 'CODX-P1-TENANT';
DELETE FROM sys_user_role
WHERE user_id IN (SELECT id FROM sys_user WHERE code LIKE 'CODX-P1-SU-%');
DELETE FROM sys_user WHERE code LIKE 'CODX-P1-SU-%';
DELETE FROM sys_role_permission
WHERE role_id IN (SELECT id FROM sys_role WHERE code = 'CODX_P1_MOBILE_ROLE');
DELETE FROM sys_role WHERE code = 'CODX_P1_MOBILE_ROLE';

INSERT INTO tenant
  (code, name, contact_person, contact_phone, status, created_by, updated_by, remark)
VALUES
  ('CODX-P1-TENANT', 'Codex Phase1 Isolation Tenant', 'Phase1 Owner',
   '13800138001', 'ENABLED', 0, 0, 'CODX-P1 isolated HTTP verification');
SET @tenant_id = LAST_INSERT_ID();

INSERT INTO tenant_contact
  (code, tenant_id, name, phone, role, is_primary, status, created_by, updated_by, remark)
VALUES
  ('CODX-P1-TC-RESP', @tenant_id, 'Phase1 Responsible', '13800138001',
   'RESPONSIBLE', 1, 'ENABLED', 0, 0, 'CODX-P1'),
  ('CODX-P1-TC-EMP1', @tenant_id, 'Phase1 Employee One', '13800138002',
   'EMPLOYEE', 0, 'ENABLED', 0, 0, 'CODX-P1'),
  ('CODX-P1-TC-EMP2', @tenant_id, 'Phase1 Employee Two', '13800138003',
   'EMPLOYEE', 0, 'ENABLED', 0, 0, 'CODX-P1'),
  ('CODX-P1-TC-MULTI', @tenant_id, 'Phase1 Multi Identity', '13800138004',
   'EMPLOYEE', 0, 'ENABLED', 0, 0, 'CODX-P1');

INSERT INTO sys_role
  (code, name, role_type, status, created_by, updated_by, remark)
VALUES
  ('CODX_P1_MOBILE_ROLE', 'Codex Phase1 Mobile Role', 'BUSINESS',
   'ENABLED', 0, 0, 'CODX-P1 isolated HTTP verification');
SET @role_id = LAST_INSERT_ID();

INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by)
SELECT @role_id, permission_record.id, 'ENABLED', 0, 0
FROM sys_permission permission_record
WHERE permission_record.deleted = 0
  AND permission_record.status = 'ENABLED'
  AND permission_record.code IN (
    'workorder:view',
    'workorder:update',
    'workorder:complete',
    'energy:view',
    'parking:view',
    'meeting-booking:view',
    'visitor:view',
    'visitor:update'
  );

INSERT INTO sys_user
  (code, username, password_hash, real_name, phone, status, mobile_enabled,
   created_by, updated_by, remark)
VALUES
  ('CODX-P1-SU-STAFF', 'codx_p1_staff', '$2a$10$disabledPhaseOneOnly',
   'Phase1 Property Staff', '13800138005', 'ENABLED', 1, 0, 0, 'CODX-P1'),
  ('CODX-P1-SU-MULTI', 'codx_p1_multi', '$2a$10$disabledPhaseOneOnly',
   'Phase1 Multi Staff', '13800138004', 'ENABLED', 1, 0, 0, 'CODX-P1');
SET @staff_user_id = (
  SELECT id FROM sys_user WHERE code = 'CODX-P1-SU-STAFF' AND deleted = 0
);
SET @multi_staff_user_id = (
  SELECT id FROM sys_user WHERE code = 'CODX-P1-SU-MULTI' AND deleted = 0
);

INSERT INTO sys_user_role
  (user_id, role_id, status, created_by, updated_by)
VALUES
  (@staff_user_id, @role_id, 'ENABLED', 0, 0),
  (@multi_staff_user_id, @role_id, 'ENABLED', 0, 0);

INSERT INTO mobile_user
  (code, nickname, phone, user_type, status, last_login_at, created_by, updated_by, remark)
VALUES
  ('CODX-P1-MU-PUBLIC', 'Phase1 Public', NULL, 'PUBLIC', 'ENABLED', NOW(), 0, 0, 'CODX-P1'),
  ('CODX-P1-MU-RESP', 'Phase1 Responsible', '13800138001', 'TENANT', 'ENABLED', NOW(), 0, 0, 'CODX-P1'),
  ('CODX-P1-MU-EMP1', 'Phase1 Employee One', '13800138002', 'TENANT', 'ENABLED', NOW(), 0, 0, 'CODX-P1'),
  ('CODX-P1-MU-EMP2', 'Phase1 Employee Two', '13800138003', 'TENANT', 'ENABLED', NOW(), 0, 0, 'CODX-P1'),
  ('CODX-P1-MU-STAFF', 'Phase1 Staff', '13800138005', 'STAFF', 'ENABLED', NOW(), 0, 0, 'CODX-P1'),
  ('CODX-P1-MU-UNMATCHED', 'Phase1 Unmatched', '13800138009', 'PUBLIC', 'ENABLED', NOW(), 0, 0, 'CODX-P1'),
  ('CODX-P1-MU-MULTI', 'Phase1 Multi', '13800138004', 'PUBLIC', 'ENABLED', NOW(), 0, 0, 'CODX-P1');

SET @mu_resp = (SELECT id FROM mobile_user WHERE code = 'CODX-P1-MU-RESP' AND deleted = 0);
SET @mu_emp1 = (SELECT id FROM mobile_user WHERE code = 'CODX-P1-MU-EMP1' AND deleted = 0);
SET @mu_emp2 = (SELECT id FROM mobile_user WHERE code = 'CODX-P1-MU-EMP2' AND deleted = 0);
SET @mu_staff = (SELECT id FROM mobile_user WHERE code = 'CODX-P1-MU-STAFF' AND deleted = 0);
SET @mu_multi = (SELECT id FROM mobile_user WHERE code = 'CODX-P1-MU-MULTI' AND deleted = 0);

INSERT INTO mobile_user_bind
  (mobile_user_id, bind_type, tenant_id, tenant_contact_id, sys_user_id,
   status, created_by, updated_by, remark)
SELECT @mu_resp, 'TENANT', @tenant_id, id, NULL, 'ENABLED', 0, 0, 'CODX-P1'
FROM tenant_contact WHERE code = 'CODX-P1-TC-RESP' AND deleted = 0;
SET @bind_resp = LAST_INSERT_ID();

INSERT INTO mobile_user_bind
  (mobile_user_id, bind_type, tenant_id, tenant_contact_id, sys_user_id,
   status, created_by, updated_by, remark)
SELECT @mu_emp1, 'TENANT', @tenant_id, id, NULL, 'ENABLED', 0, 0, 'CODX-P1'
FROM tenant_contact WHERE code = 'CODX-P1-TC-EMP1' AND deleted = 0;
SET @bind_emp1 = LAST_INSERT_ID();

INSERT INTO mobile_user_bind
  (mobile_user_id, bind_type, tenant_id, tenant_contact_id, sys_user_id,
   status, created_by, updated_by, remark)
SELECT @mu_emp2, 'TENANT', @tenant_id, id, NULL, 'ENABLED', 0, 0, 'CODX-P1'
FROM tenant_contact WHERE code = 'CODX-P1-TC-EMP2' AND deleted = 0;
SET @bind_emp2 = LAST_INSERT_ID();

INSERT INTO mobile_user_bind
  (mobile_user_id, bind_type, tenant_id, tenant_contact_id, sys_user_id,
   status, created_by, updated_by, remark)
VALUES
  (@mu_staff, 'INTERNAL', NULL, NULL, @staff_user_id, 'ENABLED', 0, 0, 'CODX-P1');
SET @bind_staff = LAST_INSERT_ID();

INSERT INTO mobile_user_bind
  (mobile_user_id, bind_type, tenant_id, tenant_contact_id, sys_user_id,
   status, created_by, updated_by, remark)
SELECT @mu_multi, 'TENANT', @tenant_id, id, NULL, 'ENABLED', 0, 0, 'CODX-P1'
FROM tenant_contact WHERE code = 'CODX-P1-TC-MULTI' AND deleted = 0;

INSERT INTO mobile_user_bind
  (mobile_user_id, bind_type, tenant_id, tenant_contact_id, sys_user_id,
   status, created_by, updated_by, remark)
VALUES
  (@mu_multi, 'INTERNAL', NULL, NULL, @multi_staff_user_id, 'ENABLED', 0, 0, 'CODX-P1');

UPDATE mobile_user
SET current_bind_id = CASE code
  WHEN 'CODX-P1-MU-RESP' THEN @bind_resp
  WHEN 'CODX-P1-MU-EMP1' THEN @bind_emp1
  WHEN 'CODX-P1-MU-EMP2' THEN @bind_emp2
  WHEN 'CODX-P1-MU-STAFF' THEN @bind_staff
  ELSE NULL
END
WHERE code LIKE 'CODX-P1-MU-%' AND deleted = 0;

SELECT code, id, user_type, current_bind_id
FROM mobile_user
WHERE code LIKE 'CODX-P1-MU-%'
ORDER BY code;
