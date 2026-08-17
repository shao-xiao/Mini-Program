-- Repair previously disabled core administrator roles. Runtime write guards
-- prevent these roles from being disabled or deleted again.
UPDATE sys_role
SET role_type = 'SYSTEM',
    status = 'ENABLED',
    updated_by = 0
WHERE code IN ('ADMIN', 'SUPER_ADMIN')
  AND deleted = 0;
