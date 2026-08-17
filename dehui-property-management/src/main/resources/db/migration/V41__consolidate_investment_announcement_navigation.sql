-- Consolidate investment content, listings, leads and announcements under one navigation module.
-- Existing permission codes and business records remain unchanged.

INSERT INTO sys_menu (
    code, parent_id, name, path, component, icon, sort_order,
    status, created_by, updated_by, remark
)
VALUES (
    'investment-announcement', NULL, '招商公告', NULL, NULL, NULL, 25,
    'ENABLED', 0, 0, '招商展示、招商房源、招商线索和公告统一入口'
)
ON DUPLICATE KEY UPDATE
    parent_id = NULL,
    name = VALUES(name),
    path = NULL,
    sort_order = VALUES(sort_order),
    status = 'ENABLED',
    deleted = 0,
    updated_by = 0,
    remark = VALUES(remark);

SET @menu_investment_announcement = (
    SELECT id FROM sys_menu
    WHERE code = 'investment-announcement' AND deleted = 0
    LIMIT 1
);

INSERT INTO sys_menu (
    code, parent_id, name, path, component, icon, sort_order,
    status, created_by, updated_by, remark
)
VALUES
    ('investment-announcement:manage', @menu_investment_announcement, '招商管理', '/investment/manage', NULL, NULL, 251, 'ENABLED', 0, 0, '招商展示和招商房源统一管理'),
    ('investment-announcement:leads', @menu_investment_announcement, '招商线索', '/investment/leads', NULL, NULL, 252, 'ENABLED', 0, 0, '招商线索跟进管理'),
    ('investment-announcement:announcements', @menu_investment_announcement, '公告管理', '/investment/announcements', NULL, NULL, 253, 'ENABLED', 0, 0, '公告发布和首页轮播管理')
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

SET @menu_investment_manage = (
    SELECT id FROM sys_menu
    WHERE code = 'investment-announcement:manage' AND deleted = 0
    LIMIT 1
);
SET @menu_investment_leads = (
    SELECT id FROM sys_menu
    WHERE code = 'investment-announcement:leads' AND deleted = 0
    LIMIT 1
);
SET @menu_investment_announcements = (
    SELECT id FROM sys_menu
    WHERE code = 'investment-announcement:announcements' AND deleted = 0
    LIMIT 1
);

-- A role receives each consolidated child only if it had a corresponding legacy child.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_investment_manage, 'ENABLED', 0, 0
FROM sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id AND legacy_menu.deleted = 0
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND legacy_menu.code IN ('lease:investment-content', 'lease:investment-listing')
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_investment_leads, 'ENABLED', 0, 0
FROM sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id AND legacy_menu.deleted = 0
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND legacy_menu.code = 'lease:investment-lead'
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_investment_announcements, 'ENABLED', 0, 0
FROM sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id AND legacy_menu.deleted = 0
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND legacy_menu.code = 'operation:announcement'
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

-- Every role with a consolidated child also receives the new parent node.
INSERT INTO sys_role_menu (role_id, menu_id, status, created_by, updated_by)
SELECT DISTINCT rm.role_id, @menu_investment_announcement, 'ENABLED', 0, 0
FROM sys_role_menu rm
WHERE rm.deleted = 0
  AND rm.status = 'ENABLED'
  AND rm.menu_id IN (@menu_investment_manage, @menu_investment_leads, @menu_investment_announcements)
ON DUPLICATE KEY UPDATE status = 'ENABLED', deleted = 0, updated_by = 0;

-- Preserve legacy rows for audit and route compatibility, but remove old navigation entries.
UPDATE sys_role_menu rm
JOIN sys_menu legacy_menu ON legacy_menu.id = rm.menu_id
SET rm.status = 'DISABLED', rm.updated_by = 0
WHERE rm.deleted = 0
  AND legacy_menu.deleted = 0
  AND legacy_menu.code IN (
      'lease:investment-content',
      'lease:investment-listing',
      'lease:investment-lead',
      'operation:announcement'
  );
UPDATE sys_menu
SET status = 'DISABLED',
    updated_by = 0,
    remark = CONCAT(COALESCE(remark, ''), CASE WHEN remark IS NULL OR remark = '' THEN '' ELSE ' | ' END, 'V41后由招商公告统一入口承接')
WHERE deleted = 0
  AND code IN (
      'lease:investment-content',
      'lease:investment-listing',
      'lease:investment-lead',
      'operation:announcement'
  );
