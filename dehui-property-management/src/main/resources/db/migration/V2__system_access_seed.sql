INSERT INTO sys_user (code, username, password_hash, real_name, phone, email, status, created_by, updated_by, remark)
VALUES ('USR-ADMIN', 'admin', '$2a$10$XqhZR.pObyI//tr.gLRq7.Joa1SjTQRO0USIJcDEo4UtUarcndRBW', '系统管理员', NULL, NULL, 'ENABLED', 0, 0, '初始化管理员，默认密码 123456')
ON DUPLICATE KEY UPDATE
  real_name = VALUES(real_name),
  status = 'ENABLED',
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

INSERT INTO sys_role (code, name, role_type, status, created_by, updated_by, remark)
VALUES
  ('ADMIN', '系统管理员', 'SYSTEM', 'ENABLED', 0, 0, '拥有平台全部菜单和操作权限'),
  ('PROPERTY_MANAGER', '物业运营', 'BUSINESS', 'ENABLED', 0, 0, '负责资产、租户、工单、公告等日常运营'),
  ('FINANCE', '财务人员', 'BUSINESS', 'ENABLED', 0, 0, '负责账单、支付和财务看板'),
  ('ENGINEER', '工程维修', 'BUSINESS', 'ENABLED', 0, 0, '负责工单处理、巡检和能耗抄表')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  role_type = VALUES(role_type),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

INSERT INTO sys_menu (code, parent_id, name, path, component, icon, sort_order, status, created_by, updated_by, remark)
VALUES
  ('dashboard', NULL, '驾驶舱', '/dashboard', NULL, 'dashboard', 10, 'ENABLED', 0, 0, '首页驾驶舱'),
  ('asset', NULL, '资产管理', NULL, NULL, 'building', 20, 'ENABLED', 0, 0, '楼宇房源资产'),
  ('lease', NULL, '租赁管理', NULL, NULL, 'contract', 30, 'ENABLED', 0, 0, '招商、合同、租户'),
  ('operation', NULL, '运营管理', NULL, NULL, 'tool', 40, 'ENABLED', 0, 0, '工单、巡检、访客、公告'),
  ('parking', NULL, '停车管理', NULL, NULL, 'parking', 50, 'ENABLED', 0, 0, '车位和停车账单'),
  ('meeting', NULL, '会议经营', NULL, NULL, 'meeting', 60, 'ENABLED', 0, 0, '会议室经营'),
  ('energy', NULL, '能耗管理', NULL, NULL, 'energy', 70, 'ENABLED', 0, 0, '抄表和统计'),
  ('finance', NULL, '财务管理', NULL, NULL, 'finance', 80, 'ENABLED', 0, 0, '账单和财务看板'),
  ('ai', NULL, 'AI分析', NULL, NULL, 'ai', 90, 'ENABLED', 0, 0, 'AI 分析'),
  ('system', NULL, '系统管理', NULL, NULL, 'settings', 100, 'ENABLED', 0, 0, '用户和角色')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  name = VALUES(name),
  path = VALUES(path),
  component = VALUES(component),
  icon = VALUES(icon),
  sort_order = VALUES(sort_order),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

SET @menu_asset = (SELECT id FROM sys_menu WHERE code = 'asset' AND deleted = 0 LIMIT 1);
SET @menu_lease = (SELECT id FROM sys_menu WHERE code = 'lease' AND deleted = 0 LIMIT 1);
SET @menu_operation = (SELECT id FROM sys_menu WHERE code = 'operation' AND deleted = 0 LIMIT 1);
SET @menu_parking = (SELECT id FROM sys_menu WHERE code = 'parking' AND deleted = 0 LIMIT 1);
SET @menu_meeting = (SELECT id FROM sys_menu WHERE code = 'meeting' AND deleted = 0 LIMIT 1);
SET @menu_energy = (SELECT id FROM sys_menu WHERE code = 'energy' AND deleted = 0 LIMIT 1);
SET @menu_finance = (SELECT id FROM sys_menu WHERE code = 'finance' AND deleted = 0 LIMIT 1);
SET @menu_ai = (SELECT id FROM sys_menu WHERE code = 'ai' AND deleted = 0 LIMIT 1);
SET @menu_system = (SELECT id FROM sys_menu WHERE code = 'system' AND deleted = 0 LIMIT 1);

INSERT INTO sys_menu (code, parent_id, name, path, component, icon, sort_order, status, created_by, updated_by, remark)
VALUES
  ('asset:building', @menu_asset, '楼宇管理', '/buildings', NULL, NULL, 21, 'ENABLED', 0, 0, '楼宇台账'),
  ('asset:floor', @menu_asset, '楼层管理', '/floors', NULL, NULL, 22, 'ENABLED', 0, 0, '楼层台账'),
  ('asset:room', @menu_asset, '房间管理', '/rooms', NULL, NULL, 23, 'ENABLED', 0, 0, '房间房源'),
  ('asset:equipment', @menu_asset, '设备台账', '/equipment', NULL, NULL, 24, 'ENABLED', 0, 0, '设备资产'),
  ('lease:investment-content', @menu_lease, '招商内容', '/investment/contents', NULL, NULL, 31, 'ENABLED', 0, 0, '招商展示内容'),
  ('lease:investment-lead', @menu_lease, '招商线索', '/investment/leads', NULL, NULL, 32, 'ENABLED', 0, 0, '招商线索'),
  ('lease:contract', @menu_lease, '合同台账', '/contracts', NULL, NULL, 33, 'ENABLED', 0, 0, '租赁合同'),
  ('lease:tenant', @menu_lease, '租户管理', '/tenants', NULL, NULL, 34, 'ENABLED', 0, 0, '租户台账'),
  ('lease:checkin', @menu_lease, '租户入驻', '/leases', NULL, NULL, 35, 'ENABLED', 0, 0, '租户入驻'),
  ('operation:workorder', @menu_operation, '工单管理', '/workorders', NULL, NULL, 41, 'ENABLED', 0, 0, '报修工单'),
  ('operation:inspection', @menu_operation, '巡检管理', '/inspections', NULL, NULL, 42, 'ENABLED', 0, 0, '巡检记录'),
  ('operation:visitor', @menu_operation, '访客管理', '/visitors', NULL, NULL, 43, 'ENABLED', 0, 0, '访客预约'),
  ('operation:announcement', @menu_operation, '公告管理', '/announcements', NULL, NULL, 44, 'ENABLED', 0, 0, '公告发布'),
  ('parking:space', @menu_parking, '车位管理', '/parking/spaces', NULL, NULL, 51, 'ENABLED', 0, 0, '停车位'),
  ('parking:bill', @menu_parking, '停车账单', '/parking/bills', NULL, NULL, 52, 'ENABLED', 0, 0, '停车账单'),
  ('meeting:room', @menu_meeting, '会议室管理', '/meetings/rooms', NULL, NULL, 61, 'ENABLED', 0, 0, '会议室'),
  ('meeting:booking', @menu_meeting, '会议预约', '/meetings/bookings', NULL, NULL, 62, 'ENABLED', 0, 0, '会议预约'),
  ('energy:record', @menu_energy, '抄表管理', '/energy/records', NULL, NULL, 71, 'ENABLED', 0, 0, '抄表记录'),
  ('energy:stats', @menu_energy, '能耗统计', '/energy/stats', NULL, NULL, 72, 'ENABLED', 0, 0, '能耗统计'),
  ('finance:bill', @menu_finance, '账单管理', '/bills', NULL, NULL, 81, 'ENABLED', 0, 0, '业务账单'),
  ('finance:dashboard', @menu_finance, '财务看板', '/finance/dashboard', NULL, NULL, 82, 'ENABLED', 0, 0, '财务看板'),
  ('ai:daily-report', @menu_ai, '运营日报', '/ai/daily-report', NULL, NULL, 91, 'ENABLED', 0, 0, 'AI 运营日报'),
  ('system:user', @menu_system, '用户管理', '/system/users', NULL, NULL, 101, 'ENABLED', 0, 0, '后台用户管理'),
  ('system:role', @menu_system, '角色管理', '/system/roles', NULL, NULL, 102, 'ENABLED', 0, 0, '角色权限管理')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  name = VALUES(name),
  path = VALUES(path),
  component = VALUES(component),
  icon = VALUES(icon),
  sort_order = VALUES(sort_order),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
  ('system:*', '系统管理全部权限', 'system', '*', 'ENABLED', 0, 0, '兼容早期系统管理权限'),
  ('system:user:view', '查看用户', 'system', 'user:view', 'ENABLED', 0, 0, '查看后台用户'),
  ('system:user:create', '新增用户', 'system', 'user:create', 'ENABLED', 0, 0, '新增后台用户'),
  ('system:user:update', '编辑用户', 'system', 'user:update', 'ENABLED', 0, 0, '编辑后台用户'),
  ('system:user:disable', '禁用用户', 'system', 'user:disable', 'ENABLED', 0, 0, '启停后台用户'),
  ('system:user:delete', '删除用户', 'system', 'user:delete', 'ENABLED', 0, 0, '删除后台用户'),
  ('system:user:assign-role', '分配用户角色', 'system', 'user:assign-role', 'ENABLED', 0, 0, '维护用户角色'),
  ('system:user:reset-password', '重置用户密码', 'system', 'user:reset-password', 'ENABLED', 0, 0, '重置后台用户密码'),
  ('system:role:view', '查看角色', 'system', 'role:view', 'ENABLED', 0, 0, '查看角色'),
  ('system:role:create', '新增角色', 'system', 'role:create', 'ENABLED', 0, 0, '新增角色'),
  ('system:role:update', '编辑角色', 'system', 'role:update', 'ENABLED', 0, 0, '编辑角色'),
  ('system:role:disable', '禁用角色', 'system', 'role:disable', 'ENABLED', 0, 0, '启停角色'),
  ('system:role:delete', '删除角色', 'system', 'role:delete', 'ENABLED', 0, 0, '删除角色'),
  ('system:role:assign-permission', '分配角色权限', 'system', 'role:assign-permission', 'ENABLED', 0, 0, '维护角色菜单和权限'),
  ('system:menu:view', '查看菜单', 'system', 'menu:view', 'ENABLED', 0, 0, '查看菜单'),
  ('system:permission:view', '查看权限', 'system', 'permission:view', 'ENABLED', 0, 0, '查看权限'),
  ('asset:view', '查看资产', 'asset', 'view', 'ENABLED', 0, 0, '查看资产管理'),
  ('asset:create', '新增资产', 'asset', 'create', 'ENABLED', 0, 0, '新增资产'),
  ('asset:update', '编辑资产', 'asset', 'update', 'ENABLED', 0, 0, '编辑资产'),
  ('asset:delete', '删除资产', 'asset', 'delete', 'ENABLED', 0, 0, '删除资产'),
  ('building:*', '楼宇房源全部权限', 'building', '*', 'ENABLED', 0, 0, '兼容早期楼宇权限'),
  ('building:view', '查看楼宇房源', 'building', 'view', 'ENABLED', 0, 0, '查看楼宇楼层房间'),
  ('building:create', '新增楼宇房源', 'building', 'create', 'ENABLED', 0, 0, '新增楼宇楼层房间'),
  ('building:update', '编辑楼宇房源', 'building', 'update', 'ENABLED', 0, 0, '编辑楼宇楼层房间'),
  ('building:delete', '删除楼宇房源', 'building', 'delete', 'ENABLED', 0, 0, '删除楼宇楼层房间'),
  ('tenant:*', '租户管理全部权限', 'tenant', '*', 'ENABLED', 0, 0, '兼容早期租户权限'),
  ('tenant:view', '查看租户', 'tenant', 'view', 'ENABLED', 0, 0, '查看租户'),
  ('tenant:create', '新增租户', 'tenant', 'create', 'ENABLED', 0, 0, '新增租户'),
  ('tenant:update', '编辑租户', 'tenant', 'update', 'ENABLED', 0, 0, '编辑租户'),
  ('tenant:delete', '删除租户', 'tenant', 'delete', 'ENABLED', 0, 0, '删除租户'),
  ('tenant:portal:view', '查看租户门户', 'tenant', 'portal:view', 'ENABLED', 0, 0, '查看租户端信息'),
  ('contract:view', '查看合同', 'contract', 'view', 'ENABLED', 0, 0, '查看合同'),
  ('contract:create', '新增合同', 'contract', 'create', 'ENABLED', 0, 0, '新增合同'),
  ('contract:update', '编辑合同', 'contract', 'update', 'ENABLED', 0, 0, '编辑合同'),
  ('contract:delete', '删除合同', 'contract', 'delete', 'ENABLED', 0, 0, '删除合同'),
  ('bill:*', '账单管理全部权限', 'bill', '*', 'ENABLED', 0, 0, '兼容早期账单权限'),
  ('bill:view', '查看账单', 'bill', 'view', 'ENABLED', 0, 0, '查看账单'),
  ('bill:add', '新增账单', 'bill', 'add', 'ENABLED', 0, 0, '新增账单'),
  ('bill:audit', '审核账单', 'bill', 'audit', 'ENABLED', 0, 0, '审核账单'),
  ('bill:pay', '确认收款', 'bill', 'pay', 'ENABLED', 0, 0, '确认账单收款'),
  ('finance:bill:view', '查看财务账单', 'finance', 'bill:view', 'ENABLED', 0, 0, '查看财务账单'),
  ('finance:bill:create', '财务新增账单', 'finance', 'bill:create', 'ENABLED', 0, 0, '财务新增账单'),
  ('finance:bill:audit', '财务审核账单', 'finance', 'bill:audit', 'ENABLED', 0, 0, '财务审核账单'),
  ('finance:bill:pay', '财务确认收款', 'finance', 'bill:pay', 'ENABLED', 0, 0, '财务确认收款'),
  ('finance:bill:export', '导出财务账单', 'finance', 'bill:export', 'ENABLED', 0, 0, '导出财务账单'),
  ('finance:invoice:upload', '上传发票', 'finance', 'invoice:upload', 'ENABLED', 0, 0, '上传发票附件'),
  ('meeting:view', '查看会议', 'meeting', 'view', 'ENABLED', 0, 0, '查看会议室和预约'),
  ('meeting:create', '新增会议预约', 'meeting', 'create', 'ENABLED', 0, 0, '新增会议预约'),
  ('meeting:update', '编辑会议预约', 'meeting', 'update', 'ENABLED', 0, 0, '编辑会议预约'),
  ('meeting:delete', '删除会议预约', 'meeting', 'delete', 'ENABLED', 0, 0, '删除会议预约'),
  ('parking:view', '查看车位', 'parking', 'view', 'ENABLED', 0, 0, '查看车位'),
  ('parking:create', '新增车位', 'parking', 'create', 'ENABLED', 0, 0, '新增车位'),
  ('parking:update', '编辑车位', 'parking', 'update', 'ENABLED', 0, 0, '编辑车位'),
  ('parking:delete', '删除车位', 'parking', 'delete', 'ENABLED', 0, 0, '删除车位'),
  ('parking-bill:view', '查看停车账单', 'parking-bill', 'view', 'ENABLED', 0, 0, '查看停车账单'),
  ('parking-bill:add', '新增停车账单', 'parking-bill', 'add', 'ENABLED', 0, 0, '新增停车账单'),
  ('parking-bill:pay', '停车账单收款', 'parking-bill', 'pay', 'ENABLED', 0, 0, '停车账单收款'),
  ('workorder:*', '工单管理全部权限', 'workorder', '*', 'ENABLED', 0, 0, '兼容早期工单权限'),
  ('workorder:view', '查看工单', 'workorder', 'view', 'ENABLED', 0, 0, '查看工单'),
  ('workorder:create', '新增工单', 'workorder', 'create', 'ENABLED', 0, 0, '新增工单'),
  ('workorder:assign', '分配工单', 'workorder', 'assign', 'ENABLED', 0, 0, '分配工单'),
  ('workorder:complete', '完成工单', 'workorder', 'complete', 'ENABLED', 0, 0, '完成工单'),
  ('workorder:close', '关闭工单', 'workorder', 'close', 'ENABLED', 0, 0, '关闭工单'),
  ('inspection:view', '查看巡检', 'inspection', 'view', 'ENABLED', 0, 0, '查看巡检'),
  ('visitor:view', '查看访客', 'visitor', 'view', 'ENABLED', 0, 0, '查看访客'),
  ('visitor:create', '新增访客预约', 'visitor', 'create', 'ENABLED', 0, 0, '新增访客预约'),
  ('announcement:view', '查看公告', 'announcement', 'view', 'ENABLED', 0, 0, '查看公告'),
  ('announcement:create', '新增公告', 'announcement', 'create', 'ENABLED', 0, 0, '新增公告'),
  ('announcement:update', '编辑公告', 'announcement', 'update', 'ENABLED', 0, 0, '编辑公告'),
  ('announcement:delete', '删除公告', 'announcement', 'delete', 'ENABLED', 0, 0, '删除公告'),
  ('investment:view', '查看招商', 'investment', 'view', 'ENABLED', 0, 0, '查看招商内容和线索'),
  ('investment:create', '新增招商', 'investment', 'create', 'ENABLED', 0, 0, '新增招商内容和线索'),
  ('investment:update', '编辑招商', 'investment', 'update', 'ENABLED', 0, 0, '编辑招商内容和线索'),
  ('investment:delete', '删除招商', 'investment', 'delete', 'ENABLED', 0, 0, '删除招商内容和线索'),
  ('investment:follow', '跟进招商线索', 'investment', 'follow', 'ENABLED', 0, 0, '跟进招商线索'),
  ('energy:view', '查看能耗', 'energy', 'view', 'ENABLED', 0, 0, '查看能耗'),
  ('energy:create', '新增能耗记录', 'energy', 'create', 'ENABLED', 0, 0, '新增能耗记录'),
  ('feerule:view', '查看收费规则', 'feerule', 'view', 'ENABLED', 0, 0, '查看收费规则'),
  ('feerule:add', '新增收费规则', 'feerule', 'add', 'ENABLED', 0, 0, '新增收费规则'),
  ('feerule:generate', '生成收费账单', 'feerule', 'generate', 'ENABLED', 0, 0, '生成收费账单'),
  ('ai:view', '查看AI分析', 'ai', 'view', 'ENABLED', 0, 0, '查看 AI 分析'),
  ('mobile:*', '小程序端全部权限', 'mobile', '*', 'ENABLED', 0, 0, '兼容早期小程序端权限'),
  ('mobile:view', '查看小程序端', 'mobile', 'view', 'ENABLED', 0, 0, '查看小程序端聚合信息')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  module = VALUES(module),
  action = VALUES(action),
  status = VALUES(status),
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

SET @admin_user_id = (SELECT id FROM sys_user WHERE username = 'admin' AND deleted = 0 LIMIT 1);
SET @admin_role_id = (SELECT id FROM sys_role WHERE code = 'ADMIN' AND deleted = 0 LIMIT 1);

INSERT INTO sys_user_role (user_id, role_id, status, created_by, updated_by)
SELECT @admin_user_id, @admin_role_id, 'ENABLED', 0, 0
WHERE @admin_user_id IS NOT NULL AND @admin_role_id IS NOT NULL
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT @admin_role_id, id, 'ENABLED', 0, 0
FROM sys_menu
WHERE deleted = 0 AND @admin_role_id IS NOT NULL
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT @admin_role_id, id, 'ENABLED', 0, 0
FROM sys_permission
WHERE deleted = 0 AND @admin_role_id IS NOT NULL
ON DUPLICATE KEY UPDATE
  deleted = 0,
  status = 'ENABLED',
  updated_by = 0;
