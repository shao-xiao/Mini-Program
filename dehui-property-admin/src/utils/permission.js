function parseStorageArray(key) {
  try {
    const value = JSON.parse(localStorage.getItem(key) || '[]')
    return Array.isArray(value) ? value : []
  } catch (error) {
    return []
  }
}

export function getCurrentRoles() {
  const roles = parseStorageArray('roles')
  if (roles.length > 0) return roles
  const legacyRole = localStorage.getItem('role')
  return legacyRole ? [legacyRole] : []
}

export function getCurrentPermissions() {
  return parseStorageArray('permissions')
}

export function hasRole(roleCode) {
  return getCurrentRoles().includes(roleCode)
}

export function hasAnyRole(roleCodes = []) {
  const roles = getCurrentRoles()
  return roles.includes('SUPER_ADMIN') || roles.includes('ADMIN') || roleCodes.some(role => roles.includes(role))
}

export function hasPermission(code) {
  const roles = getCurrentRoles()
  if (roles.includes('SUPER_ADMIN') || roles.includes('ADMIN')) return true
  return getCurrentPermissions().includes(code)
}

export function hasAnyPermission(codes = []) {
  const roles = getCurrentRoles()
  if (roles.includes('SUPER_ADMIN') || roles.includes('ADMIN')) return true
  const permissions = getCurrentPermissions()
  return codes.some(code => permissions.includes(code))
}
