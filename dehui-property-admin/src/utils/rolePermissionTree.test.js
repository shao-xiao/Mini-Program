import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildRolePermissionTree,
  collectMenuIdsForPermissionIds,
  normalizeSelectedPermissionIds,
  permissionCodesForIds,
  permissionIdsFromRoleAccess
} from './rolePermissionTree.js'

test('normalizes selected operation permissions with view permission and menu ancestors', () => {
  const menus = [
    {
      id: 10,
      menuCode: 'operation',
      menuName: 'Operation',
      children: [
        { id: 11, menuCode: 'operation:workorders', menuName: 'Work orders', path: '/workorders' }
      ]
    }
  ]
  const permissions = [
    { id: 1, permissionCode: 'workorder:view', permissionName: 'View workorders' },
    { id: 2, permissionCode: 'workorder:assign', permissionName: 'Assign workorders' },
    { id: 3, permissionCode: 'finance:bill:view', permissionName: 'View finance bills' }
  ]

  const tree = buildRolePermissionTree(menus, permissions)
  const normalizedPermissionIds = normalizeSelectedPermissionIds(tree, [2])

  assert.deepEqual(normalizedPermissionIds, [2, 1])
  assert.deepEqual(collectMenuIdsForPermissionIds(tree, normalizedPermissionIds), [10, 11])
})

test('restores checked permissions from stable codes when numeric ids change', () => {
  const permissions = [
    { id: 101, permissionCode: 'energy:view' },
    { id: 102, permissionCode: 'energy:reading:update' }
  ]

  assert.deepEqual(permissionIdsFromRoleAccess(permissions, {
    permissionIds: [9],
    permissionCodes: ['energy:view', 'energy:reading:update']
  }), [101, 102])
})

test('serializes selected permissions with stable codes without duplicates', () => {
  const permissions = [
    { id: 101, permissionCode: 'energy:view' },
    { id: 102, code: 'energy:reading:update' },
    { id: 102, permissionCode: 'energy:reading:update' }
  ]

  assert.deepEqual(permissionCodesForIds(permissions, [101, '102', 102]), [
    'energy:view',
    'energy:reading:update'
  ])
})
