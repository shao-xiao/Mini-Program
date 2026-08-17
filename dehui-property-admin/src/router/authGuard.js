import { canAccessPath, getCurrentRoles, getFirstAccessibleRoute } from '../config/access.js'
import { ensureCurrentUserAccess } from '../utils/authAccess.js'

export function createAuthGuard(dependencies = {}) {
  const readToken = dependencies.readToken || (() => localStorage.getItem('token'))
  const refreshAccess = dependencies.refreshAccess || ensureCurrentUserAccess
  const readRoles = dependencies.readRoles || getCurrentRoles
  const firstAccessibleRoute = dependencies.firstAccessibleRoute || getFirstAccessibleRoute
  const canAccess = dependencies.canAccess || canAccessPath

  return async function authGuard(to) {
    if (to.path === '/login') return true
    if (!readToken()) return '/login'

    try {
      await refreshAccess({ silent: true })
    } catch {
      if (!readToken()) return '/login'
      return to.path === '/403' ? true : '/403'
    }

    const roles = readRoles()
    if (to.path === '/') return firstAccessibleRoute(roles)
    if (to.path !== '/403' && !canAccess(roles, to.path)) return '/403'
    return true
  }
}
