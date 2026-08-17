import assert from 'node:assert/strict'
import test from 'node:test'

const storage = new Map()

global.localStorage = {
  getItem(key) {
    return storage.has(key) ? storage.get(key) : null
  },
  setItem(key, value) {
    storage.set(key, String(value))
  },
  removeItem(key) {
    storage.delete(key)
  },
  clear() {
    storage.clear()
  }
}

const { hasAnyPermission, hasPermission } = await import('../src/utils/permission.js')

test('frontend permission helpers support wildcard permission codes', () => {
  localStorage.clear()
  localStorage.setItem('roles', JSON.stringify(['ENGINEER']))
  localStorage.setItem('permissions', JSON.stringify(['system:*', 'workorder:*']))

  assert.equal(hasPermission('system:role:assign-permission'), true)
  assert.equal(hasPermission('workorder:close'), true)
  assert.equal(hasPermission('parking:create'), false)
  assert.equal(hasAnyPermission(['parking:create', 'workorder:assign']), true)
})
