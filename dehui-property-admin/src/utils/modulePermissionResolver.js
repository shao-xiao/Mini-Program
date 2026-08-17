import {
  getModulePermissionState,
  modulePermissionBundles,
  modulePermissionSections,
  permissionCodeIsGranted
} from '../config/modulePermissionBundles.js'

function unique(values = []) {
  const seen = new Set()
  return values.filter(value => {
    const normalized = String(value || '').trim()
    if (!normalized || seen.has(normalized)) return false
    seen.add(normalized)
    return true
  }).map(value => String(value).trim())
}

function permissionCode(permission = {}) {
  return String(permission.permissionCode || permission.code || '').trim()
}

function isEnabled(item = {}) {
  return String(item.status || 'ENABLED').toUpperCase() !== 'DISABLED'
}

export function permissionCodesFromRoleAccess(permissions = [], access = {}) {
  const enabled = permissions.filter(isEnabled)
  const codesById = new Map(enabled.map(item => [String(item.id), permissionCode(item)]))
  const availableCodes = new Set(enabled.map(permissionCode).filter(Boolean))
  return unique([
    ...(access?.permissionIds || access?.permissions || []).map(id => codesById.get(String(id))),
    ...(access?.permissionCodes || []).filter(code => availableCodes.has(String(code)))
  ])
}

export function permissionIdsForCodes(permissions = [], codes = []) {
  const selected = new Set(unique(codes))
  return permissions
    .filter(item => isEnabled(item) && selected.has(permissionCode(item)))
    .map(item => item.id)
    .filter(id => id !== undefined && id !== null)
}

export function nextModuleAccessChange(current = {}, accessType, checked) {
  const next = { ...current, [accessType]: Boolean(checked) }
  if (accessType === 'read' && !checked) next.write = false
  if (accessType === 'write' && checked) next.read = true
  return next
}

function wildcardCode(code) {
  return code === '*' || code.endsWith(':*')
}

function touchedBundleCodes(changes = {}, bundles = modulePermissionBundles) {
  const touched = new Set()
  bundles.forEach(module => {
    if (!changes[module.key]) return
    ;[...module.readCodes, ...module.writeCodes].forEach(code => touched.add(code))
  })
  return touched
}

function exactWriteCodes(bundles = modulePermissionBundles) {
  return new Set(bundles
    .filter(module => module.writeRequiresExact)
    .flatMap(module => module.writeCodes))
}

function expandTouchedWildcards(originalCodes, changes, availableCodes, bundles) {
  const touchedCodes = touchedBundleCodes(changes, bundles)
  const protectedWriteCodes = exactWriteCodes(bundles)
  const expanded = []
  originalCodes.forEach(code => {
    const shouldExpand = wildcardCode(code)
      && Array.from(touchedCodes).some(required => permissionCodeIsGranted([code], required))
    if (!shouldExpand) {
      expanded.push(code)
      return
    }
    availableCodes
      .filter(candidate => !wildcardCode(candidate)
        && !protectedWriteCodes.has(candidate)
        && permissionCodeIsGranted([code], candidate))
      .forEach(candidate => expanded.push(candidate))
  })
  return unique(expanded)
}

function moduleWantsCode(module, code, change, originalCodes) {
  let wanted = false
  if (module.readCodes.includes(code)) {
    if (change?.write === true || change?.read === true) wanted = true
    else if (change?.read === false) wanted = false
    else wanted = originalCodes.has(code)
  }
  if (module.writeCodes.includes(code)) {
    if (change?.write === true) wanted = true
    else if (change?.write === false) wanted = wanted || false
    else wanted = wanted || originalCodes.has(code)
  }
  return wanted
}

export function resolveModulePermissionCodes({
  originalCodes = [],
  changes = {},
  availableCodes = [],
  bundles = modulePermissionBundles
} = {}) {
  const normalizedOriginal = unique(originalCodes)
  const touchedKeys = Object.keys(changes).filter(key => changes[key])
  if (touchedKeys.length === 0) return normalizedOriginal

  const normalizedAvailable = unique(availableCodes.length > 0
    ? availableCodes
    : bundles.flatMap(module => [...module.readCodes, ...module.writeCodes]))
  const available = new Set(normalizedAvailable)
  const expandedOriginal = expandTouchedWildcards(
    normalizedOriginal,
    changes,
    normalizedAvailable,
    bundles
  )
  const originalSet = new Set(expandedOriginal)
  const selected = new Set(expandedOriginal)
  const activeCodes = unique(bundles.flatMap(module => [...module.readCodes, ...module.writeCodes]))

  activeCodes.forEach(code => {
    if (!available.has(code) && !originalSet.has(code)) return
    const owners = bundles.filter(module => module.readCodes.includes(code) || module.writeCodes.includes(code))
    const wanted = owners.some(module => moduleWantsCode(module, code, changes[module.key], originalSet))
    if (wanted) selected.add(code)
    else selected.delete(code)
  })

  const ordered = []
  expandedOriginal.forEach(code => {
    if (selected.has(code)) ordered.push(code)
  })
  bundles.forEach(module => {
    ;[...module.readCodes, ...module.writeCodes].forEach(code => {
      if (selected.has(code)) ordered.push(code)
    })
  })
  normalizedAvailable.forEach(code => {
    if (selected.has(code)) ordered.push(code)
  })
  return unique(ordered)
}

function flattenMenus(items = [], ancestors = [], result = []) {
  items.forEach(item => {
    if (!isEnabled(item)) return
    const code = String(item.menuCode || item.code || '').trim()
    const currentAncestors = code ? [...ancestors, code] : ancestors
    result.push({
      ...item,
      code,
      ancestors,
      id: item.id ?? item.menuId
    })
    flattenMenus(item.children || [], currentAncestors, result)
  })
  return result
}

export function menuCodesFromRoleAccess(menus = [], access = {}) {
  const flat = flattenMenus(menus)
  const codesById = new Map(flat.map(item => [String(item.id), item.code]))
  const availableCodes = new Set(flat.map(item => item.code).filter(Boolean))
  return unique([
    ...(access?.menuIds || access?.menus || []).map(id => codesById.get(String(id))),
    ...(access?.menuCodes || []).filter(code => availableCodes.has(String(code)))
  ])
}

export function buildRoleMenuSelection({
  menus = [],
  permissionCodes = [],
  originalMenuCodes = []
} = {}) {
  const flat = flattenMenus(menus)
  const menusByCode = new Map(flat.filter(item => item.code).map(item => [item.code, item]))
  const activeMenuCodes = new Set(modulePermissionSections
    .filter(section => !section.permissionOnly)
    .flatMap(section => [
    section.menuCode,
    ...section.modules.map(module => module.menuCode)
    ]))
  const menuCodes = unique(originalMenuCodes)
    .filter(code => menusByCode.has(code) && !activeMenuCodes.has(code))

  modulePermissionSections.forEach(section => {
    if (section.permissionOnly) return
    section.modules.forEach(module => {
      if (!getModulePermissionState(module, permissionCodes).read) return
      if (menusByCode.has(section.menuCode)) menuCodes.push(section.menuCode)
      if (menusByCode.has(module.menuCode)) menuCodes.push(module.menuCode)
    })
  })

  const normalizedCodes = unique(menuCodes)
  return {
    menuCodes: normalizedCodes,
    menuIds: normalizedCodes
      .map(code => menusByCode.get(code)?.id)
      .filter(id => id !== undefined && id !== null)
  }
}

export function findMissingModulePermissionCodes(permissions = []) {
  const available = new Set(
    permissions.filter(isEnabled).map(permissionCode).filter(Boolean)
  )
  return modulePermissionBundles.flatMap(module => {
    const missingCodes = [...module.readCodes, ...module.writeCodes]
      .filter(code => !available.has(code))
    return missingCodes.length > 0 ? [{ moduleKey: module.key, moduleTitle: module.title, missingCodes }] : []
  })
}
