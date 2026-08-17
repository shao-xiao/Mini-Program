-- Local-only isolated data for Phase 3 STAFF workbench HTTP verification.
-- Prefix: CODX-P3. Never run against production.

SET @repair_user = (SELECT id FROM sys_user WHERE code = 'CODX-P3-SU-REPAIR' AND deleted = 0);
SET @electric_user = (SELECT id FROM sys_user WHERE code = 'CODX-P3-SU-ELECTRIC' AND deleted = 0);
SET @operation_user = (SELECT id FROM sys_user WHERE code = 'CODX-P3-SU-OPERATION' AND deleted = 0);
SET @visitor_user = (SELECT id FROM sys_user WHERE code = 'CODX-P3-SU-VISITOR' AND deleted = 0);
SET @investment_user = (SELECT id FROM sys_user WHERE code = 'CODX-P3-SU-INVESTMENT' AND deleted = 0);

UPDATE work_order
SET assignee_id = NULL, work_status = 'CREATED'
WHERE code = 'CODX-P2-WO-EMP1';

DELETE FROM inspection_record_history
WHERE inspection_id IN (SELECT id FROM inspection_record WHERE code LIKE 'CODX-P3-INSP-%');
DELETE FROM inspection_attachment
WHERE inspection_id IN (SELECT id FROM inspection_record WHERE code LIKE 'CODX-P3-INSP-%');
DELETE FROM inspection_record WHERE code LIKE 'CODX-P3-INSP-%';
DELETE FROM energy_reading_audit
WHERE reading_id IN (SELECT id FROM energy_reading WHERE code LIKE 'CODX-P3-ER-%');
DELETE FROM energy_reading WHERE code LIKE 'CODX-P3-ER-%';
DELETE FROM energy_price_setting WHERE code = 'CODX-P3-PRICE-202609-ELECTRIC';
DELETE FROM visitor_pass_record
WHERE appointment_id IN (SELECT id FROM visitor_appointment WHERE code = 'CODX-P2-VIS-EMP1');
DELETE FROM investment_follow_record
WHERE content LIKE 'CODX-P3%';
DELETE FROM investment_lead_status_history
WHERE lead_id IN (SELECT id FROM investment_lead WHERE code = 'CODX-P3-LEAD-1');
DELETE FROM investment_lead WHERE code = 'CODX-P3-LEAD-1';
DELETE FROM work_order_attachment
WHERE work_order_id IN (SELECT id FROM work_order WHERE code = 'CODX-P2-WO-EMP1')
  AND created_by IN (@repair_user, 0);
DELETE FROM work_order_record
WHERE work_order_id IN (SELECT id FROM work_order WHERE code = 'CODX-P2-WO-EMP1')
  AND operator_id = @repair_user;
DELETE FROM file_upload WHERE code LIKE 'CODX-P3-FILE-%';

DELETE FROM mobile_user_bind
WHERE mobile_user_id IN (SELECT id FROM mobile_user WHERE code LIKE 'CODX-P3-MU-%');
DELETE FROM mobile_user WHERE code LIKE 'CODX-P3-MU-%';
DELETE FROM sys_user_role
WHERE user_id IN (SELECT id FROM sys_user WHERE code LIKE 'CODX-P3-SU-%');
DELETE FROM sys_user WHERE code LIKE 'CODX-P3-SU-%';
DELETE FROM sys_role_permission
WHERE role_id IN (SELECT id FROM sys_role WHERE code LIKE 'CODX_P3_%');
DELETE FROM sys_role WHERE code LIKE 'CODX_P3_%';

INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark)
VALUES
  ('CODX_P3_REPAIR', 'Phase3 Repair Mobile', 'BUSINESS', 'ENABLED', 0, 0, 'CODX-P3'),
  ('CODX_P3_ELECTRIC', 'Phase3 Energy Mobile', 'BUSINESS', 'ENABLED', 0, 0, 'CODX-P3'),
  ('CODX_P3_OPERATION', 'Phase3 Operation Mobile', 'BUSINESS', 'ENABLED', 0, 0, 'CODX-P3'),
  ('CODX_P3_VISITOR', 'Phase3 Visitor Mobile', 'BUSINESS', 'ENABLED', 0, 0, 'CODX-P3'),
  ('CODX_P3_INVESTMENT', 'Phase3 Investment Mobile', 'BUSINESS', 'ENABLED', 0, 0, 'CODX-P3'),
  ('CODX_P3_FULL', 'Phase3 Full Mobile Matrix', 'BUSINESS', 'ENABLED', 0, 0, 'CODX-P3'),
  ('CODX_P3_READONLY', 'Phase3 Operation Readonly Matrix', 'BUSINESS', 'ENABLED', 0, 0, 'CODX-P3'),
  ('CODX_P3_NONE', 'Phase3 No Mobile Permission', 'BUSINESS', 'ENABLED', 0, 0, 'CODX-P3');

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT role_record.id, permission_record.id, 'ENABLED', 0, 0
FROM sys_role role_record
JOIN sys_permission permission_record
  ON permission_record.deleted = 0 AND permission_record.status = 'ENABLED'
WHERE role_record.code = 'CODX_P3_REPAIR'
  AND permission_record.code IN ('workorder:view', 'workorder:update', 'workorder:complete');

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT role_record.id, permission_record.id, 'ENABLED', 0, 0
FROM sys_role role_record
JOIN sys_permission permission_record
  ON permission_record.deleted = 0 AND permission_record.status = 'ENABLED'
WHERE role_record.code = 'CODX_P3_ELECTRIC'
  AND permission_record.code IN (
    'energy:view', 'energy:reading:update',
    'workorder:view', 'workorder:update', 'workorder:complete'
  );

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT role_record.id, permission_record.id, 'ENABLED', 0, 0
FROM sys_role role_record
JOIN sys_permission permission_record
  ON permission_record.deleted = 0 AND permission_record.status = 'ENABLED'
WHERE role_record.code = 'CODX_P3_OPERATION'
  AND permission_record.code IN (
    'inspection:view', 'inspection:create', 'inspection:update',
    'meeting-booking:view', 'meeting-booking:confirm',
    'meeting-booking:cancel', 'meeting-booking:complete'
  );

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT role_record.id, permission_record.id, 'ENABLED', 0, 0
FROM sys_role role_record
JOIN sys_permission permission_record
  ON permission_record.deleted = 0 AND permission_record.status = 'ENABLED'
WHERE role_record.code = 'CODX_P3_VISITOR'
  AND permission_record.code IN (
    'inspection:view', 'inspection:create', 'inspection:update',
    'visitor:view', 'visitor:update'
  );

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT role_record.id, permission_record.id, 'ENABLED', 0, 0
FROM sys_role role_record
JOIN sys_permission permission_record
  ON permission_record.deleted = 0 AND permission_record.status = 'ENABLED'
WHERE role_record.code = 'CODX_P3_INVESTMENT'
  AND permission_record.code IN (
    'investment-lead:view', 'investment-lead:follow', 'investment-lead:convert'
  );

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT role_record.id, permission_record.id, 'ENABLED', 0, 0
FROM sys_role role_record
JOIN sys_permission permission_record
  ON permission_record.deleted = 0 AND permission_record.status = 'ENABLED'
WHERE role_record.code = 'CODX_P3_FULL'
  AND permission_record.code IN (
    'inspection:view', 'inspection:create', 'inspection:update',
    'workorder:view', 'workorder:update', 'workorder:complete',
    'energy:view', 'energy:reading:update',
    'meeting-booking:view', 'meeting-booking:confirm',
    'meeting-booking:cancel', 'meeting-booking:complete',
    'visitor:view', 'visitor:update',
    'investment-lead:view', 'investment-lead:follow', 'investment-lead:convert'
  );

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT role_record.id, permission_record.id, 'ENABLED', 0, 0
FROM sys_role role_record
JOIN sys_permission permission_record
  ON permission_record.deleted = 0 AND permission_record.status = 'ENABLED'
WHERE role_record.code = 'CODX_P3_READONLY'
  AND permission_record.code IN ('meeting-booking:view', 'investment-lead:view');

INSERT INTO sys_user (
  code, username, password_hash, real_name, phone, status, mobile_enabled,
  created_by, updated_by, remark
)
VALUES
  ('CODX-P3-SU-REPAIR', 'codx_p3_repair', '$2a$10$disabledPhaseThreeOnly', 'Phase3 Repair Staff', '13800138201', 'ENABLED', 1, 0, 0, 'CODX-P3'),
  ('CODX-P3-SU-ELECTRIC', 'codx_p3_electric', '$2a$10$disabledPhaseThreeOnly', 'Phase3 Energy Staff', '13800138202', 'ENABLED', 1, 0, 0, 'CODX-P3'),
  ('CODX-P3-SU-OPERATION', 'codx_p3_operation', '$2a$10$disabledPhaseThreeOnly', 'Phase3 Operation Staff', '13800138203', 'ENABLED', 1, 0, 0, 'CODX-P3'),
  ('CODX-P3-SU-VISITOR', 'codx_p3_visitor', '$2a$10$disabledPhaseThreeOnly', 'Phase3 Visitor Staff', '13800138204', 'ENABLED', 1, 0, 0, 'CODX-P3'),
  ('CODX-P3-SU-INVESTMENT', 'codx_p3_investment', '$2a$10$disabledPhaseThreeOnly', 'Phase3 Investment Staff', '13800138205', 'ENABLED', 1, 0, 0, 'CODX-P3'),
  ('CODX-P3-SU-FULL', 'codx_p3_full', '$2a$10$disabledPhaseThreeOnly', 'Phase3 Full Mobile Staff', '13800138207', 'ENABLED', 1, 0, 0, 'CODX-P3'),
  ('CODX-P3-SU-READONLY', 'codx_p3_readonly', '$2a$10$disabledPhaseThreeOnly', 'Phase3 Operation Readonly', '13800138208', 'ENABLED', 1, 0, 0, 'CODX-P3'),
  ('CODX-P3-SU-NONE', 'codx_p3_none', '$2a$10$disabledPhaseThreeOnly', 'Phase3 No Mobile Permission', '13800138206', 'ENABLED', 1, 0, 0, 'CODX-P3');

INSERT INTO sys_user_role (user_id, role_id, status, created_by, updated_by)
SELECT user_record.id, role_record.id, 'ENABLED', 0, 0
FROM sys_user user_record
JOIN sys_role role_record
  ON role_record.code = REPLACE(user_record.code, 'CODX-P3-SU-', 'CODX_P3_')
WHERE user_record.code LIKE 'CODX-P3-SU-%';

INSERT INTO mobile_user (
  code, nickname, phone, user_type, status, last_login_at, created_by, updated_by, remark
)
SELECT REPLACE(user_record.code, 'CODX-P3-SU-', 'CODX-P3-MU-'),
       user_record.real_name, user_record.phone, 'STAFF', 'ENABLED', NOW(), 0, 0, 'CODX-P3'
FROM sys_user user_record
WHERE user_record.code LIKE 'CODX-P3-SU-%';

INSERT INTO mobile_user_bind (
  mobile_user_id, bind_type, sys_user_id, status, created_by, updated_by, remark
)
SELECT mobile.id, 'INTERNAL', user_record.id, 'ENABLED', 0, 0, 'CODX-P3'
FROM mobile_user mobile
JOIN sys_user user_record
  ON mobile.code = REPLACE(user_record.code, 'CODX-P3-SU-', 'CODX-P3-MU-')
WHERE mobile.code LIKE 'CODX-P3-MU-%';

UPDATE mobile_user mobile
JOIN mobile_user_bind bind_record
  ON bind_record.mobile_user_id = mobile.id AND bind_record.remark = 'CODX-P3'
SET mobile.current_bind_id = bind_record.id
WHERE mobile.code LIKE 'CODX-P3-MU-%';

SET @repair_user = (SELECT id FROM sys_user WHERE code = 'CODX-P3-SU-REPAIR');
SET @electric_user = (SELECT id FROM sys_user WHERE code = 'CODX-P3-SU-ELECTRIC');
SET @operation_user = (SELECT id FROM sys_user WHERE code = 'CODX-P3-SU-OPERATION');

UPDATE work_order
SET assignee_id = @repair_user, work_status = 'ASSIGNED', updated_by = 0
WHERE code = 'CODX-P2-WO-EMP1';

UPDATE meeting_booking
SET booking_status = 'BOOKED', updated_by = 0
WHERE code = 'CODX-P2-MBK-EMP1';

UPDATE visitor_appointment
SET appointment_status = 'REGISTERED', updated_by = 0
WHERE code = 'CODX-P2-VIS-EMP1';

INSERT INTO energy_price_setting (
  code, period_month, meter_type, unit_price, status, created_by, updated_by, remark
)
VALUES (
  'CODX-P3-PRICE-202609-ELECTRIC', '2026-09', 'ELECTRIC', 0.8800,
  'ENABLED', 0, 0, 'CODX-P3'
);

INSERT INTO investment_lead (
  code, name, phone, company_name, intended_room, room_id, listing_id,
  mobile_user_id, listing_title_snapshot, building_name_snapshot,
  floor_snapshot, room_code_snapshot, area_snapshot, desired_area,
  intended_use, preferred_visit_time, source, lead_status,
  status, created_by, updated_by, remark
)
SELECT 'CODX-P3-LEAD-1', 'Phase3 Viewing Customer', '13800138288', 'Phase3 Prospect Company',
       room.room_no, room.id, listing.id, mobile.id,
       listing.title, building.name, floor.name, room.room_no, room.area, room.area,
       'Office', '2026-08-06 10:00', 'MINIPROGRAM', 'NEW',
       'ENABLED', mobile.id, mobile.id, 'CODX-P3'
FROM building_room room
JOIN building ON building.id = room.building_id
JOIN building_floor floor ON floor.id = room.floor_id
JOIN mobile_user mobile ON mobile.code = 'CODX-P1-MU-EMP1'
LEFT JOIN investment_room_listing listing ON listing.room_id = room.id AND listing.deleted = 0
WHERE room.code = 'CODX-P2-ROOM-T1'
LIMIT 1;

SELECT mobile.code, mobile.id AS mobile_user_id, user_record.id AS sys_user_id,
       role_record.code AS role_code
FROM mobile_user mobile
JOIN mobile_user_bind bind_record ON bind_record.id = mobile.current_bind_id
JOIN sys_user user_record ON user_record.id = bind_record.sys_user_id
LEFT JOIN sys_user_role user_role ON user_role.user_id = user_record.id AND user_role.deleted = 0
LEFT JOIN sys_role role_record ON role_record.id = user_role.role_id AND role_record.deleted = 0
WHERE mobile.code LIKE 'CODX-P3-MU-%'
ORDER BY mobile.code;
