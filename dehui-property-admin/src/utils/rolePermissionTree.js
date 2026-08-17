import { fallbackMenuSections } from '../config/access.js'
import { getPathPermissionCodes } from '../config/permissionRules.js'

export const UNMATCHED_PERMISSION_MODULE_NAME = '未归类权限'

export function permissionCode(permission = {}) {
  return permission.permissionCode || permission.code || ''
}

function permissionIdKey(id) {
  return String(id)
}

function normalizeMenuLabel(item = {}) {
  return item.title || item.menuName || item.name || item.label || item.code || item.menuCode || '未命名模块'
}

function normalizeMenuPath(item = {}) {
  return item.path || item.routePath || item.url || ''
}

function normalizeMenuKey(item = {}, fallback) {
  return item.menuCode || item.code || item.index || normalizeMenuPath(item) || fallback
}

function normalizePermission(permission = {}) {
  const code = permissionCode(permission)
  return {
    ...permission,
    id: permission.id,
    code,
    permissionCode: code,
    label: permission.permissionName || permission.name || code || '未命名权限'
  }
}

function normalizeMenuNode(item = {}, indexPath = '0', ancestorMenuIds = []) {
  const label = normalizeMenuLabel(item)
  const path = normalizeMenuPath(item)
  const menuId = item.id ?? item.menuId
  const currentAncestorMenuIds = menuId === undefined || menuId === null
    ? ancestorMenuIds
    : [...ancestorMenuIds, menuId]
  return {
    ...item,
    key: normalizeMenuKey(item, `${indexPath}:${label}`),
    label,
    path,
    menuId,
    ancestorMenuIds,
    children: Array.isArray(item.children)
      ? item.children.map((child, index) => normalizeMenuNode(child, `${indexPath}-${index}`, currentAncestorMenuIds))
      : []
  }
}

function collectSourceMenus(menus = []) {
  const source = Array.isArray(menus) && menus.length > 0 ? menus : fallbackMenuSections
  return source.map((item, index) => normalizeMenuNode(item, String(index)))
}

function createFeatureNode(root, menu, permissionsByCode, assignedCodes) {
  const codes = getPathPermissionCodes(menu.path)
  const permissionItems = codes
    .map(code => permissionsByCode.get(code))
    .filter(Boolean)
    .map(normalizePermission)

  permissionItems.forEach(item => assignedCodes.add(item.code))

  return {
    key: `${root.key}:${menu.key}`,
    label: menu.label,
    path: menu.path,
    menuCode: menu.menuCode || menu.code,
    menuId: menu.menuId,
    ancestorMenuIds: menu.ancestorMenuIds || [],
    permissions: permissionItems
  }
}

function createRootNode(root, permissionsByCode, assignedCodes) {
  const menus = root.children.length > 0 ? root.children : [root]
  return {
    key: root.key,
    label: root.label,
    menuCode: root.menuCode || root.code,
    menuId: root.menuId,
    ancestorMenuIds: root.ancestorMenuIds || [],
    children: menus.map(menu => createFeatureNode(root, menu, permissionsByCode, assignedCodes))
  }
}

export function buildRolePermissionTree(menus = fallbackMenuSections, permissions = []) {
  const normalizedPermissions = permissions.map(normalizePermission).filter(item => item.id !== undefined && item.id !== null && item.code)
  const permissionsByCode = new Map(normalizedPermissions.map(item => [item.code, item]))
  const assignedCodes = new Set()

  const tree = collectSourceMenus(menus).map(root => createRootNode(root, permissionsByCode, assignedCodes))
  const unmatchedPermissions = normalizedPermissions.filter(item => !assignedCodes.has(item.code))

  if (unmatchedPermissions.length > 0) {
    tree.push({
      key: '__unmatched_permissions__',
      label: UNMATCHED_PERMISSION_MODULE_NAME,
      children: [
        {
          key: '__unmatched_permissions__:feature',
          label: UNMATCHED_PERMISSION_MODULE_NAME,
          path: '',
          permissions: unmatchedPermissions
        }
      ]
    })
  }

  return tree
}

export function collectPermissionIds(node) {
  if (!node) return []
  if (Array.isArray(node.permissions)) {
    return node.permissions.map(item => item.id).filter(id => id !== undefined && id !== null)
  }
  return (node.children || []).flatMap(child => collectPermissionIds(child))
}

export function uniquePermissionIds(ids = []) {
  const result = []
  const seen = new Set()
  ids.forEach(id => {
    const key = permissionIdKey(id)
    if (seen.has(key)) return
    seen.add(key)
    result.push(id)
  })
  return result
}

export function permissionIdsFromRoleAccess(permissions = [], access = {}) {
  const validPermissionIds = new Set(
    permissions
      .map(item => item?.id)
      .filter(id => id !== undefined && id !== null)
      .map(permissionIdKey)
  )
  const permissionIdsByCode = new Map(
    permissions
      .map(item => [permissionCode(item), item?.id])
      .filter(([code, id]) => code && id !== undefined && id !== null)
  )
  const ids = [
    ...(access?.permissionIds || access?.permissions || [])
      .filter(id => validPermissionIds.has(permissionIdKey(id))),
    ...(access?.permissionCodes || [])
      .map(code => permissionIdsByCode.get(code))
      .filter(id => id !== undefined && id !== null)
  ]
  return uniquePermissionIds(ids)
}

export function permissionCodesForIds(permissions = [], ids = []) {
  const selected = new Set(uniquePermissionIds(ids).map(permissionIdKey))
  return Array.from(new Set(
    permissions
      .filter(item => selected.has(permissionIdKey(item?.id)))
      .map(permissionCode)
      .filter(Boolean)
  ))
}

function walkFeatures(tree = [], visitor) {
  tree.forEach(module => {
    ;(module.children || []).forEach(feature => visitor(feature, module))
  })
}

export function normalizeSelectedPermissionIds(tree = [], selectedIds = []) {
  const next = new Map(uniquePermissionIds(selectedIds).map(id => [permissionIdKey(id), id]))
  const selected = new Set(next.keys())

  walkFeatures(tree, feature => {
    const featureIds = collectPermissionIds(feature)
    const hasSelected = featureIds.some(id => selected.has(permissionIdKey(id)))
    if (!hasSelected) return
    viewPermissionIdsForSelection(feature, selected).forEach(id => next.set(permissionIdKey(id), id))
  })

  return Array.from(next.values())
}

export function collectMenuIdsForPermissionIds(tree = [], selectedIds = []) {
  const selected = new Set(normalizeSelectedPermissionIds(tree, selectedIds).map(permissionIdKey))
  const menuIds = []
  const seen = new Set()

  function addMenuId(id) {
    if (id === undefined || id === null) return
    const key = String(id)
    if (seen.has(key)) return
    seen.add(key)
    menuIds.push(id)
  }

  walkFeatures(tree, feature => {
    const hasSelected = collectPermissionIds(feature).some(id => selected.has(permissionIdKey(id)))
    if (!hasSelected) return
    ;(feature.ancestorMenuIds || []).forEach(addMenuId)
    addMenuId(feature.menuId)
  })

  return menuIds
}

export function selectedPermissionCount(node, selectedIds = []) {
  const selected = new Set(selectedIds.map(permissionIdKey))
  return collectPermissionIds(node).filter(id => selected.has(permissionIdKey(id))).length
}

export function totalPermissionCount(node) {
  return collectPermissionIds(node).length
}

export function filterRolePermissionTree(tree = [], keyword = '') {
  const normalizedKeyword = String(keyword || '').trim().toLowerCase()
  if (!normalizedKeyword) return tree

  function permissionMatches(permission) {
    return [
      permission.label,
      permission.permissionName,
      permission.name,
      permission.code,
      permission.permissionCode
    ].some(value => String(value || '').toLowerCase().includes(normalizedKeyword))
  }

  function featureMatches(feature) {
    return [feature.label, feature.path, feature.menuCode]
      .some(value => String(value || '').toLowerCase().includes(normalizedKeyword))
  }

  return tree
    .map(module => {
      const moduleMatched = [module.label, module.menuCode]
        .some(value => String(value || '').toLowerCase().includes(normalizedKeyword))
      const children = (module.children || [])
        .map(feature => {
          const matchedPermissions = moduleMatched || featureMatches(feature)
            ? feature.permissions
            : feature.permissions.filter(permissionMatches)
          return { ...feature, permissions: matchedPermissions }
        })
        .filter(feature => moduleMatched || featureMatches(feature) || feature.permissions.length > 0)

      return { ...module, children }
    })
    .filter(module => module.children.length > 0)
}

function isViewPermission(permission = {}) {
  const code = permissionCode(permission)
  const action = permission.action || code.split(':').slice(1).join(':')
  return action === 'view' || action.endsWith(':view') || code.endsWith(':view')
}

function viewPermissionIds(feature = {}) {
  return (feature.permissions || [])
    .filter(isViewPermission)
    .map(item => item.id)
}

function viewPermissionIdsForSelection(feature = {}, selectedIds = new Set()) {
  const selectedDomains = new Set(
    (feature.permissions || [])
      .filter(item => selectedIds.has(permissionIdKey(item.id)))
      .map(item => permissionCode(item).split(':')[0])
      .filter(Boolean)
  )
  return (feature.permissions || [])
    .filter(item => selectedDomains.has(permissionCode(item).split(':')[0]) && isViewPermission(item))
    .map(item => item.id)
}

export function nextPermissionIdsAfterBulkToggle(selectedIds = [], ids = [], checked = false) {
  const next = new Map(selectedIds.map(id => [permissionIdKey(id), id]))
  ids.forEach(id => {
    const key = permissionIdKey(id)
    if (checked) {
      next.set(key, id)
    } else {
      next.delete(key)
    }
  })
  return Array.from(next.values())
}

export function nextPermissionIdsAfterToggle(selectedIds = [], feature = {}, permissionId, checked = false) {
  const next = new Map(selectedIds.map(id => [permissionIdKey(id), id]))
  const permission = (feature.permissions || []).find(item => permissionIdKey(item.id) === permissionIdKey(permissionId))
  if (!permission) return Array.from(next.values())

  if (checked) {
    next.set(permissionIdKey(permission.id), permission.id)
    if (!isViewPermission(permission)) {
      viewPermissionIds(feature).forEach(id => next.set(permissionIdKey(id), id))
    }
    return Array.from(next.values())
  }

  if (isViewPermission(permission)) {
    collectPermissionIds(feature).forEach(id => next.delete(permissionIdKey(id)))
  } else {
    next.delete(permissionIdKey(permission.id))
  }

  return Array.from(next.values())
}
