-- Consolidate tenants, contracts and occupancy operations into one tenant workspace.
-- Historical fee rules, billing plans and all permission codes remain unchanged.

SET @menu_lease = (
    SELECT id FROM sys_menu
    WHERE code = 'lease' AND deleted = 0
    LIMIT 1
);

UPDATE sys_menu
SET name = '租户管理',
    path = NULL,
    status = 'ENABLED',
    updated_by = 0,
    remark = '租户档案、合同和入住退租统一管理'
WHERE code = 'lease' AND deleted = 0;

INSERT INTO sys_menu (
    code, parent_id, name, path, component, icon, sort_order,
    status, created_by, updated_by, remark
)
VALUES (
    'lease:tenant-workspace', @menu_lease, '租户档案', '/tenant/workspace', NULL, NULL, 311,
    'ENABLED', 0, 0, '租户、合同、联系人、租赁房间和关联账单统一工作台'
)
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

SET @menu_tenant_workspace = (
    SELECT id FROM sys_menu
    WHERE code = 'lease:tenant-workspace' AND deleted = 0
    LIMIT 1
);

-- A role receives the unified workspace only if it had tenant, contract or occupancy navigation.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_tenant_workspace, 'ENABLED', 0, 0
FROM sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id AND legacy_menu.deleted = 0
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND legacy_menu.code IN ('lease:tenant', 'lease:contract', 'lease:checkin')
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_lease, 'ENABLED', 0, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND rm.menu_id = @menu_tenant_workspace
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

-- Preserve legacy menu rows for audit and old-link redirects, but remove active entry points.
UPDATE sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id
SET rm.status = 'DISABLED', rm.updated_by = 0
WHERE rm.deleted = 0
  AND legacy_menu.deleted = 0
  AND legacy_menu.code IN (
      'lease:tenant',
      'lease:contract',
      'lease:checkin',
      'lease:fee-rule-v2',
      'lease:billing-plan'
  );

UPDATE sys_menu
SET status = 'DISABLED',
    updated_by = 0,
    remark = CONCAT(COALESCE(remark, ''), CASE WHEN remark IS NULL OR remark = '' THEN '' ELSE ' | ' END, 'V42后由租户档案或财务账单承接')
WHERE deleted = 0
  AND code IN (
      'lease:tenant',
      'lease:contract',
      'lease:checkin',
      'lease:fee-rule-v2',
      'lease:billing-plan'
  );
