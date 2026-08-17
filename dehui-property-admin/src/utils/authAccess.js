import request from './request.js'

let accessRefreshToken = ''
let accessRefreshPromise = null

function normalizedArray(value) {
  return Array.isArray(value) ? value : []
}

export function applyUserAccessToStorage(access = {}) {
  const roles = normalizedArray(access.roles)
  const permissions = normalizedArray(access.permissions)
  const menus = normalizedArray(access.menus)

  localStorage.setItem('roles', JSON.stringify(roles))
  localStorage.setItem('permissions', JSON.stringify(permissions))
  localStorage.setItem('menus', JSON.stringify(menus))
  localStorage.setItem('role', roles[0] || '')

  return { roles, permissions, menus }
}

export async function refreshCurrentUserAccess(options = {}) {
  const access = await request.get('/system/user/access', { silent: options.silent !== false })
  return applyUserAccessToStorage(access || {})
}

export async function ensureCurrentUserAccess(options = {}) {
  const token = localStorage.getItem('token') || ''
  if (!token) {
    return null
  }

  if (!options.force && accessRefreshToken === token) {
    return null
  }

  if (accessRefreshPromise) {
    return accessRefreshPromise
  }

  accessRefreshPromise = refreshCurrentUserAccess(options)
    .then(result => {
      accessRefreshToken = token
      return result
    })
    .finally(() => {
      accessRefreshPromise = null
    })

  return accessRefreshPromise
}

export function resetCurrentUserAccessRefresh() {
  accessRefreshToken = ''
  accessRefreshPromise = null
}
