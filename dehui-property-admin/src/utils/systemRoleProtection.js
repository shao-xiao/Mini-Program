const CORE_ADMIN_ROLE_CODES = new Set(['ADMIN', 'SUPER_ADMIN'])

function normalized(value) {
  return String(value || '').trim().toUpperCase()
}

export function isProtectedSystemRole(role) {
  const roleCode = normalized(role?.roleCode || role?.code)
  const roleType = normalized(role?.roleType || role?.role_type)
  return CORE_ADMIN_ROLE_CODES.has(roleCode) || roleType === 'SYSTEM'
}
