-- Local-only isolated data for Phase 2 tenant business HTTP verification.
-- Depends on phase_one_identity_http_seed.sql. Prefix: CODX-P2.

SET @tenant1 = (SELECT id FROM tenant WHERE code = 'CODX-P1-TENANT' AND deleted = 0);
SET @resp = (SELECT id FROM mobile_user WHERE code = 'CODX-P1-MU-RESP' AND deleted = 0);
SET @emp1 = (SELECT id FROM mobile_user WHERE code = 'CODX-P1-MU-EMP1' AND deleted = 0);
SET @emp2 = (SELECT id FROM mobile_user WHERE code = 'CODX-P1-MU-EMP2' AND deleted = 0);
SET @tc_emp1 = (SELECT id FROM tenant_contact WHERE code = 'CODX-P1-TC-EMP1' AND deleted = 0);
SET @tc_emp2 = (SELECT id FROM tenant_contact WHERE code = 'CODX-P1-TC-EMP2' AND deleted = 0);

DELETE FROM custom_field_value
WHERE field_id IN (SELECT id FROM custom_field_definition WHERE code LIKE 'CODX-P2-CF-%');
DELETE FROM custom_field_definition WHERE code LIKE 'CODX-P2-CF-%';
DELETE FROM visitor_appointment WHERE code LIKE 'CODX-P2-VIS-%';
DELETE FROM work_order_record
WHERE work_order_id IN (SELECT id FROM work_order WHERE code LIKE 'CODX-P2-WO-%');
DELETE FROM work_order WHERE code LIKE 'CODX-P2-WO-%';
DELETE FROM meeting_booking WHERE code LIKE 'CODX-P2-MBK-%';
DELETE FROM meeting_room WHERE code = 'CODX-P2-MR-1';
DELETE FROM parking_assignment WHERE code LIKE 'CODX-P2-PA-%';
DELETE FROM parking_space WHERE code LIKE 'CODX-P2-PS-%';
DELETE FROM energy_reading WHERE code LIKE 'CODX-P2-ER-%';
DELETE FROM energy_meter_binding WHERE code LIKE 'CODX-P2-EMB-%';
DELETE FROM energy_meter WHERE code LIKE 'CODX-P2-EM-%';
DELETE FROM bill WHERE code LIKE 'CODX-P2-BILL-%';
DELETE FROM contract_room
WHERE contract_id IN (SELECT id FROM contract WHERE code LIKE 'CODX-P2-CON-%');
DELETE FROM contract WHERE code LIKE 'CODX-P2-CON-%';
DELETE FROM building_room WHERE code LIKE 'CODX-P2-ROOM-%';
DELETE FROM building_floor WHERE code = 'CODX-P2-FLOOR-1';
DELETE FROM building WHERE code = 'CODX-P2-BUILDING-1';
DELETE FROM mobile_user_bind
WHERE mobile_user_id IN (SELECT id FROM mobile_user WHERE code = 'CODX-P2-MU-OTHER');
DELETE FROM mobile_user WHERE code = 'CODX-P2-MU-OTHER';
DELETE FROM tenant_contact WHERE code = 'CODX-P2-TC-OTHER';
DELETE FROM tenant WHERE code = 'CODX-P2-TENANT-OTHER';

INSERT INTO tenant
  (code, name, contact_person, contact_phone, status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-TENANT-OTHER', 'Codex Phase2 Other Tenant', 'Other Owner',
   '13800138101', 'ENABLED', 0, 0, 'CODX-P2');
SET @tenant2 = LAST_INSERT_ID();

INSERT INTO tenant_contact
  (code, tenant_id, name, phone, role, is_primary, status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-TC-OTHER', @tenant2, 'Phase2 Other Responsible', '13800138101',
   'RESPONSIBLE', 1, 'ENABLED', 0, 0, 'CODX-P2');
SET @tc_other = LAST_INSERT_ID();

INSERT INTO mobile_user
  (code, nickname, phone, user_type, status, last_login_at, created_by, updated_by, remark)
VALUES
  ('CODX-P2-MU-OTHER', 'Phase2 Other Responsible', '13800138101',
   'TENANT', 'ENABLED', NOW(), 0, 0, 'CODX-P2');
SET @mu_other = LAST_INSERT_ID();

INSERT INTO mobile_user_bind
  (mobile_user_id, bind_type, tenant_id, tenant_contact_id, status, created_by, updated_by, remark)
VALUES
  (@mu_other, 'TENANT', @tenant2, @tc_other, 'ENABLED', 0, 0, 'CODX-P2');
SET @bind_other = LAST_INSERT_ID();
UPDATE mobile_user SET current_bind_id = @bind_other WHERE id = @mu_other;

INSERT INTO building
  (code, name, address, total_floor, status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-BUILDING-1', 'Phase2 Test Building', 'Local Test Address',
   1, 'ENABLED', 0, 0, 'CODX-P2');
SET @building = LAST_INSERT_ID();

INSERT INTO building_floor
  (code, building_id, floor_no, name, total_area, status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-FLOOR-1', @building, 1, '1F', 300.00, 'ENABLED', 0, 0, 'CODX-P2');
SET @floor = LAST_INSERT_ID();

INSERT INTO building_room
  (code, building_id, floor_id, room_no, area, property_fee_area,
   usage_type, rent_status, status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-ROOM-T1', @building, @floor, 'P2-101', 120.00, 120.00,
   'OFFICE', 'RENTED', 'ENABLED', 0, 0, 'CODX-P2'),
  ('CODX-P2-ROOM-T2', @building, @floor, 'P2-102', 80.00, 80.00,
   'OFFICE', 'RENTED', 'ENABLED', 0, 0, 'CODX-P2');
SET @room1 = (SELECT id FROM building_room WHERE code = 'CODX-P2-ROOM-T1');
SET @room2 = (SELECT id FROM building_room WHERE code = 'CODX-P2-ROOM-T2');

INSERT INTO contract
  (code, tenant_id, room_id, start_date, end_date, rent_amount, deposit_amount,
   status, effective_date, currency, current_revision_no, version,
   created_by, updated_by, remark)
VALUES
  ('CODX-P2-CON-T1', @tenant1, @room1, '2026-01-01', '2026-12-31',
   12000.00, 24000.00, 'ACTIVE', '2026-01-01', 'CNY', 1, 0, 0, 0, 'Phase2 Tenant1 Contract'),
  ('CODX-P2-CON-T2', @tenant2, @room2, '2026-01-01', '2026-12-31',
   8000.00, 16000.00, 'ACTIVE', '2026-01-01', 'CNY', 1, 0, 0, 0, 'Phase2 Tenant2 Contract');
SET @contract1 = (SELECT id FROM contract WHERE code = 'CODX-P2-CON-T1');
SET @contract2 = (SELECT id FROM contract WHERE code = 'CODX-P2-CON-T2');

INSERT INTO bill
  (code, tenant_id, contract_id, source_type, source_id, bill_type, title,
   amount, paid_amount, period_start, period_end, due_date,
   audit_status, pay_status, status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-BILL-T1-1', @tenant1, @contract1, 'CONTRACT', @contract1, 'RENT',
   'Phase2 Tenant1 Rent', 12000.00, 0.00, '2026-07-01', '2026-07-31', '2026-07-10',
   'APPROVED', 'UNPAID', 'ACTIVE', 0, 0, 'CODX-P2 snapshot'),
  ('CODX-P2-BILL-T1-2', @tenant1, @contract1, 'OTHER', NULL, 'PROPERTY',
   'Phase2 Tenant1 Property', 1200.00, 1200.00, '2026-06-01', '2026-06-30', '2026-06-10',
   'APPROVED', 'PAID', 'ACTIVE', 0, 0, 'CODX-P2 snapshot'),
  ('CODX-P2-BILL-T2-1', @tenant2, @contract2, 'CONTRACT', @contract2, 'RENT',
   'Phase2 Tenant2 Rent', 8000.00, 0.00, '2026-07-01', '2026-07-31', '2026-07-10',
   'APPROVED', 'UNPAID', 'ACTIVE', 0, 0, 'CODX-P2 snapshot');

INSERT INTO energy_meter
  (code, meter_no, meter_name, meter_type, building_id, floor_id, room_id,
   tenant_id, unit, multiplier, initial_reading, commissioning_date,
   status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-EM-T1', 'P2-E-101', 'P2 Tenant1 Electric', 'ELECTRIC',
   @building, @floor, @room1, @tenant1, 'kWh', 1, 100, '2026-01-01',
   'ACTIVE', 0, 0, 'CODX-P2'),
  ('CODX-P2-EM-T2', 'P2-E-102', 'P2 Tenant2 Electric', 'ELECTRIC',
   @building, @floor, @room2, @tenant2, 'kWh', 1, 200, '2026-01-01',
   'ACTIVE', 0, 0, 'CODX-P2');
SET @meter1 = (SELECT id FROM energy_meter WHERE code = 'CODX-P2-EM-T1');
SET @meter2 = (SELECT id FROM energy_meter WHERE code = 'CODX-P2-EM-T2');

INSERT INTO energy_meter_binding
  (code, meter_id, subject_type, tenant_id, room_id, effective_from,
   status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-EMB-T1', @meter1, 'TENANT', @tenant1, @room1, '2026-01-01',
   'ACTIVE', 0, 0, 'CODX-P2'),
  ('CODX-P2-EMB-T2', @meter2, 'TENANT', @tenant2, @room2, '2026-01-01',
   'ACTIVE', 0, 0, 'CODX-P2');

INSERT INTO energy_reading
  (code, meter_id, period_month, reading_date, previous_reading, current_reading,
   usage_amount, multiplier, unit_price, settlement_amount, operator_name,
   reading_source, bill_status, abnormal_flag, abnormal_status,
   status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-ER-T1-06', @meter1, '2026-06', '2026-06-30', 100, 150, 50, 1, 0.80, 40.00,
   'Phase2 Meter', 'MANUAL', 'PENDING', 0, 'NORMAL', 'ACTIVE', 0, 0, 'CODX-P2'),
  ('CODX-P2-ER-T1-07', @meter1, '2026-07', '2026-07-26', 150, 210, 60, 1, 0.80, 48.00,
   'Phase2 Meter', 'MANUAL', 'PENDING', 0, 'NORMAL', 'ACTIVE', 0, 0, 'CODX-P2'),
  ('CODX-P2-ER-T2-07', @meter2, '2026-07', '2026-07-26', 200, 240, 40, 1, 0.80, 32.00,
   'Phase2 Meter', 'MANUAL', 'PENDING', 0, 'NORMAL', 'ACTIVE', 0, 0, 'CODX-P2');

INSERT INTO parking_space
  (code, space_no, area, location, space_type, monthly_rate, rent_status,
   status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-PS-1', 'P2-A001', 'A区', 'B1', 'MONTHLY', 500.00, 'RENTED', 'ENABLED', 0, 0, 'CODX-P2'),
  ('CODX-P2-PS-2', 'P2-A002', 'A区', 'B1', 'MONTHLY', 500.00, 'RENTED', 'ENABLED', 0, 0, 'CODX-P2'),
  ('CODX-P2-PS-3', 'P2-B001', 'B区', 'B2', 'MONTHLY', 400.00, 'RENTED', 'ENABLED', 0, 0, 'CODX-P2');
SET @ps1 = (SELECT id FROM parking_space WHERE code = 'CODX-P2-PS-1');
SET @ps2 = (SELECT id FROM parking_space WHERE code = 'CODX-P2-PS-2');
SET @ps3 = (SELECT id FROM parking_space WHERE code = 'CODX-P2-PS-3');

INSERT INTO parking_assignment
  (code, parking_space_id, tenant_id, tenant_contact_id,
   mobile_user_id, car_plate_no, vehicle_type, start_date, end_date, status,
   monthly_fee, unit_price_snapshot, created_by, updated_by, remark)
VALUES
  ('CODX-P2-PA-EMP1', @ps1, @tenant1, @tc_emp1, @emp1,
   '京A-P2001', 'CAR', '2026-01-01', '2026-12-31', 'ACTIVE', 500.00, 500.00, 0, 0, 'CODX-P2'),
  ('CODX-P2-PA-EMP2', @ps2, @tenant1, @tc_emp2, @emp2,
   '京A-P2002', 'CAR', '2026-01-01', '2026-12-31', 'ACTIVE', 500.00, 500.00, 0, 0, 'CODX-P2'),
  ('CODX-P2-PA-OTHER', @ps3, @tenant2, @tc_other, @mu_other,
   '京B-P2101', 'CAR', '2026-01-01', '2026-12-31', 'ACTIVE', 400.00, 400.00, 0, 0, 'CODX-P2');

INSERT INTO meeting_room
  (code, name, capacity, location, facilities, description, open_time, close_time,
   image_file_ids, hourly_price, status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-MR-1', 'Phase2 Shared Meeting Room', 12, 'Building 1F',
   '投影、白板', '公开展示会议室', '08:00:00', '22:00:00', JSON_ARRAY(), 100.00,
   'ENABLED', 0, 0, 'CODX-P2');
SET @meeting_room = LAST_INSERT_ID();

INSERT INTO meeting_booking
  (code, meeting_room_id, tenant_id, applicant_mobile_user_id, start_time, end_time,
   purpose, participant_count, contact_name, contact_phone, unit_price_snapshot,
   duration_minutes, amount, booking_status, status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-MBK-EMP1', @meeting_room, @tenant1, @emp1,
   '2026-08-01 10:00:00', '2026-08-01 11:00:00', 'Employee1 Meeting', 4,
   'Phase1 Employee One', '13800138002', 100.00, 60, 100.00, 'BOOKED', 'ACTIVE', @emp1, @emp1, 'CODX-P2'),
  ('CODX-P2-MBK-EMP2', @meeting_room, @tenant1, @emp2,
   '2026-08-01 14:00:00', '2026-08-01 15:00:00', 'Employee2 Meeting', 4,
   'Phase1 Employee Two', '13800138003', 100.00, 60, 100.00, 'BOOKED', 'ACTIVE', @emp2, @emp2, 'CODX-P2');

INSERT INTO work_order
  (code, tenant_id, room_id, submitter_mobile_user_id, reporter_name, reporter_phone,
   title, order_type, category, priority, location, description,
   work_status, status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-WO-EMP1', @tenant1, @room1, @emp1, 'Phase1 Employee One', '13800138002',
   'Employee1 Aircon', 'REPAIR', 'AIR_CONDITIONER', 'NORMAL', 'P2-101', 'No cooling',
   'CREATED', 'ACTIVE', @emp1, @emp1, 'CODX-P2'),
  ('CODX-P2-WO-EMP2', @tenant1, @room1, @emp2, 'Phase1 Employee Two', '13800138003',
   'Employee2 Network', 'REPAIR', 'NETWORK', 'NORMAL', 'P2-101', 'Network unstable',
   'COMPLETED', 'ACTIVE', @emp2, @emp2, 'CODX-P2');

INSERT INTO visitor_appointment
  (code, tenant_id, applicant_mobile_user_id, visitor_name, visitor_phone,
   visit_company, car_plate_no, visit_time, visit_reason,
   appointment_status, status, created_by, updated_by, remark)
VALUES
  ('CODX-P2-VIS-EMP1', @tenant1, @emp1, 'Visitor One', '13900139001',
   'Visitor Company One', '京V-P2001', '2026-08-02 10:00:00', 'Business',
   'REGISTERED', 'ACTIVE', @emp1, @emp1, 'CODX-P2'),
  ('CODX-P2-VIS-EMP2', @tenant1, @emp2, 'Visitor Two', '13900139002',
   'Visitor Company Two', NULL, '2026-08-02 14:00:00', 'Interview',
   'REGISTERED', 'ACTIVE', @emp2, @emp2, 'CODX-P2');

INSERT INTO custom_field_definition
  (code, module, workspace_key, entity_type, field_key, label, description,
   required_flag, unique_value, field_type, options_json, config_json, version,
   sort_order, sortable, groupable, status, deleted, created_by, updated_by)
VALUES
  ('CODX-P2-CF-WO', 'workorders', 'workorders', 'work_order', 'mobileNote',
   '移动备注', 'Phase2 HTTP custom field', 0, 0, 'TEXT', '[]', '{}', 1, 900, 0, 0, 'ENABLED', 0, 0, 0),
  ('CODX-P2-CF-VIS', 'visitors', 'visitors', 'visitor_appointment', 'mobileNote',
   '移动备注', 'Phase2 HTTP custom field', 0, 0, 'TEXT', '[]', '{}', 1, 900, 0, 0, 'ENABLED', 0, 0, 0),
  ('CODX-P2-CF-MBK', 'meeting-bookings', 'meeting-bookings', 'meeting_booking', 'mobileNote',
   '移动备注', 'Phase2 HTTP custom field', 0, 0, 'TEXT', '[]', '{}', 1, 900, 0, 0, 'ENABLED', 0, 0, 0);

SELECT 'tenant1' AS kind, @tenant1 AS id
UNION ALL SELECT 'tenant2', @tenant2
UNION ALL SELECT 'responsible', @resp
UNION ALL SELECT 'employee1', @emp1
UNION ALL SELECT 'employee2', @emp2
UNION ALL SELECT 'otherResponsible', @mu_other;
