export function getCurrentRoles() {
  try {
    const roles = JSON.parse(localStorage.getItem('roles') || '[]')
    if (Array.isArray(roles) && roles.length > 0) {
      return roles
    }
  } catch (error) {
    console.warn('roles 解析失败：', error)
  }

  const legacyRole = localStorage.getItem('role')
  return legacyRole ? [legacyRole] : []
}

export function hasRole(allowedRoles = []) {
  const roles = getCurrentRoles()

  if (!Array.isArray(allowedRoles) || allowedRoles.length === 0) {
    return false
  }

  return roles.some(role => allowedRoles.includes(role))
}

export function hasPermission(permissionCode) {
  const roles = getCurrentRoles()

  if (roles.includes('ADMIN')) {
    return true
  }

  const permissionMap = {
    MANAGER: [
      'view',
      'add',
      'edit',

      'building:view',
      'building:add',
      'building:edit',
      'building:delete',

      'floor:view',
      'floor:add',
      'floor:edit',
      'floor:delete',

      'room:view',
      'room:add',
      'room:edit',
      'room:delete',

      'tenant:view',
      'tenant:add',
      'tenant:edit',
      'tenant:delete',

      'lease:view',
      'lease:add',
      'lease:edit',
      'lease:checkout',

      'contract:view',
      'contract:add',
      'contract:edit',
      'contract:terminate',

      'workorder:view',
      'workorder:add',
      'workorder:edit',

      'inspection:view',
      'inspection:add',
      'inspection:edit',

      'visitor:view',
      'visitor:add',
      'visitor:edit',

      'announcement:view',
      'announcement:add',
      'announcement:edit',

      'energy:view',
      'energy:add',
      'energy:stats',

      'parking:view',
      'parking:add',
      'parking:edit'
    ],

    FINANCE: [
      'bill:view',
      'bill:add',
      'bill:pay',
      'feerule:view',
      'feerule:add',
      'finance:view',
      'parking-bill:view',
      'parking-bill:add',
      'parking-bill:pay'
    ],

    STAFF: [
      'workorder:view',
      'workorder:add',
      'inspection:view',
      'announcement:view'
    ],

    SECURITY: [
      'visitor:view',
      'visitor:add',
      'inspection:view',
      'inspection:add',
      'announcement:view'
    ],

    CLEANER: [
      'workorder:view',
      'workorder:edit',
      'inspection:view',
      'announcement:view'
    ]
  }

  return roles.some(role => {
    const permissions = permissionMap[role] || []
    return permissions.includes(permissionCode)
  })
}
