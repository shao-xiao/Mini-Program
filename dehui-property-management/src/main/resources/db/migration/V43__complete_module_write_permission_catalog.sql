-- Register concrete write permissions already enforced by controllers and AuthInterceptor.
-- Do not grant them to any role here; role access remains an explicit administrator decision.
INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
VALUES
  ('inspection:delete', '删除巡检记录', 'inspection', 'delete', 'ENABLED', 0, 0, '删除巡检记录'),
  ('bill:update', '编辑账单', 'bill', 'update', 'ENABLED', 0, 0, '编辑或撤回账单'),
  ('bill:delete', '删除账单', 'bill', 'delete', 'ENABLED', 0, 0, '删除符合业务状态约束的账单')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  module = VALUES(module),
  action = VALUES(action),
  status = 'ENABLED',
  deleted = 0,
  updated_by = 0,
  remark = VALUES(remark);
