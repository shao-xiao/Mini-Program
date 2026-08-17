import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'

import { getVisibleMenuSections, canAccessPath } from '../config/access.js'
import { applyUserAccessToStorage } from './authAccess.js'
import { hasPermission } from './permission.js'

const store = new Map()

global.localStorage = {
  getItem(key) {
    return store.has(key) ? store.get(key) : null
  },
  setItem(key, value) {
    store.set(key, String(value))
  },
  removeItem(key) {
    store.delete(key)
  },
  clear() {
    store.clear()
  }
}

beforeEach(() => {
  localStorage.clear()
  localStorage.setItem('token', 'token')
})

test('current user access refresh overwrites stale permissions for buttons and routes', () => {
  localStorage.setItem('roles', JSON.stringify(['FINANCE']))
  localStorage.setItem('permissions', JSON.stringify(['finance:bill:export']))
  localStorage.setItem('menus', JSON.stringify([
    {
      title: '财务管理',
      menuName: '财务管理',
      menuCode: 'finance',
      children: [
        { title: '账单管理', menuName: '账单管理', path: '/bills' }
      ]
    }
  ]))

  applyUserAccessToStorage({
    roles: ['ENGINEER'],
    permissions: ['workorder:view', 'workorder:assign'],
    menus: [
      {
        title: '运营管理',
        menuName: '运营管理',
        menuCode: 'operation',
        children: [
          { title: '工单管理', menuName: '工单管理', path: '/workorders' }
        ]
      }
    ]
  })

  assert.equal(hasPermission('finance:bill:export'), false)
  assert.equal(hasPermission('workorder:assign'), true)
  assert.equal(canAccessPath(['ENGINEER'], '/bills'), false)
  assert.equal(canAccessPath(['ENGINEER'], '/workorders'), true)
})

test('current user access refresh removes menus when view permission is removed', () => {
  localStorage.setItem('roles', JSON.stringify(['ENGINEER']))
  localStorage.setItem('permissions', JSON.stringify(['workorder:view']))
  localStorage.setItem('menus', JSON.stringify([
    {
      title: '运营管理',
      menuName: '运营管理',
      menuCode: 'operation',
      children: [
        { title: '工单管理', menuName: '工单管理', path: '/workorders' }
      ]
    }
  ]))

  applyUserAccessToStorage({
    roles: ['ENGINEER'],
    permissions: [],
    menus: [
      {
        title: '运营管理',
        menuName: '运营管理',
        menuCode: 'operation',
        children: [
          { title: '工单管理', menuName: '工单管理', path: '/workorders' }
        ]
      }
    ]
  })

  assert.equal(canAccessPath(['ENGINEER'], '/workorders'), false)
  assert.deepEqual(getVisibleMenuSections(['ENGINEER']), [])
})

test('ADMIN follows configured permissions while SUPER_ADMIN keeps full access', () => {
  localStorage.setItem('roles', JSON.stringify(['ADMIN']))
  localStorage.setItem('permissions', JSON.stringify([]))
  localStorage.setItem('menus', JSON.stringify([
    {
      title: '系统管理',
      menuName: '系统管理',
      menuCode: 'system',
      children: [
        { title: '角色管理', menuName: '角色管理', path: '/system/roles' }
      ]
    }
  ]))

  assert.equal(hasPermission('system:role:delete'), false)
  assert.equal(canAccessPath(['ADMIN'], '/system/roles'), false)

  localStorage.setItem('roles', JSON.stringify(['SUPER_ADMIN']))

  assert.equal(hasPermission('system:role:delete'), true)
  assert.equal(canAccessPath(['SUPER_ADMIN'], '/system/roles'), true)
})
