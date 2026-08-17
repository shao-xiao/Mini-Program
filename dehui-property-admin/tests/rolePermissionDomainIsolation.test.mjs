import assert from 'node:assert/strict'
import test from 'node:test'

import { buildRolePermissionTree, normalizeSelectedPermissionIds } from '../src/utils/rolePermissionTree.js'

const menus = [{
  id: 10,
  menuCode: 'investment-announcement',
  menuName: '招商公告',
  children: [{
    id: 11,
    menuCode: 'investment-announcement:manage',
    menuName: '招商管理',
    path: '/investment/manage'
  }]
}]

const permissions = [
  { id: 1, permissionCode: 'investment-content:view' },
  { id: 2, permissionCode: 'investment-content:update' },
  { id: 3, permissionCode: 'investment-listing:view' },
  { id: 4, permissionCode: 'investment-listing:update' }
]

test('unified manage page only adds the view permission for the selected permission domain', () => {
  const tree = buildRolePermissionTree(menus, permissions)

  assert.deepEqual(normalizeSelectedPermissionIds(tree, [4]).sort(), [3, 4])
  assert.deepEqual(normalizeSelectedPermissionIds(tree, [2]).sort(), [1, 2])
  assert.deepEqual(normalizeSelectedPermissionIds(tree, [2, 4]).sort(), [1, 2, 3, 4])
})
