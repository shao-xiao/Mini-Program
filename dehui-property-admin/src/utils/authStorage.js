export const ADMIN_SESSION_STORAGE_KEYS = Object.freeze([
  'token',
  'username',
  'role',
  'roles',
  'permissions',
  'menus',
  'userId',
  'userInfo'
])

export function clearAdminSession(storage = globalThis.localStorage) {
  ADMIN_SESSION_STORAGE_KEYS.forEach(key => storage?.removeItem(key))
}
