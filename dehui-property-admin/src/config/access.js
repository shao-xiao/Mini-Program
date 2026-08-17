import { getCurrentRoles, hasAnyRole, hasAnyPermission } from '../utils/permission.js'
import { getPathViewPermissionCodes } from './permissionRules.js'
import {
  canReadModule,
  getModulePermissionBundleForPath,
  modulePermissionMenuSections
} from './modulePermissionBundles.js'

export const fallbackMenuSections = modulePermissionMenuSections

const restrictedPathRoles = {}

const legacyRouteAliases = new Map([
  ['/buildings', '/assets/spaces'],
  ['/floors', '/assets/spaces'],
  ['/rooms', '/assets/spaces'],
  ['/equipment', '/assets/equipment'],
  ['/energy/meters', '/assets/energy'],
  ['/energy/records', '/assets/energy'],
  ['/energy/stats', '/assets/energy'],
  ['/energy/statistics', '/assets/energy'],
  ['/parking/spaces', '/assets/parking'],
  ['/parking/bills', '/bills'],
  ['/meetings/rooms', '/assets/meetings'],
  ['/meetings/bookings', '/assets/meetings'],
  ['/investment/contents', '/investment/manage'],
  ['/investment/listings', '/investment/manage'],
  ['/announcements', '/investment/announcements'],
  ['/tenants', '/tenant/workspace'],
  ['/contracts', '/tenant/workspace'],
  ['/leases', '/tenant/workspace'],
  ['/fee-rules', '/bills'],
  ['/billing-plans', '/bills']
])

export function canonicalAccessPath(path) {
  return legacyRouteAliases.get(path) || path
}

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

function menuIdentity(item) {
  return item?.path || item?.menuCode || item?.index || item?.menuName || item?.title || ''
}

function menuMatches(left, right) {
  if (!left || !right) return false
  if (left.path && right.path) return left.path === right.path
  if (left.menuCode && right.menuCode) return left.menuCode === right.menuCode
  if (left.index && right.index) return left.index === right.index
  return menuIdentity(left) === menuIdentity(right)
}

function mergeChildrenByFallbackOrder(storedChildren = [], fallbackChildren = []) {
  const result = [...storedChildren]

  fallbackChildren.forEach((fallbackChild, fallbackIndex) => {
    if (result.some(item => menuMatches(item, fallbackChild))) return

    const previousFallbackSiblings = fallbackChildren.slice(0, fallbackIndex)
    let insertAfterIndex = -1

    previousFallbackSiblings.forEach(previous => {
      const existingIndex = result.findIndex(item => menuMatches(item, previous))
      if (existingIndex > insertAfterIndex) insertAfterIndex = existingIndex
    })

    result.splice(insertAfterIndex + 1, 0, fallbackChild)
  })

  return result
}

function mergeMenusWithFallback(storedMenus = []) {
  const result = [...storedMenus]

  fallbackMenuSections.forEach((fallbackSection, fallbackIndex) => {
    const existingIndex = result.findIndex(item => menuMatches(item, fallbackSection))

    if (existingIndex >= 0) {
      const existing = result[existingIndex]
      result[existingIndex] = {
        ...existing,
        children: mergeChildrenByFallbackOrder(existing.children || [], fallbackSection.children || [])
      }
      return
    }

    const previousFallbackSections = fallbackMenuSections.slice(0, fallbackIndex)
    let insertAfterIndex = -1

    previousFallbackSections.forEach(previous => {
      const existingIndex = result.findIndex(item => menuMatches(item, previous))
      if (existingIndex > insertAfterIndex) insertAfterIndex = existingIndex
    })

    result.splice(insertAfterIndex + 1, 0, fallbackSection)
  })

  return result
}

function isSuperAdminRole(currentRoles = []) {
  return currentRoles.includes('SUPER_ADMIN')
}

const unifiedAssetMenus = [
  {
    title: '楼宇空间管理',
    menuName: '楼宇空间管理',
    menuCode: 'asset:spaces',
    path: '/assets/spaces',
    legacyPaths: ['/buildings', '/floors', '/rooms']
  },
  {
    title: '设备台账',
    menuName: '设备台账',
    menuCode: 'asset:equipment',
    path: '/assets/equipment',
    legacyPaths: ['/equipment']
  },
  {
    title: '能耗管理',
    menuName: '能耗管理',
    menuCode: 'asset:energy',
    path: '/assets/energy',
    legacyPaths: ['/energy/meters', '/energy/records', '/energy/stats', '/energy/statistics']
  },
  {
    title: '停车位管理',
    menuName: '停车位管理',
    menuCode: 'asset:parking',
    path: '/assets/parking',
    legacyPaths: ['/parking/spaces']
  },
  {
    title: '会议管理',
    menuName: '会议管理',
    menuCode: 'asset:meetings',
    path: '/assets/meetings',
    legacyPaths: ['/meetings/rooms', '/meetings/bookings']
  }
]

function flattenMenus(items = [], result = []) {
  items.forEach(item => {
    result.push(item)
    if (Array.isArray(item.children)) flattenMenus(item.children, result)
  })
  return result
}

export function consolidateAssetMenus(items = []) {
  const flatMenus = flattenMenus(items)
  const assetChildren = unifiedAssetMenus.flatMap(definition => {
    const source = flatMenus.find(item => item.path === definition.path)
      || flatMenus.find(item => definition.legacyPaths.includes(item.path))
    if (!source) return []
    return [{
      ...source,
      title: definition.title,
      menuName: definition.menuName,
      menuCode: definition.menuCode,
      path: definition.path,
      children: []
    }]
  })

  const obsoleteSectionCodes = new Set(['parking', 'meeting', 'energy'])
  const withoutObsoleteSections = items.filter(item => !obsoleteSectionCodes.has(item.menuCode))
  const assetIndex = withoutObsoleteSections.findIndex(item => item.menuCode === 'asset' || item.title === '资产管理' || item.menuName === '资产管理')

  if (!assetChildren.length) {
    return withoutObsoleteSections.filter((item, index) => index !== assetIndex)
  }

  const assetSection = assetIndex >= 0 ? withoutObsoleteSections[assetIndex] : {
    title: '资产管理',
    menuName: '资产管理',
    menuCode: 'asset'
  }
  const normalizedAssetSection = {
    ...assetSection,
    title: '资产管理',
    menuName: '资产管理',
    menuCode: 'asset',
    path: undefined,
    children: assetChildren
  }

  if (assetIndex >= 0) {
    return withoutObsoleteSections.map((item, index) => index === assetIndex ? normalizedAssetSection : item)
  }

  const dashboardIndex = withoutObsoleteSections.findIndex(item => item.path === '/dashboard')
  const result = [...withoutObsoleteSections]
  result.splice(dashboardIndex >= 0 ? dashboardIndex + 1 : 0, 0, normalizedAssetSection)
  return result
}

const unifiedInvestmentAnnouncementMenus = [
  {
    title: '招商管理',
    menuName: '招商管理',
    menuCode: 'investment-announcement:manage',
    path: '/investment/manage',
    legacyPaths: ['/investment/contents', '/investment/listings']
  },
  {
    title: '招商线索',
    menuName: '招商线索',
    menuCode: 'investment-announcement:leads',
    path: '/investment/leads',
    legacyPaths: ['/investment/leads']
  },
  {
    title: '公告管理',
    menuName: '公告管理',
    menuCode: 'investment-announcement:announcements',
    path: '/investment/announcements',
    legacyPaths: ['/announcements']
  }
]

export function consolidateInvestmentAnnouncementMenus(items = []) {
  const flatMenus = flattenMenus(items)
  const targetPaths = new Set(unifiedInvestmentAnnouncementMenus.map(item => item.path))
  const legacyPaths = new Set(unifiedInvestmentAnnouncementMenus.flatMap(item => item.legacyPaths))
  const children = unifiedInvestmentAnnouncementMenus.flatMap(definition => {
    const source = flatMenus.find(item => item.path === definition.path)
      || flatMenus.find(item => definition.legacyPaths.includes(item.path))
    if (!source) return []
    return [{
      ...source,
      title: definition.title,
      menuName: definition.menuName,
      menuCode: definition.menuCode,
      path: definition.path,
      children: []
    }]
  })

  const rootIndex = items.findIndex(item => item.menuCode === 'investment-announcement'
    || item.title === '招商公告'
    || item.menuName === '招商公告')
  const cleaned = items
    .filter((_, index) => index !== rootIndex)
    .map(section => ({
      ...section,
      children: Array.isArray(section.children)
        ? section.children.filter(child => !legacyPaths.has(child.path) && !targetPaths.has(child.path))
        : section.children
    }))

  if (!children.length) return cleaned

  const existingRoot = rootIndex >= 0 ? items[rootIndex] : {}
  const section = {
    ...existingRoot,
    title: '招商公告',
    menuName: '招商公告',
    menuCode: 'investment-announcement',
    path: undefined,
    children
  }
  const assetIndex = cleaned.findIndex(item => item.menuCode === 'asset'
    || item.title === '资产管理'
    || item.menuName === '资产管理')
  const result = [...cleaned]
  result.splice(assetIndex >= 0 ? assetIndex + 1 : 0, 0, section)
  return result
}

const tenantWorkspaceLegacyPaths = new Set([
  '/tenants',
  '/contracts',
  '/leases',
  '/fee-rules',
  '/billing-plans'
])

export function consolidateTenantManagementMenus(items = []) {
  const flatMenus = flattenMenus(items)
  const source = flatMenus.find(item => item.path === '/tenant/workspace')
    || flatMenus.find(item => ['/tenants', '/contracts', '/leases'].includes(item.path))
  const rootIndex = items.findIndex(item => item.menuCode === 'lease'
    || item.title === '租赁管理'
    || item.title === '租户管理'
    || item.menuName === '租赁管理'
    || item.menuName === '租户管理')
  const cleaned = items
    .filter((_, index) => index !== rootIndex)
    .map(section => ({
      ...section,
      children: Array.isArray(section.children)
        ? section.children.filter(child => !tenantWorkspaceLegacyPaths.has(child.path) && child.path !== '/tenant/workspace')
        : section.children
    }))

  if (!source) return cleaned

  const previousRoot = rootIndex >= 0 ? items[rootIndex] : {}
  const section = {
    ...previousRoot,
    title: '租户管理',
    menuName: '租户管理',
    menuCode: 'lease',
    path: undefined,
    children: [{
      ...source,
      title: '租户档案',
      menuName: '租户档案',
      menuCode: 'lease:tenant-workspace',
      path: '/tenant/workspace',
      children: []
    }]
  }
  const investmentIndex = cleaned.findIndex(item => item.menuCode === 'investment-announcement')
  const result = [...cleaned]
  result.splice(investmentIndex >= 0 ? investmentIndex + 1 : 0, 0, section)
  return result
}

export function normalizeMenu(item) {
  return {
    ...item,
    title: item.title || item.menuName,
    index: item.menuCode || item.path || String(item.id),
    children: Array.isArray(item.children) ? item.children.map(normalizeMenu) : []
  }
}

export function isPathAllowed(path, currentRoles = getCurrentRoles()) {
  if (!path) return true
  const matchedEntry = Object.entries(restrictedPathRoles).find(([basePath]) => path === basePath || path.startsWith(basePath + '/'))
  if (!matchedEntry) return true
  const [, allowedRoles] = matchedEntry
  return allowedRoles.some(role => currentRoles.includes(role))
}

function filterMenus(items, currentRoles = getCurrentRoles()) {
  return items
    .map(normalizeMenu)
    .map(item => ({
      ...item,
      children: Array.isArray(item.children) ? filterMenus(item.children, currentRoles) : []
    }))
    .filter(item => {
      if (item.path) {
        if (!isPathAllowed(item.path, currentRoles)) return false
        if (isSuperAdminRole(currentRoles)) return true
        const module = getModulePermissionBundleForPath(item.path)
        if (module) return canReadModule(module.key, null, currentRoles)
        const viewPermissions = getPathViewPermissionCodes(item.path)
        return viewPermissions.length === 0 || hasAnyPermission(viewPermissions)
      }
      return item.children.length > 0
    })
}

export function getVisibleMenuSections(currentRoles = getCurrentRoles()) {
  const storedMenus = getStoredMenus()
  const sourceMenus = consolidateTenantManagementMenus(
    consolidateInvestmentAnnouncementMenus(consolidateAssetMenus(
      isSuperAdminRole(currentRoles) ? mergeMenusWithFallback(storedMenus) : storedMenus
    ))
  )
  return filterMenus(sourceMenus, currentRoles)
}

function collectPaths(menus, result = []) {
  menus.forEach(item => {
    if (Array.isArray(item.children) && item.children.length > 0) {
      collectPaths(item.children, result)
    } else if (item.path) {
      result.push(item.path)
    }
  })
  return result
}

export function getFirstAccessibleRoute(currentRoles = getCurrentRoles()) {
  const paths = collectPaths(getVisibleMenuSections(currentRoles))
  if (paths.includes('/dashboard')) return '/dashboard'
  return paths[0] || '/403'
}

export function canAccessPath(currentRoles, path) {
  if (!localStorage.getItem('token')) return false
  if (path === '/403') return true
  if (hasAnyRole(['SUPER_ADMIN'])) return true
  const accessPath = canonicalAccessPath(path)
  if (!isPathAllowed(accessPath, currentRoles)) return false
  const paths = collectPaths(getVisibleMenuSections(currentRoles))
  if (paths.some(item => accessPath === item || accessPath.startsWith(item + '/'))) {
    return true
  }

  const pathPermissionCodes = getPathViewPermissionCodes(accessPath)
  return pathPermissionCodes.length > 0 && hasAnyPermission(pathPermissionCodes)
}

export { getCurrentRoles }
