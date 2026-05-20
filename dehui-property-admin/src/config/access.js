import { getCurrentRoles, hasAnyRole } from '../utils/permission'

export const fallbackMenuSections = [
  { title: '驾驶舱', menuName: '驾驶舱', path: '/dashboard' },
  {
    title: '资产管理',
    menuName: '资产管理',
    menuCode: 'asset',
    children: [
      { title: '楼宇管理', menuName: '楼宇管理', path: '/buildings' },
      { title: '楼层管理', menuName: '楼层管理', path: '/floors' },
      { title: '房间管理', menuName: '房间管理', path: '/rooms' },
      { title: '设备台账', menuName: '设备台账', path: '/equipment' }
    ]
  },
  {
    title: '租赁管理',
    menuName: '租赁管理',
    menuCode: 'lease',
    children: [
      { title: '招商内容', menuName: '招商内容', path: '/investment/contents' },
      { title: '招商线索', menuName: '招商线索', path: '/investment/leads' },
      { title: '合同台账', menuName: '合同台账', path: '/contracts' },
      { title: '租户管理', menuName: '租户管理', path: '/tenants' },
      { title: '租户入驻', menuName: '租户入驻', path: '/leases' }
    ]
  },
  {
    title: '运营管理',
    menuName: '运营管理',
    menuCode: 'operation',
    children: [
      { title: '工单管理', menuName: '工单管理', path: '/workorders' },
      { title: '巡检管理', menuName: '巡检管理', path: '/inspections' },
      { title: '访客管理', menuName: '访客管理', path: '/visitors' },
      { title: '公告管理', menuName: '公告管理', path: '/announcements' }
    ]
  },
  {
    title: '停车管理',
    menuName: '停车管理',
    menuCode: 'parking',
    children: [
      { title: '车位管理', menuName: '车位管理', path: '/parking/spaces' },
      { title: '停车账单', menuName: '停车账单', path: '/parking/bills' }
    ]
  },
  {
    title: '会议经营',
    menuName: '会议经营',
    menuCode: 'meeting',
    children: [
      { title: '会议室管理', menuName: '会议室管理', path: '/meetings/rooms' },
      { title: '会议预约', menuName: '会议预约', path: '/meetings/bookings' }
    ]
  },
  {
    title: '能耗管理',
    menuName: '能耗管理',
    menuCode: 'energy',
    children: [
      { title: '抄表管理', menuName: '抄表管理', path: '/energy/records' },
      { title: '能耗统计', menuName: '能耗统计', path: '/energy/stats' }
    ]
  },
  {
    title: '财务管理',
    menuName: '财务管理',
    menuCode: 'finance',
    children: [
      { title: '账单管理', menuName: '账单管理', path: '/bills' },
      { title: '财务看板', menuName: '财务看板', path: '/finance/dashboard' }
    ]
  },
  {
    title: 'AI分析',
    menuName: 'AI分析',
    menuCode: 'ai',
    children: [{ title: '运营日报', menuName: '运营日报', path: '/ai/daily-report' }]
  },
  {
    title: '系统管理',
    menuName: '系统管理',
    menuCode: 'system',
    children: [
      { title: '用户管理', menuName: '用户管理', path: '/system/users' },
      { title: '角色管理', menuName: '角色管理', path: '/system/roles' }
    ]
  }
]

export function getStoredMenus() {
  try {
    const menus = JSON.parse(localStorage.getItem('menus') || '[]')
    if (Array.isArray(menus) && menus.length > 0) {
      return menus
    }
  } catch (error) {
    console.warn('menus 解析失败：', error)
  }
  return fallbackMenuSections
}

export function normalizeMenu(item) {
  return {
    ...item,
    title: item.title || item.menuName,
    index: item.menuCode || item.path || String(item.id),
    children: Array.isArray(item.children) ? item.children.map(normalizeMenu) : []
  }
}

export function getVisibleMenuSections() {
  return getStoredMenus().map(normalizeMenu)
}

function collectPaths(menus, result = []) {
  menus.forEach(item => {
    if (item.path) result.push(item.path)
    if (Array.isArray(item.children)) collectPaths(item.children, result)
  })
  return result
}

export function canAccessPath(currentRoles, path) {
  if (!localStorage.getItem('token')) return false
  if (hasAnyRole(['SUPER_ADMIN', 'ADMIN'])) return true
  const paths = collectPaths(getVisibleMenuSections())
  return paths.some(item => path === item || path.startsWith(item + '/'))
}

export { getCurrentRoles }
