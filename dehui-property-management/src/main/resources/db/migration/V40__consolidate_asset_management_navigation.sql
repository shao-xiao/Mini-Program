-- Consolidate space, equipment, energy, parking and meeting assets under one menu.
-- Permission codes and historical business data remain unchanged.

SET @menu_asset = (
    SELECT id FROM sys_menu
    WHERE code = 'asset' AND deleted = 0
    LIMIT 1
);

UPDATE sys_menu
SET name = '设备台账',
    path = '/assets/equipment',
    parent_id = @menu_asset,
    sort_order = 22,
    status = 'ENABLED',
    updated_by = 0,
    remark = '资产管理统一入口：设备台账'
WHERE code = 'asset:equipment' AND deleted = 0;

INSERT INTO sys_menu (
    code, parent_id, name, path, component, icon, sort_order,
    status, created_by, updated_by, remark
)
VALUES
    ('asset:spaces', @menu_asset, '楼宇空间管理', '/assets/spaces', NULL, NULL, 21, 'ENABLED', 0, 0, '楼宇、楼层和房间统一管理'),
    ('asset:energy', @menu_asset, '能耗管理', '/assets/energy', NULL, NULL, 23, 'ENABLED', 0, 0, '表具、抄表和用量统计统一管理'),
    ('asset:parking', @menu_asset, '停车位管理', '/assets/parking', NULL, NULL, 24, 'ENABLED', 0, 0, '车位和租户车辆绑定统一管理'),
    ('asset:meetings', @menu_asset, '会议管理', '/assets/meetings', NULL, NULL, 25, 'ENABLED', 0, 0, '会议室和预约统一管理')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    name = VALUES(name),
    path = VALUES(path),
    component = VALUES(component),
    icon = VALUES(icon),
    sort_order = VALUES(sort_order),
    status = 'ENABLED',
    deleted = 0,
    updated_by = 0,
    remark = VALUES(remark);

SET @menu_spaces = (SELECT id FROM sys_menu WHERE code = 'asset:spaces' AND deleted = 0 LIMIT 1);
SET @menu_energy_unified = (SELECT id FROM sys_menu WHERE code = 'asset:energy' AND deleted = 0 LIMIT 1);
SET @menu_parking_unified = (SELECT id FROM sys_menu WHERE code = 'asset:parking' AND deleted = 0 LIMIT 1);
SET @menu_meetings_unified = (SELECT id FROM sys_menu WHERE code = 'asset:meetings' AND deleted = 0 LIMIT 1);

-- A role receives a consolidated child only when it had at least one matching legacy child.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_spaces, 'ENABLED', 0, 0
FROM sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id AND legacy_menu.deleted = 0
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND legacy_menu.code IN ('asset:building', 'asset:floor', 'asset:room')
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_energy_unified, 'ENABLED', 0, 0
FROM sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id AND legacy_menu.deleted = 0
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND legacy_menu.code IN ('energy:meter', 'energy:record', 'energy:stats')
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_parking_unified, 'ENABLED', 0, 0
FROM sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id AND legacy_menu.deleted = 0
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND legacy_menu.code = 'parking:space'
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_meetings_unified, 'ENABLED', 0, 0
FROM sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id AND legacy_menu.deleted = 0
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND legacy_menu.code IN ('meeting:room', 'meeting:booking')
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

-- Ensure each role with a consolidated child can receive the asset root in the menu tree.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_asset, 'ENABLED', 0, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND rm.menu_id IN (
      @menu_spaces,
      (SELECT id FROM sys_menu WHERE code = 'asset:equipment' AND deleted = 0 LIMIT 1),
      @menu_energy_unified,
      @menu_parking_unified,
      @menu_meetings_unified
  )
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

-- Keep historical menu rows for audit and old-link compatibility, but remove them from active navigation.
UPDATE sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id
SET rm.status = 'DISABLED', rm.updated_by = 0
WHERE rm.deleted = 0
  AND legacy_menu.deleted = 0
  AND legacy_menu.code IN (
      'asset:building', 'asset:floor', 'asset:room',
      'parking', 'parking:space', 'parking:bill',
      'meeting', 'meeting:room', 'meeting:booking',
      'energy', 'energy:meter', 'energy:record', 'energy:stats'
  );

UPDATE sys_menu
SET status = 'DISABLED', updated_by = 0,
    remark = CONCAT(COALESCE(remark, ''), CASE WHEN remark IS NULL OR remark = '' THEN '' ELSE ' | ' END, 'V40后由资产管理统一入口承接')
WHERE deleted = 0
  AND code IN (
      'asset:building', 'asset:floor', 'asset:room',
      'parking', 'parking:space', 'parking:bill',
      'meeting', 'meeting:room', 'meeting:booking',
      'energy', 'energy:meter', 'energy:record', 'energy:stats'
  );

UPDATE sys_menu
SET name = '资产管理',
    status = 'ENABLED',
    updated_by = 0,
    remark = '楼宇空间、设备、能耗、停车位和会议资产统一管理'
WHERE code = 'asset' AND deleted = 0;
