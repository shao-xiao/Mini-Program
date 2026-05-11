export const menuSections = [
  {
    title: '驾驶舱',
    path: '/dashboard',
    roles: ['ADMIN', 'MANAGER', 'STAFF', 'SECURITY', 'CLEANER', 'FINANCE']
  },
  {
    title: '资产管理',
    index: 'asset',
    roles: ['ADMIN', 'MANAGER'],
    children: [
      { title: '楼宇管理', path: '/buildings' },
      { title: '楼层管理', path: '/floors' },
      { title: '房间管理', path: '/rooms' },
      { title: '设备台账', path: '/equipment' }
    ]
  },
  {
    title: '租赁管理',
    index: 'tenant',
    roles: ['ADMIN', 'MANAGER'],
    children: [
      { title: '招商内容', path: '/investment/contents' },
      { title: '招商线索', path: '/investment/leads' },
      { title: '合同台账', path: '/contracts' },
      { title: '租户管理', path: '/tenants' },
      { title: '租户入驻', path: '/leases' }
    ]
  },
  {
    title: '运营管理',
    index: 'ops',
    roles: ['ADMIN', 'MANAGER', 'STAFF', 'SECURITY', 'CLEANER'],
    children: [
      { title: '工单管理', path: '/workorders', roles: ['ADMIN', 'MANAGER', 'STAFF', 'CLEANER'] },
      { title: '巡检管理', path: '/inspections' },
      { title: '访客管理', path: '/visitors', roles: ['ADMIN', 'MANAGER', 'SECURITY'] },
      { title: '公告管理', path: '/announcements' }
    ]
  },
  {
    title: '停车管理',
    index: 'parking',
    roles: ['ADMIN', 'MANAGER', 'FINANCE'],
    children: [
      { title: '车位管理', path: '/parking/spaces' },
      { title: '停车账单', path: '/parking/bills' }
    ]
  },
  {
    title: '会议经营',
    index: 'meeting',
    roles: ['ADMIN', 'MANAGER', 'STAFF', 'FINANCE'],
    children: [
      { title: '会议室管理', path: '/meetings/rooms', roles: ['ADMIN', 'MANAGER', 'FINANCE'] },
      { title: '会议预约', path: '/meetings/bookings' }
    ]
  },
  {
    title: '能耗管理',
    index: 'energy',
    roles: ['ADMIN', 'MANAGER'],
    children: [
      { title: '抄表管理', path: '/energy/records' },
      { title: '能耗统计', path: '/energy/stats' }
    ]
  },
  {
    title: '财务管理',
    index: 'finance',
    roles: ['ADMIN', 'FINANCE', 'MANAGER'],
    children: [
      { title: '账单管理', path: '/bills' },
      { title: '财务看板', path: '/finance/dashboard' }
    ]
  },
  {
    title: 'AI分析',
    index: 'ai',
    roles: ['ADMIN', 'MANAGER'],
    children: [
      { title: '运营日报', path: '/ai/daily-report' }
    ]
  },
  {
    title: '系统管理',
    index: 'system',
    roles: ['ADMIN'],
    children: [
      { title: '用户管理', path: '/system/users' },
      { title: '角色管理', path: '/system/roles' }
    ]
  }
]

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

export function hasAnyRole(currentRoles, allowedRoles = []) {
  if (currentRoles.includes('ADMIN')) {
    return true
  }

  return allowedRoles.some(role => currentRoles.includes(role))
}

export function canAccessMenuItem(item, currentRoles) {
  const roles = item.roles || []
  return roles.length === 0 || hasAnyRole(currentRoles, roles)
}

export function getVisibleMenuSections(currentRoles = getCurrentRoles()) {
  return menuSections
    .filter(item => canAccessMenuItem(item, currentRoles))
    .map(item => {
      if (!Array.isArray(item.children)) {
        return item
      }

      return {
        ...item,
        children: item.children.filter(child => {
          return canAccessMenuItem({
            ...child,
            roles: child.roles || item.roles
          }, currentRoles)
        })
      }
    })
    .filter(item => !Array.isArray(item.children) || item.children.length > 0)
}

export function getRouteAccessMap() {
  const map = {}

  menuSections.forEach(section => {
    if (section.path) {
      map[section.path] = section.roles || []
    }

    if (Array.isArray(section.children)) {
      section.children.forEach(child => {
        map[child.path] = child.roles || section.roles || []
      })
    }
  })

  return map
}

export function canAccessPath(currentRoles, path) {
  if (!Array.isArray(currentRoles) || currentRoles.length === 0) {
    return path === '/dashboard'
  }

  const routeAccessMap = getRouteAccessMap()
  const matchedPath = Object.keys(routeAccessMap)
    .sort((a, b) => b.length - a.length)
    .find(item => path === item || path.startsWith(item + '/'))

  if (!matchedPath) {
    return false
  }

  return hasAnyRole(currentRoles, routeAccessMap[matchedPath])
}
