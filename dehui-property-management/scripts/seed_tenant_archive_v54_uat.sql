-- Local-only UAT data for the tenant archive closure. Idempotent by stable UAT codes.
-- This script never deletes existing business data.
SET NAMES utf8mb4;

SET @admin_role_id = (SELECT id FROM sys_role WHERE code = 'ADMIN' AND deleted = 0 LIMIT 1);
INSERT INTO sys_user (
    code, username, password_hash, real_name, phone, email, status,
    mobile_enabled, web_login_enabled, primary_role_id, created_by, updated_by, remark
) VALUES (
    'UAT-USR-TEN-V54', 'uat_tenant_service_v54',
    '$2a$10$XqhZR.pObyI//tr.gLRq7.Joa1SjTQRO0USIJcDEo4UtUarcndRBW',
    '租户档案物业对接人', '13900005400', 'tenant-v54-service@example.test',
    'ENABLED', 0, 1, @admin_role_id, 0, 0, 'V54租户档案手工验收数据'
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), real_name = VALUES(real_name), phone = VALUES(phone),
    status = 'ENABLED', deleted = 0, primary_role_id = VALUES(primary_role_id),
    updated_by = 0, remark = VALUES(remark);
SET @manager_id = LAST_INSERT_ID();
INSERT INTO sys_user_role (user_id, role_id, status, deleted, created_by, updated_by)
SELECT @manager_id, @admin_role_id, 'ENABLED', 0, 0, 0
WHERE @manager_id IS NOT NULL
  AND @admin_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role
      WHERE user_id = @manager_id AND role_id = @admin_role_id AND deleted = 0
  );

INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark)
VALUES ('UAT_TENANT_READER_V54', 'V54租户只读验收', 'BUSINESS', 'ENABLED', 0, 0, '仅用于验证租户权限不泄露财务账单')
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), name = VALUES(name), status = 'ENABLED', deleted = 0, updated_by = 0;
SET @tenant_reader_role_id = LAST_INSERT_ID();
SET @tenant_view_permission_id = (
    SELECT id FROM sys_permission WHERE code = 'tenant:view' AND deleted = 0 LIMIT 1
);
INSERT INTO sys_role_permission (role_id, permission_id, status, deleted, created_by, updated_by)
SELECT @tenant_reader_role_id, @tenant_view_permission_id, 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission
    WHERE role_id = @tenant_reader_role_id
      AND permission_id = @tenant_view_permission_id
      AND deleted = 0
);
INSERT INTO sys_user (
    code, username, password_hash, real_name, phone, email, status,
    mobile_enabled, web_login_enabled, primary_role_id, created_by, updated_by, remark
) VALUES (
    'UAT-USR-TEN-READ-V54', 'uat_tenant_reader_v54',
    '$2a$10$XqhZR.pObyI//tr.gLRq7.Joa1SjTQRO0USIJcDEo4UtUarcndRBW',
    '租户档案只读验收员', '13900005490', 'tenant-v54-reader@example.test',
    'ENABLED', 0, 1, @tenant_reader_role_id, 0, 0, 'V54财务权限隔离验收账号'
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), status = 'ENABLED', deleted = 0,
    primary_role_id = VALUES(primary_role_id), updated_by = 0, remark = VALUES(remark);
SET @tenant_reader_user_id = LAST_INSERT_ID();
INSERT INTO sys_user_role (user_id, role_id, status, deleted, created_by, updated_by)
SELECT @tenant_reader_user_id, @tenant_reader_role_id, 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_role
    WHERE user_id = @tenant_reader_user_id
      AND role_id = @tenant_reader_role_id
      AND deleted = 0
);

INSERT INTO tenant (
    code, name, short_name, contact_person, credit_code, invoice_title, tax_number,
    service_manager_user_id, contact_phone, contact_email, status, created_by, updated_by, remark
) VALUES (
    'UAT-TEN-V54-A', 'V54租户档案验收甲公司', '验收甲',
    '甲公司负责人', '91310000UATV540001', 'V54租户档案验收甲公司',
    '91310000UATV540001', @manager_id, '13900005401', 'tenant-a-v54@example.test',
    'ACTIVE', 0, 0, 'V54租户档案闭环验收主租户'
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), name = VALUES(name), short_name = VALUES(short_name),
    credit_code = VALUES(credit_code), invoice_title = VALUES(invoice_title),
    tax_number = VALUES(tax_number), service_manager_user_id = VALUES(service_manager_user_id),
    status = 'ACTIVE', deleted = 0, updated_by = 0, remark = VALUES(remark);
SET @tenant_a = LAST_INSERT_ID();

INSERT INTO tenant (
    code, name, short_name, contact_person, credit_code, invoice_title, tax_number,
    service_manager_user_id, contact_phone, contact_email, status, created_by, updated_by, remark
) VALUES (
    'UAT-TEN-V54-B', 'V54租户档案验收乙公司', '验收乙',
    '乙公司负责人', '91310000UATV540002', 'V54租户档案验收乙公司',
    '91310000UATV540002', @manager_id, '13900005411', 'tenant-b-v54@example.test',
    'ACTIVE', 0, 0, 'V54跨租户权限验收数据'
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), name = VALUES(name), short_name = VALUES(short_name),
    credit_code = VALUES(credit_code), invoice_title = VALUES(invoice_title),
    tax_number = VALUES(tax_number), service_manager_user_id = VALUES(service_manager_user_id),
    status = 'ACTIVE', deleted = 0, updated_by = 0, remark = VALUES(remark);
SET @tenant_b = LAST_INSERT_ID();

INSERT INTO tenant_contact (
    code, tenant_id, name, phone, email, position, role, is_primary, is_emergency,
    status, created_by, updated_by, remark
) VALUES
    ('UAT-TC-V54-A-R', @tenant_a, '甲公司负责人', '13900005401', 'responsible-a@example.test', '行政负责人', 'RESPONSIBLE', 1, 0, 'ACTIVE', 0, 0, 'V54负责人'),
    ('UAT-TC-V54-A-E1', @tenant_a, '甲公司员工一', '13900005402', 'employee-a1@example.test', '财务专员', 'EMPLOYEE', 0, 0, 'ACTIVE', 0, 0, 'V54普通员工'),
    ('UAT-TC-V54-A-E2', @tenant_a, '甲公司紧急联系人', '13900005403', 'emergency-a@example.test', '安全主管', 'EMPLOYEE', 0, 1, 'ACTIVE', 0, 0, 'V54紧急联系人及手机号变更验收'),
    ('UAT-TC-V54-B-R', @tenant_b, '乙公司负责人', '13900005411', 'responsible-b@example.test', '行政负责人', 'RESPONSIBLE', 1, 1, 'ACTIVE', 0, 0, 'V54跨租户负责人')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), phone = VALUES(phone), email = VALUES(email), position = VALUES(position),
    role = VALUES(role), is_primary = VALUES(is_primary), is_emergency = VALUES(is_emergency),
    status = 'ACTIVE', deleted = 0, updated_by = 0, remark = VALUES(remark);
SET @contact_a_r = (SELECT id FROM tenant_contact WHERE code = 'UAT-TC-V54-A-R' AND deleted = 0 LIMIT 1);
SET @contact_a_e1 = (SELECT id FROM tenant_contact WHERE code = 'UAT-TC-V54-A-E1' AND deleted = 0 LIMIT 1);
SET @contact_a_e2 = (SELECT id FROM tenant_contact WHERE code = 'UAT-TC-V54-A-E2' AND deleted = 0 LIMIT 1);
SET @contact_b_r = (SELECT id FROM tenant_contact WHERE code = 'UAT-TC-V54-B-R' AND deleted = 0 LIMIT 1);

INSERT INTO building (code, name, address, total_floor, status, created_by, updated_by, remark)
VALUES ('UAT-BLD-V54', 'V54验收楼宇', '本地自动化验收地址', 2, 'ENABLED', 0, 0, 'V54租赁空间验收')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), name = VALUES(name), status = 'ENABLED', deleted = 0;
SET @building_id = LAST_INSERT_ID();
INSERT INTO building_floor (code, building_id, floor_no, name, total_area, status, created_by, updated_by, remark)
VALUES ('UAT-FLR-V54-1', @building_id, 1, '1层', 500.00, 'ENABLED', 0, 0, 'V54验收楼层')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), building_id = VALUES(building_id), total_area = VALUES(total_area), deleted = 0;
SET @floor_id = LAST_INSERT_ID();

INSERT INTO building_room (
    code, building_id, floor_id, room_no, area, property_fee_area,
    usage_type, rent_status, status, created_by, updated_by, remark
) VALUES
    ('UAT-RM-V54-101', @building_id, @floor_id, 'UAT-101', 100.00, 100.00, 'OFFICE', 'RENTED', 'ENABLED', 0, 0, '同面积口径验收'),
    ('UAT-RM-V54-102', @building_id, @floor_id, 'UAT-102', 100.00, 100.00, 'OFFICE', 'RENTED', 'ENABLED', 0, 0, '同面积口径验收'),
    ('UAT-RM-V54-103', @building_id, @floor_id, 'UAT-103', 80.00, 80.00, 'OFFICE', 'VACANT', 'ENABLED', 0, 0, '历史租赁验收'),
    ('UAT-RM-V54-104', @building_id, @floor_id, 'UAT-104', 60.00, 60.00, 'OFFICE', 'RENTED', 'ENABLED', 0, 0, '跨租户验收')
ON DUPLICATE KEY UPDATE
    building_id = VALUES(building_id), floor_id = VALUES(floor_id), room_no = VALUES(room_no),
    area = VALUES(area), property_fee_area = VALUES(property_fee_area), status = 'ENABLED', deleted = 0;
SET @room_101 = (SELECT id FROM building_room WHERE code = 'UAT-RM-V54-101' AND deleted = 0 LIMIT 1);
SET @room_102 = (SELECT id FROM building_room WHERE code = 'UAT-RM-V54-102' AND deleted = 0 LIMIT 1);
SET @room_103 = (SELECT id FROM building_room WHERE code = 'UAT-RM-V54-103' AND deleted = 0 LIMIT 1);
SET @room_104 = (SELECT id FROM building_room WHERE code = 'UAT-RM-V54-104' AND deleted = 0 LIMIT 1);

INSERT INTO contract (
    code, tenant_id, room_id, start_date, end_date, rent_amount, deposit_amount,
    status, effective_date, signed_at, currency, version, created_by, updated_by, remark
) VALUES
    ('UAT-CON-V54-A1', @tenant_a, @room_101, DATE_SUB(CURRENT_DATE, INTERVAL 6 MONTH), DATE_ADD(CURRENT_DATE, INTERVAL 18 MONTH), 20000.00, 40000.00, 'ACTIVE', DATE_SUB(CURRENT_DATE, INTERVAL 6 MONTH), NOW(), 'CNY', 1, 0, 0, 'V54当前主合同'),
    ('UAT-CON-V54-A2', @tenant_a, @room_103, DATE_SUB(CURRENT_DATE, INTERVAL 24 MONTH), DATE_SUB(CURRENT_DATE, INTERVAL 12 MONTH), 8000.00, 16000.00, 'TERMINATED', DATE_SUB(CURRENT_DATE, INTERVAL 24 MONTH), DATE_SUB(NOW(), INTERVAL 24 MONTH), 'CNY', 2, 0, 0, 'V54历史合同'),
    ('UAT-CON-V54-B1', @tenant_b, @room_104, DATE_SUB(CURRENT_DATE, INTERVAL 3 MONTH), DATE_ADD(CURRENT_DATE, INTERVAL 12 MONTH), 6000.00, 12000.00, 'ACTIVE', DATE_SUB(CURRENT_DATE, INTERVAL 3 MONTH), NOW(), 'CNY', 1, 0, 0, 'V54跨租户合同')
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id), room_id = VALUES(room_id), start_date = VALUES(start_date),
    end_date = VALUES(end_date), rent_amount = VALUES(rent_amount), deposit_amount = VALUES(deposit_amount),
    status = VALUES(status), deleted = 0, updated_by = 0, remark = VALUES(remark);
SET @contract_a1 = (SELECT id FROM contract WHERE code = 'UAT-CON-V54-A1' AND deleted = 0 LIMIT 1);
SET @contract_a2 = (SELECT id FROM contract WHERE code = 'UAT-CON-V54-A2' AND deleted = 0 LIMIT 1);
SET @contract_b1 = (SELECT id FROM contract WHERE code = 'UAT-CON-V54-B1' AND deleted = 0 LIMIT 1);
INSERT INTO contract_room (contract_id, room_id, created_by, updated_by, deleted, remark)
SELECT @contract_a1, @room_101, 0, 0, 0, 'V54验收'
WHERE NOT EXISTS (SELECT 1 FROM contract_room WHERE contract_id = @contract_a1 AND room_id = @room_101 AND deleted = 0);
INSERT INTO contract_room (contract_id, room_id, created_by, updated_by, deleted, remark)
SELECT @contract_a1, @room_102, 0, 0, 0, 'V54验收'
WHERE NOT EXISTS (SELECT 1 FROM contract_room WHERE contract_id = @contract_a1 AND room_id = @room_102 AND deleted = 0);
INSERT INTO contract_room (contract_id, room_id, created_by, updated_by, deleted, remark)
SELECT @contract_a2, @room_103, 0, 0, 0, 'V54历史验收'
WHERE NOT EXISTS (SELECT 1 FROM contract_room WHERE contract_id = @contract_a2 AND room_id = @room_103 AND deleted = 0);
INSERT INTO contract_room (contract_id, room_id, created_by, updated_by, deleted, remark)
SELECT @contract_b1, @room_104, 0, 0, 0, 'V54跨租户验收'
WHERE NOT EXISTS (SELECT 1 FROM contract_room WHERE contract_id = @contract_b1 AND room_id = @room_104 AND deleted = 0);

INSERT INTO parking_space (code, space_no, area, location, space_type, monthly_rate, rent_status, status, created_by, updated_by, remark)
VALUES
    ('UAT-PS-V54-01', 'UAT-P01', 'A区', 'A区地下一层', 'STANDARD', 500.00, 'RENTED', 'ENABLED', 0, 0, 'V54当前车位'),
    ('UAT-PS-V54-02', 'UAT-P02', 'B区', 'B区地下一层', 'CHARGING', 650.00, 'VACANT', 'ENABLED', 0, 0, 'V54历史车位')
ON DUPLICATE KEY UPDATE
    space_no = VALUES(space_no), area = VALUES(area), location = VALUES(location),
    space_type = VALUES(space_type), monthly_rate = VALUES(monthly_rate), status = 'ENABLED', deleted = 0;
SET @parking_1 = (SELECT id FROM parking_space WHERE code = 'UAT-PS-V54-01' AND deleted = 0 LIMIT 1);
SET @parking_2 = (SELECT id FROM parking_space WHERE code = 'UAT-PS-V54-02' AND deleted = 0 LIMIT 1);
INSERT INTO parking_assignment (
    code, parking_space_id, tenant_id, tenant_contact_id, car_plate_no, vehicle_type,
    start_date, end_date, status, monthly_fee, unit_price_snapshot, created_by, updated_by, remark
) VALUES
    ('UAT-PA-V54-01', @parking_1, @tenant_a, @contact_a_e1, '沪AU5401', 'SMALL', DATE_SUB(CURRENT_DATE, INTERVAL 2 MONTH), DATE_ADD(CURRENT_DATE, INTERVAL 10 MONTH), 'ACTIVE', 500.00, 500.00, 0, 0, 'V54当前车位绑定'),
    ('UAT-PA-V54-02', @parking_2, @tenant_a, @contact_a_e2, '沪AU5402', 'NEW_ENERGY', DATE_SUB(CURRENT_DATE, INTERVAL 18 MONTH), DATE_SUB(CURRENT_DATE, INTERVAL 6 MONTH), 'EXPIRED', 650.00, 650.00, 0, 0, 'V54历史车位绑定')
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id), tenant_contact_id = VALUES(tenant_contact_id),
    car_plate_no = VALUES(car_plate_no), start_date = VALUES(start_date), end_date = VALUES(end_date),
    status = VALUES(status), deleted = 0, updated_by = 0, remark = VALUES(remark);

INSERT INTO bill (
    code, tenant_id, contract_id, source_type, source_id, bill_type, title,
    amount, calculated_total, adjustment_total, final_amount, paid_amount,
    period_start, period_end, billing_period, bill_date, due_date,
    audit_status, workflow_status, pay_status, invoice_status,
    status, created_by, updated_by, remark
) VALUES
    ('UAT-BILL-V54-UNPAID', @tenant_a, @contract_a1, 'CONTRACT', @contract_a1, 'RENT', 'V54租金待缴账单', 20000.00, 20000.00, 0.00, 20000.00, 0.00, DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), LAST_DAY(CURRENT_DATE), DATE_FORMAT(CURRENT_DATE, '%Y-%m'), CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 10 DAY), 'APPROVED', 'APPROVED', 'UNPAID', 'NOT_INVOICED', 'ENABLED', 0, 0, 'V54明确关联合同'),
    ('UAT-BILL-V54-PAID', @tenant_a, NULL, 'MANUAL', NULL, 'PROPERTY', 'V54历史已缴账单', 9800.00, 10000.00, -200.00, 9800.00, 9800.00, DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 2 MONTH), LAST_DAY(DATE_SUB(CURRENT_DATE, INTERVAL 2 MONTH)), DATE_FORMAT(DATE_SUB(CURRENT_DATE, INTERVAL 2 MONTH), '%Y-%m'), DATE_SUB(CURRENT_DATE, INTERVAL 2 MONTH), DATE_SUB(CURRENT_DATE, INTERVAL 50 DAY), 'APPROVED', 'APPROVED', 'PAID', 'ISSUED', 'ENABLED', 0, 0, 'V54未明确关联合同历史账单'),
    ('UAT-BILL-V54-OVERDUE', @tenant_a, @contract_a1, 'CONTRACT', @contract_a1, 'PROPERTY', 'V54逾期账单', 1200.00, 1200.00, 0.00, 1200.00, 0.00, DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 1 MONTH), LAST_DAY(DATE_SUB(CURRENT_DATE, INTERVAL 1 MONTH)), DATE_FORMAT(DATE_SUB(CURRENT_DATE, INTERVAL 1 MONTH), '%Y-%m'), DATE_SUB(CURRENT_DATE, INTERVAL 1 MONTH), DATE_SUB(CURRENT_DATE, INTERVAL 5 DAY), 'APPROVED', 'APPROVED', 'OVERDUE', 'PENDING', 'ENABLED', 0, 0, 'V54逾期状态'),
    ('UAT-BILL-V54-B1', @tenant_b, @contract_b1, 'CONTRACT', @contract_b1, 'RENT', 'V54乙公司跨租户账单', 6000.00, 6000.00, 0.00, 6000.00, 0.00, DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), LAST_DAY(CURRENT_DATE), DATE_FORMAT(CURRENT_DATE, '%Y-%m'), CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 10 DAY), 'APPROVED', 'APPROVED', 'UNPAID', 'NOT_INVOICED', 'ENABLED', 0, 0, 'V54跨租户账单')
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id), contract_id = VALUES(contract_id),
    calculated_total = VALUES(calculated_total), adjustment_total = VALUES(adjustment_total),
    final_amount = VALUES(final_amount), paid_amount = VALUES(paid_amount),
    workflow_status = VALUES(workflow_status), pay_status = VALUES(pay_status),
    invoice_status = VALUES(invoice_status), status = 'ENABLED', deleted = 0, updated_by = 0;
SET @bill_unpaid = (SELECT id FROM bill WHERE code = 'UAT-BILL-V54-UNPAID' AND deleted = 0 LIMIT 1);
SET @bill_paid = (SELECT id FROM bill WHERE code = 'UAT-BILL-V54-PAID' AND deleted = 0 LIMIT 1);
INSERT INTO bill_line_item (
    bill_id, charge_item_type, source_type, source_id, source_snapshot, description,
    quantity_snapshot, unit, unit_price_snapshot, calculated_amount, adjustment_amount,
    final_amount, sort_order, status, created_by, updated_by
)
SELECT @bill_unpaid, 'RENT', 'CONTRACT', @contract_a1,
       JSON_OBJECT('contractId', @contract_a1, 'roomIds', JSON_ARRAY(@room_101, @room_102)),
       'V54合同租金', 1, '项', 20000.00, 20000.00, 0.00, 20000.00, 1, 'ACTIVE', 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM bill_line_item WHERE bill_id = @bill_unpaid AND description = 'V54合同租金' AND deleted = 0
);
INSERT INTO bill_invoice (
    code, bill_id, tenant_id, invoice_title, invoice_type, tax_no, invoice_date,
    amount, tax_amount, version_no, invoice_status, issued_at, status, created_by, updated_by, remark
) VALUES (
    'UAT-INV-V54-01', @bill_paid, @tenant_a, 'V54租户档案验收甲公司',
    'ELECTRONIC', '91310000UATV540001', DATE_SUB(CURRENT_DATE, INTERVAL 45 DAY),
    9800.00, 0.00, 1, 'ISSUED', DATE_SUB(NOW(), INTERVAL 45 DAY),
    'ENABLED', 0, 0, 'V54历史发票快照'
)
ON DUPLICATE KEY UPDATE
    invoice_title = VALUES(invoice_title), tax_no = VALUES(tax_no),
    invoice_status = 'ISSUED', status = 'ENABLED', deleted = 0, updated_by = 0;

INSERT INTO mobile_user (
    code, nickname, real_name, phone, phone_verified_at, user_type, status,
    last_login_at, created_by, updated_by, remark
) VALUES
    ('UAT-MU-V54-A-R', '甲负责人', '甲公司负责人', '13900005401', NOW(), 'TENANT', 'ENABLED', NOW(), 0, 0, 'V54负责人移动身份'),
    ('UAT-MU-V54-A-E1', '甲员工一', '甲公司员工一', '13900005402', NOW(), 'TENANT', 'ENABLED', NOW(), 0, 0, 'V54员工移动身份'),
    ('UAT-MU-V54-A-E2', '甲紧急人', '甲公司紧急联系人', '13900005403', NOW(), 'TENANT', 'ENABLED', NOW(), 0, 0, 'V54手机号变更重验身份'),
    ('UAT-MU-V54-B-R', '乙负责人', '乙公司负责人', '13900005411', NOW(), 'TENANT', 'ENABLED', NOW(), 0, 0, 'V54跨租户移动身份')
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id), phone = VALUES(phone), phone_verified_at = NOW(),
    user_type = 'TENANT', status = 'ENABLED', deleted = 0, updated_by = 0, remark = VALUES(remark);
SET @mobile_a_r = (SELECT id FROM mobile_user WHERE code = 'UAT-MU-V54-A-R' AND deleted = 0 LIMIT 1);
SET @mobile_a_e1 = (SELECT id FROM mobile_user WHERE code = 'UAT-MU-V54-A-E1' AND deleted = 0 LIMIT 1);
SET @mobile_a_e2 = (SELECT id FROM mobile_user WHERE code = 'UAT-MU-V54-A-E2' AND deleted = 0 LIMIT 1);
SET @mobile_b_r = (SELECT id FROM mobile_user WHERE code = 'UAT-MU-V54-B-R' AND deleted = 0 LIMIT 1);
INSERT INTO mobile_user_bind (mobile_user_id, bind_type, tenant_id, tenant_contact_id, status, created_by, updated_by, deleted, remark)
SELECT @mobile_a_r, 'TENANT', @tenant_a, @contact_a_r, 'ENABLED', 0, 0, 0, 'V54负责人绑定'
WHERE NOT EXISTS (SELECT 1 FROM mobile_user_bind WHERE mobile_user_id = @mobile_a_r AND bind_type = 'TENANT' AND tenant_contact_id = @contact_a_r AND deleted = 0);
INSERT INTO mobile_user_bind (mobile_user_id, bind_type, tenant_id, tenant_contact_id, status, created_by, updated_by, deleted, remark)
SELECT @mobile_a_e1, 'TENANT', @tenant_a, @contact_a_e1, 'ENABLED', 0, 0, 0, 'V54员工绑定'
WHERE NOT EXISTS (SELECT 1 FROM mobile_user_bind WHERE mobile_user_id = @mobile_a_e1 AND bind_type = 'TENANT' AND tenant_contact_id = @contact_a_e1 AND deleted = 0);
INSERT INTO mobile_user_bind (mobile_user_id, bind_type, tenant_id, tenant_contact_id, status, created_by, updated_by, deleted, remark)
SELECT @mobile_a_e2, 'TENANT', @tenant_a, @contact_a_e2, 'ENABLED', 0, 0, 0, 'V54重验绑定'
WHERE NOT EXISTS (SELECT 1 FROM mobile_user_bind WHERE mobile_user_id = @mobile_a_e2 AND bind_type = 'TENANT' AND tenant_contact_id = @contact_a_e2 AND deleted = 0);
INSERT INTO mobile_user_bind (mobile_user_id, bind_type, tenant_id, tenant_contact_id, status, created_by, updated_by, deleted, remark)
SELECT @mobile_b_r, 'TENANT', @tenant_b, @contact_b_r, 'ENABLED', 0, 0, 0, 'V54跨租户绑定'
WHERE NOT EXISTS (SELECT 1 FROM mobile_user_bind WHERE mobile_user_id = @mobile_b_r AND bind_type = 'TENANT' AND tenant_contact_id = @contact_b_r AND deleted = 0);
UPDATE mobile_user
SET current_bind_id = (
    SELECT id FROM mobile_user_bind
    WHERE mobile_user_id = mobile_user.id AND status = 'ENABLED' AND deleted = 0
    ORDER BY id DESC LIMIT 1
)
WHERE id IN (@mobile_a_r, @mobile_a_e1, @mobile_a_e2, @mobile_b_r);

SELECT @tenant_a AS tenant_a_id, @tenant_b AS tenant_b_id,
       @contract_a1 AS contract_a1_id, @contract_b1 AS contract_b1_id,
       @contact_a_e2 AS phone_change_contact_id,
       @mobile_a_r AS responsible_mobile_user_id,
       @mobile_a_e1 AS employee_mobile_user_id,
       @mobile_b_r AS other_tenant_mobile_user_id;
