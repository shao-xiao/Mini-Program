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
  return roles.includes('SUPER_ADMIN') || roleCodes.some(role => roles.includes(role))
}

function matchesPermission(permissions = [], code = '') {
  if (!code) return false
  if (permissions.includes('*') || permissions.includes(code)) return true

  const parts = String(code).split(':')
  for (let index = parts.length - 1; index > 0; index -= 1) {
    const wildcard = `${parts.slice(0, index).join(':')}:*`
    if (permissions.includes(wildcard)) return true
  }

  return false
}

export function hasPermission(code) {
  const roles = getCurrentRoles()
  if (roles.includes('SUPER_ADMIN')) return true
  return matchesPermission(getCurrentPermissions(), code)
}

export function hasAnyPermission(codes = []) {
  const roles = getCurrentRoles()
  if (roles.includes('SUPER_ADMIN')) return true
  const permissions = getCurrentPermissions()
  return codes.some(code => matchesPermission(permissions, code))
}
