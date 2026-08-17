-- Removes only the isolated CODX-P1 verification records.
DELETE FROM visitor_pass_record
WHERE appointment_id IN (
  SELECT id FROM visitor_appointment WHERE code LIKE 'VIS-%' AND remark = 'CODX-P1 HTTP'
);
DELETE FROM visitor_appointment WHERE remark = 'CODX-P1 HTTP';
DELETE FROM work_order_record
WHERE work_order_id IN (
  SELECT id FROM work_order WHERE remark LIKE '%CODX-P1 HTTP%'
);
DELETE FROM work_order WHERE remark LIKE '%CODX-P1 HTTP%';
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
