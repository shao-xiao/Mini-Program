-- Normalize role display names and descriptions without changing role_code.
-- Keep role_id bindings and access rights unchanged; only semantic text labels are updated.

UPDATE sys_role
SET name = '超级管理员',
    remark = '拥有全部菜单和全部权限'
WHERE code = 'SUPER_ADMIN'
  AND deleted = 0;

UPDATE sys_role
SET name = '管理员',
    remark = '后台管理员权限'
WHERE code = 'ADMIN'
  AND deleted = 0;

UPDATE sys_role
SET name = '物业运营',
    remark = '物业运营'
WHERE code = 'PROPERTY_MANAGER'
  AND deleted = 0;

UPDATE sys_role
SET name = '财务主管',
    remark = '财务主管'
WHERE code = 'FINANCE'
  AND deleted = 0;

UPDATE sys_role
SET name = '工程部员工',
    remark = '工程部员工'
WHERE code = 'ENGINEER'
  AND deleted = 0;

UPDATE sys_role
SET name = '安保员工',
    remark = '安保员工'
WHERE code = 'SECURITY'
  AND deleted = 0;

UPDATE sys_role
SET name = '物业招商经理',
    remark = '物业招商经理'
WHERE code = 'INVESTMENT_MANAGER'
  AND deleted = 0;

UPDATE sys_role
SET name = '会计',
    remark = '会计'
WHERE code = 'ACCOUNTANT'
  AND deleted = 0;

UPDATE sys_role
SET name = '出纳',
    remark = '出纳'
WHERE code = 'CASHIER'
  AND deleted = 0;

UPDATE sys_role
SET name = '工程部负责人',
    remark = '工程部负责人'
WHERE code = 'ENGINEER_MANAGER'
  AND deleted = 0;

UPDATE sys_role
SET name = '安保经理',
    remark = '安保经理'
WHERE code = 'SECURITY_MANAGER'
  AND deleted = 0;
