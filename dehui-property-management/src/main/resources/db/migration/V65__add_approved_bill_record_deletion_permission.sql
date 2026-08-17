-- Approved bill deletion is intentionally a separate, permission-only cleanup capability.
-- Do not grant it to any role during upgrade; administrators must opt in explicitly.
INSERT INTO sys_permission (code, name, module, action, status, created_by, updated_by, remark)
SELECT 'bill:delete-approved', '删除已审核账单记录', 'bill', 'delete-approved',
       'ENABLED', 0, 0, '仅用于清理无收款、无发票、无来源关联的已审核手工测试账单'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE code = 'bill:delete-approved'
);
