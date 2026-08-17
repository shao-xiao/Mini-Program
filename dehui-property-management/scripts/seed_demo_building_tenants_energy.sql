START TRANSACTION;

INSERT INTO building (code, name, address, status, total_floor, created_by, updated_by, remark)
SELECT 'BLDG_DEMO_A', '德汇创新中心A座（演示）', '南京市江东区示例路88号', 'ENABLED', 3, 0, 0, '演示数据：虚拟楼宇'
WHERE NOT EXISTS (SELECT 1 FROM building WHERE code = 'BLDG_DEMO_A');

SET @building_id = (SELECT id FROM building WHERE code = 'BLDG_DEMO_A' LIMIT 1);

INSERT INTO building_floor (code, building_id, floor_no, name, status, created_by, updated_by, remark)
SELECT 'FLR_DEMO_A_1', @building_id, 1, '1F', 'ENABLED', 0, 0, '演示数据：虚拟楼层'
WHERE NOT EXISTS (SELECT 1 FROM building_floor WHERE code = 'FLR_DEMO_A_1');
INSERT INTO building_floor (code, building_id, floor_no, name, status, created_by, updated_by, remark)
SELECT 'FLR_DEMO_A_2', @building_id, 2, '2F', 'ENABLED', 0, 0, '演示数据：虚拟楼层'
WHERE NOT EXISTS (SELECT 1 FROM building_floor WHERE code = 'FLR_DEMO_A_2');
INSERT INTO building_floor (code, building_id, floor_no, name, status, created_by, updated_by, remark)
SELECT 'FLR_DEMO_A_3', @building_id, 3, '3F', 'ENABLED', 0, 0, '演示数据：虚拟楼层'
WHERE NOT EXISTS (SELECT 1 FROM building_floor WHERE code = 'FLR_DEMO_A_3');

SET @floor1_id = (SELECT id FROM building_floor WHERE code = 'FLR_DEMO_A_1' LIMIT 1);
SET @floor2_id = (SELECT id FROM building_floor WHERE code = 'FLR_DEMO_A_2' LIMIT 1);
SET @floor3_id = (SELECT id FROM building_floor WHERE code = 'FLR_DEMO_A_3' LIMIT 1);

INSERT INTO building_room (code, building_id, floor_id, room_no, area, usage_type, rent_status, status, created_by, updated_by, remark)
SELECT 'RM_DEMO_A_101', @building_id, @floor1_id, '101', 120.00, 'OFFICE', 'VACANT', 'ENABLED', 0, 0, '演示数据：虚拟房间'
WHERE NOT EXISTS (SELECT 1 FROM building_room WHERE code = 'RM_DEMO_A_101');
INSERT INTO building_room (code, building_id, floor_id, room_no, area, usage_type, rent_status, status, created_by, updated_by, remark)
SELECT 'RM_DEMO_A_201', @building_id, @floor2_id, '201', 128.00, 'OFFICE', 'VACANT', 'ENABLED', 0, 0, '演示数据：虚拟房间'
WHERE NOT EXISTS (SELECT 1 FROM building_room WHERE code = 'RM_DEMO_A_201');
INSERT INTO building_room (code, building_id, floor_id, room_no, area, usage_type, rent_status, status, created_by, updated_by, remark)
SELECT 'RM_DEMO_A_301', @building_id, @floor3_id, '301', 156.00, 'OFFICE', 'VACANT', 'ENABLED', 0, 0, '演示数据：虚拟房间'
WHERE NOT EXISTS (SELECT 1 FROM building_room WHERE code = 'RM_DEMO_A_301');

SET @room1_id = (SELECT id FROM building_room WHERE code = 'RM_DEMO_A_101' LIMIT 1);
SET @room2_id = (SELECT id FROM building_room WHERE code = 'RM_DEMO_A_201' LIMIT 1);
SET @room3_id = (SELECT id FROM building_room WHERE code = 'RM_DEMO_A_301' LIMIT 1);

INSERT INTO tenant (code, name, contact_phone, status, created_by, updated_by, remark)
SELECT 'TEN_DEMO_A1', '旭日科技有限公司', '13800138001', 'ENABLED', 0, 0, '演示数据：虚拟租户'
WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'TEN_DEMO_A1');
INSERT INTO tenant (code, name, contact_phone, status, created_by, updated_by, remark)
SELECT 'TEN_DEMO_A2', '晨光咨询有限公司', '13800138002', 'ENABLED', 0, 0, '演示数据：虚拟租户'
WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'TEN_DEMO_A2');
INSERT INTO tenant (code, name, contact_phone, status, created_by, updated_by, remark)
SELECT 'TEN_DEMO_A3', '星辉创新公司', '13800138003', 'ENABLED', 0, 0, '演示数据：虚拟租户'
WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE code = 'TEN_DEMO_A3');

SET @tenant1_id = (SELECT id FROM tenant WHERE code = 'TEN_DEMO_A1' LIMIT 1);
SET @tenant2_id = (SELECT id FROM tenant WHERE code = 'TEN_DEMO_A2' LIMIT 1);
SET @tenant3_id = (SELECT id FROM tenant WHERE code = 'TEN_DEMO_A3' LIMIT 1);

INSERT INTO tenant_contact (code, tenant_id, name, phone, role, is_primary, status, created_by, updated_by, remark)
SELECT 'TC_DEMO_A1', @tenant1_id, '王经理', '13800138001', '管理员', 1, 'ENABLED', 0, 0, '演示数据：虚拟联系人'
WHERE NOT EXISTS (SELECT 1 FROM tenant_contact WHERE code = 'TC_DEMO_A1');
INSERT INTO tenant_contact (code, tenant_id, name, phone, role, is_primary, status, created_by, updated_by, remark)
SELECT 'TC_DEMO_A2', @tenant2_id, '李经理', '13800138002', '管理员', 1, 'ENABLED', 0, 0, '演示数据：虚拟联系人'
WHERE NOT EXISTS (SELECT 1 FROM tenant_contact WHERE code = 'TC_DEMO_A2');
INSERT INTO tenant_contact (code, tenant_id, name, phone, role, is_primary, status, created_by, updated_by, remark)
SELECT 'TC_DEMO_A3', @tenant3_id, '张经理', '13800138003', '管理员', 1, 'ENABLED', 0, 0, '演示数据：虚拟联系人'
WHERE NOT EXISTS (SELECT 1 FROM tenant_contact WHERE code = 'TC_DEMO_A3');

INSERT INTO energy_meter (code, meter_no, meter_name, meter_type, building_id, floor_id, room_id, tenant_id, unit, multiplier, status, created_by, updated_by, remark)
SELECT 'MTR_ELEC_A1', 'ELEC_DEMO_A1', 'A1室内表', 'ELECTRIC', @building_id, @floor1_id, @room1_id, @tenant1_id, 'kWh', 1.0000, 'ENABLED', 0, 0, '演示数据：虚拟电表'
WHERE NOT EXISTS (SELECT 1 FROM energy_meter WHERE code = 'MTR_ELEC_A1');
INSERT INTO energy_meter (code, meter_no, meter_name, meter_type, building_id, floor_id, room_id, tenant_id, unit, multiplier, status, created_by, updated_by, remark)
SELECT 'MTR_WATER_A1', 'WATER_DEMO_A1', 'A1生活供水表', 'WATER', @building_id, @floor1_id, @room1_id, @tenant1_id, 'm3', 1.0000, 'ENABLED', 0, 0, '演示数据：虚拟水表'
WHERE NOT EXISTS (SELECT 1 FROM energy_meter WHERE code = 'MTR_WATER_A1');
INSERT INTO energy_meter (code, meter_no, meter_name, meter_type, building_id, floor_id, room_id, tenant_id, unit, multiplier, status, created_by, updated_by, remark)
SELECT 'MTR_ELEC_A2', 'ELEC_DEMO_A2', 'A2室内表', 'ELECTRIC', @building_id, @floor2_id, @room2_id, @tenant2_id, 'kWh', 1.0000, 'ENABLED', 0, 0, '演示数据：虚拟电表'
WHERE NOT EXISTS (SELECT 1 FROM energy_meter WHERE code = 'MTR_ELEC_A2');
INSERT INTO energy_meter (code, meter_no, meter_name, meter_type, building_id, floor_id, room_id, tenant_id, unit, multiplier, status, created_by, updated_by, remark)
SELECT 'MTR_WATER_A2', 'WATER_DEMO_A2', 'A2生活供水表', 'WATER', @building_id, @floor2_id, @room2_id, @tenant2_id, 'm3', 1.0000, 'ENABLED', 0, 0, '演示数据：虚拟水表'
WHERE NOT EXISTS (SELECT 1 FROM energy_meter WHERE code = 'MTR_WATER_A2');
INSERT INTO energy_meter (code, meter_no, meter_name, meter_type, building_id, floor_id, room_id, tenant_id, unit, multiplier, status, created_by, updated_by, remark)
SELECT 'MTR_ELEC_A3', 'ELEC_DEMO_A3', 'A3室内表', 'ELECTRIC', @building_id, @floor3_id, @room3_id, @tenant3_id, 'kWh', 1.0000, 'ENABLED', 0, 0, '演示数据：虚拟电表'
WHERE NOT EXISTS (SELECT 1 FROM energy_meter WHERE code = 'MTR_ELEC_A3');
INSERT INTO energy_meter (code, meter_no, meter_name, meter_type, building_id, floor_id, room_id, tenant_id, unit, multiplier, status, created_by, updated_by, remark)
SELECT 'MTR_WATER_A3', 'WATER_DEMO_A3', 'A3生活供水表', 'WATER', @building_id, @floor3_id, @room3_id, @tenant3_id, 'm3', 1.0000, 'ENABLED', 0, 0, '演示数据：虚拟水表'
WHERE NOT EXISTS (SELECT 1 FROM energy_meter WHERE code = 'MTR_WATER_A3');

SET @elec1 = (SELECT id FROM energy_meter WHERE code = 'MTR_ELEC_A1' LIMIT 1);
SET @water1 = (SELECT id FROM energy_meter WHERE code = 'MTR_WATER_A1' LIMIT 1);
SET @elec2 = (SELECT id FROM energy_meter WHERE code = 'MTR_ELEC_A2' LIMIT 1);
SET @water2 = (SELECT id FROM energy_meter WHERE code = 'MTR_WATER_A2' LIMIT 1);
SET @elec3 = (SELECT id FROM energy_meter WHERE code = 'MTR_ELEC_A3' LIMIT 1);
SET @water3 = (SELECT id FROM energy_meter WHERE code = 'MTR_WATER_A3' LIMIT 1);

INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_ELEC_A1_2026_05', @elec1, '2026-05', '2026-05-31', 10800.00, 11245.60, 445.60, 1.0000, 0.8000, 356.48, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-05电表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_ELEC_A1_2026_05');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_ELEC_A1_2026_06', @elec1, '2026-06', '2026-06-30', 11245.60, 11680.20, 434.60, 1.0000, 0.8200, 356.37, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-06电表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_ELEC_A1_2026_06');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_ELEC_A1_2026_07', @elec1, '2026-07', '2026-07-31', 11680.20, 11990.90, 310.70, 1.0000, 0.8200, 254.77, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-07电表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_ELEC_A1_2026_07');

INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_WATER_A1_2026_05', @water1, '2026-05', '2026-05-31', 250.00, 279.20, 29.20, 1.0000, 6.0000, 175.20, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-05水表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_WATER_A1_2026_05');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_WATER_A1_2026_06', @water1, '2026-06', '2026-06-30', 279.20, 310.90, 31.70, 1.0000, 6.2000, 196.54, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-06水表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_WATER_A1_2026_06');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_WATER_A1_2026_07', @water1, '2026-07', '2026-07-31', 310.90, 339.10, 28.20, 1.0000, 6.2000, 174.84, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-07水表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_WATER_A1_2026_07');

INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_ELEC_A2_2026_05', @elec2, '2026-05', '2026-05-31', 9800.00, 10155.80, 355.80, 1.0000, 0.8000, 284.64, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-05电表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_ELEC_A2_2026_05');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_ELEC_A2_2026_06', @elec2, '2026-06', '2026-06-30', 10155.80, 10540.20, 384.40, 1.0000, 0.8200, 315.21, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-06电表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_ELEC_A2_2026_06');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_ELEC_A2_2026_07', @elec2, '2026-07', '2026-07-31', 10540.20, 10860.45, 320.25, 1.0000, 0.8200, 262.60, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-07电表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_ELEC_A2_2026_07');

INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_WATER_A2_2026_05', @water2, '2026-05', '2026-05-31', 320.00, 340.70, 20.70, 1.0000, 6.0000, 124.20, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-05水表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_WATER_A2_2026_05');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_WATER_A2_2026_06', @water2, '2026-06', '2026-06-30', 340.70, 365.30, 24.60, 1.0000, 6.2000, 152.52, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-06水表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_WATER_A2_2026_06');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_WATER_A2_2026_07', @water2, '2026-07', '2026-07-31', 365.30, 386.10, 20.80, 1.0000, 6.2000, 128.96, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-07水表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_WATER_A2_2026_07');

INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_ELEC_A3_2026_05', @elec3, '2026-05', '2026-05-31', 14000.00, 14510.40, 510.40, 1.0000, 0.8000, 408.32, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-05电表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_ELEC_A3_2026_05');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_ELEC_A3_2026_06', @elec3, '2026-06', '2026-06-30', 14510.40, 14960.90, 450.50, 1.0000, 0.8200, 369.41, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-06电表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_ELEC_A3_2026_06');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_ELEC_A3_2026_07', @elec3, '2026-07', '2026-07-31', 14960.90, 15405.70, 444.80, 1.0000, 0.8200, 364.74, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-07电表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_ELEC_A3_2026_07');

INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_WATER_A3_2026_05', @water3, '2026-05', '2026-05-31', 410.00, 448.20, 38.20, 1.0000, 6.0000, 229.20, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-05水表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_WATER_A3_2026_05');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_WATER_A3_2026_06', @water3, '2026-06', '2026-06-30', 448.20, 476.10, 27.90, 1.0000, 6.2000, 172.98, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-06水表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_WATER_A3_2026_06');
INSERT INTO energy_reading (code, meter_id, period_month, reading_date, previous_reading, current_reading, usage_amount, multiplier, unit_price, settlement_amount, operator_name, bill_status, abnormal_flag, abnormal_reason, abnormal_status, status, created_by, updated_by, remark)
SELECT 'ENR_WATER_A3_2026_07', @water3, '2026-07', '2026-07-31', 476.10, 509.50, 33.40, 1.0000, 6.2000, 207.08, 'system', 'NOT_GENERATED', 0, NULL, 'PENDING', 'ACTIVE', 0, 0, '演示数据：2026-07水表读数'
WHERE NOT EXISTS (SELECT 1 FROM energy_reading WHERE code = 'ENR_WATER_A3_2026_07');

UPDATE building
SET total_floor = 3
WHERE id = @building_id;

COMMIT;
