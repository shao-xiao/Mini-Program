-- Separate property mobile-workbench authorization from desktop business RBAC.
-- The entry permission determines whether an internal user is a property-mobile
-- identity. Child permissions determine the readable/writable workbench modules.

INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
  ('staff-mobile:access', '启用物业移动工作站', 'staff-mobile', 'access', 'ENABLED', 0, 0, '角色勾选后，该角色用户可被识别为物业移动员工'),
  ('staff-mobile:workorder:view', '查看移动工单', 'staff-mobile', 'workorder:view', 'ENABLED', 0, 0, '在物业移动工作站查看本人待处理工单'),
  ('staff-mobile:workorder:write', '处理移动工单', 'staff-mobile', 'workorder:write', 'ENABLED', 0, 0, '在物业移动工作站开始、反馈并完成工单'),
  ('staff-mobile:inspection:view', '查看移动巡检', 'staff-mobile', 'inspection:view', 'ENABLED', 0, 0, '在物业移动工作站查看巡检记录'),
  ('staff-mobile:inspection:write', '处理移动巡检', 'staff-mobile', 'inspection:write', 'ENABLED', 0, 0, '在物业移动工作站新增和更新巡检'),
  ('staff-mobile:energy:view', '查看移动能耗', 'staff-mobile', 'energy:view', 'ENABLED', 0, 0, '在物业移动工作站查看能耗和表具'),
  ('staff-mobile:energy:write', '移动抄表', 'staff-mobile', 'energy:write', 'ENABLED', 0, 0, '在物业移动工作站提交抄表读数'),
  ('staff-mobile:meeting:view', '查看移动会议', 'staff-mobile', 'meeting:view', 'ENABLED', 0, 0, '在物业移动工作站查看会议预约'),
  ('staff-mobile:meeting:write', '处理移动会议', 'staff-mobile', 'meeting:write', 'ENABLED', 0, 0, '在物业移动工作站确认、取消和完成会议预约'),
  ('staff-mobile:visitor:view', '查看移动访客', 'staff-mobile', 'visitor:view', 'ENABLED', 0, 0, '在物业移动工作站查看访客预约'),
  ('staff-mobile:visitor:write', '核验移动访客', 'staff-mobile', 'visitor:write', 'ENABLED', 0, 0, '在物业移动工作站核验访客到访和离场'),
  ('staff-mobile:investment:view', '查看移动招商看房', 'staff-mobile', 'investment:view', 'ENABLED', 0, 0, '在物业移动工作站查看招商看房线索'),
  ('staff-mobile:investment:write', '跟进移动招商看房', 'staff-mobile', 'investment:write', 'ENABLED', 0, 0, '在物业移动工作站跟进和转化招商看房线索')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  module = VALUES(module),
  action = VALUES(action),
  status = 'ENABLED',
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);

-- Preserve existing mobile staff behavior by translating the old desktop grants
-- once. After this migration, future identity and capability checks use only the
-- new staff-mobile permission domain.
INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT source.role_id, target.id, 'ENABLED', 0, 0
FROM (
  SELECT role_permission.role_id
  FROM sys_role_permission role_permission
  INNER JOIN sys_permission old_permission
    ON old_permission.id = role_permission.permission_id
   AND old_permission.deleted = 0
   AND old_permission.status IN ('ACTIVE', 'ENABLED')
  WHERE role_permission.deleted = 0
    AND role_permission.status IN ('ACTIVE', 'ENABLED')
    AND (
      old_permission.code = '*'
      OR old_permission.code LIKE 'workorder:%'
      OR old_permission.code LIKE 'work-order:%'
      OR old_permission.code LIKE 'inspection:%'
      OR old_permission.code LIKE 'energy:%'
      OR old_permission.code LIKE 'meeting:%'
      OR old_permission.code LIKE 'meeting-room:%'
      OR old_permission.code LIKE 'meeting-booking:%'
      OR old_permission.code LIKE 'visitor:%'
      OR old_permission.code LIKE 'investment-lead:%'
    )
) source
INNER JOIN sys_permission target
  ON target.code = 'staff-mobile:access'
 AND target.deleted = 0
 AND target.status = 'ENABLED'
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:workorder:view' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND (old_permission.code = '*' OR old_permission.code LIKE 'workorder:%' OR old_permission.code LIKE 'work-order:%')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:workorder:write' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND old_permission.code IN ('*', 'workorder:*', 'work-order:*', 'workorder:update', 'work-order:update',
                              'workorder:complete', 'work-order:complete', 'workorder:create',
                              'workorder:assign', 'workorder:close')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:inspection:view' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND (old_permission.code = '*' OR old_permission.code LIKE 'inspection:%')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:inspection:write' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND old_permission.code IN ('*', 'inspection:*', 'inspection:create', 'inspection:update', 'inspection:delete')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:energy:view' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND (old_permission.code = '*' OR old_permission.code LIKE 'energy:%')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:energy:write' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND old_permission.code IN ('*', 'energy:*', 'energy:create', 'energy:update', 'energy:reading:update',
                              'energy:meter:create', 'energy:meter:update', 'energy:meter:delete')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:meeting:view' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND (old_permission.code = '*' OR old_permission.code LIKE 'meeting:%'
       OR old_permission.code LIKE 'meeting-room:%' OR old_permission.code LIKE 'meeting-booking:%')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:meeting:write' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND old_permission.code IN ('*', 'meeting:*', 'meeting-booking:*', 'meeting-booking:update',
                              'meeting-booking:confirm', 'meeting-booking:cancel', 'meeting-booking:complete')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:visitor:view' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND (old_permission.code = '*' OR old_permission.code LIKE 'visitor:%')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:visitor:write' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND old_permission.code IN ('*', 'visitor:*', 'visitor:create', 'visitor:update')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:investment:view' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND (old_permission.code = '*' OR old_permission.code LIKE 'investment-lead:%')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT DISTINCT role_permission.role_id, target.id, 'ENABLED', 0, 0
FROM sys_role_permission role_permission
INNER JOIN sys_permission old_permission ON old_permission.id = role_permission.permission_id
INNER JOIN sys_permission target ON target.code = 'staff-mobile:investment:write' AND target.deleted = 0
WHERE role_permission.deleted = 0 AND role_permission.status IN ('ACTIVE', 'ENABLED')
  AND old_permission.deleted = 0
  AND old_permission.code IN ('*', 'investment-lead:*', 'investment-lead:follow',
                              'investment-lead:convert', 'investment-lead:delete')
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;

-- SUPER_ADMIN keeps the complete catalog even on installations whose wildcard
-- grant is expanded into concrete permissions by the role editor.
INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by)
SELECT role_record.id, permission_record.id, 'ENABLED', 0, 0
FROM sys_role role_record
INNER JOIN sys_permission permission_record
  ON permission_record.module = 'staff-mobile'
 AND permission_record.deleted = 0
 AND permission_record.status = 'ENABLED'
WHERE role_record.code = 'SUPER_ADMIN'
  AND role_record.deleted = 0
  AND role_record.status = 'ENABLED'
ON DUPLICATE KEY UPDATE deleted = 0, status = 'ENABLED', updated_by = 0;
