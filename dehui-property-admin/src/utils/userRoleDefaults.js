export const USER_TYPE_DEFAULT_ROLE_CODES = {
  ADMIN: 'ADMIN',
  MANAGER: 'PROPERTY_MANAGER',
  FINANCE: 'FINANCE',
  ENGINEER: 'ENGINEER',
  SECURITY: 'SECURITY',
  TENANT: 'TENANT',
  STAFF: 'STAFF',
  CLEANER: 'STAFF'
}

export function roleCodeOf(role) {
  return role?.roleCode || role?.code || ''
}

export function defaultRoleCodeForUserType(userType) {
  return USER_TYPE_DEFAULT_ROLE_CODES[userType] || ''
}

export function defaultRoleIdsForUserType(userType, roles = []) {
  const roleCode = defaultRoleCodeForUserType(userType)
  if (!roleCode) return []

  const role = roles.find(item => roleCodeOf(item) === roleCode && item.status !== 'DISABLED')
  return role?.id ? [role.id] : []
}
