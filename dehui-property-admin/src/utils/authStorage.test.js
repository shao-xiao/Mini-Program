import assert from 'node:assert/strict'
import test from 'node:test'
import { ADMIN_SESSION_STORAGE_KEYS, clearAdminSession } from './authStorage.js'

function createMemoryStorage(entries) {
  const values = new Map(entries)
  return {
    getItem: key => values.get(key) ?? null,
    removeItem: key => values.delete(key)
  }
}

test('logout removes authentication state while preserving saved workspace views', () => {
  const workspaceKey = 'dhic-data-workspace:v2:admin:%2Fworkorders'
  const storage = createMemoryStorage([
    ...ADMIN_SESSION_STORAGE_KEYS.map(key => [key, `session-${key}`]),
    [workspaceKey, '{"activeViewId":"default"}'],
    ['dhic-side-menu-order-v2', '{"root":["dashboard"]}']
  ])

  clearAdminSession(storage)

  ADMIN_SESSION_STORAGE_KEYS.forEach(key => assert.equal(storage.getItem(key), null))
  assert.equal(storage.getItem(workspaceKey), '{"activeViewId":"default"}')
  assert.equal(storage.getItem('dhic-side-menu-order-v2'), '{"root":["dashboard"]}')
})
