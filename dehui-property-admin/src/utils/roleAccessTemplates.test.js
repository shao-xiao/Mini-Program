import test from 'node:test'
import assert from 'node:assert/strict'

import { buildRolePermissionTree } from './rolePermissionTree.js'
import {
  buildRoleTemplatePermissionIds,
  isMatrixRoleCode,
  matrixRoleCodes,
  summarizeTemplateSelection
} from './roleAccessTemplates.js'

const menus = [
  {
    id: 10,
    menuCode: 'operation',
    menuName: 'Operation',
    children: [
      { id: 11, menuCode: 'operation:workorders', menuName: 'Work orders', path: '/workorders' }
    ]
  },
  {
    id: 20,
    menuCode: 'finance',
    menuName: 'Finance',
    children: [
      { id: 21, menuCode: 'finance:bills', menuName: 'Bills', path: '/bills' },
      { id: 22, menuCode: 'finance:dashboard', menuName: 'Finance dashboard', path: '/finance/dashboard' }
    ]
  }
]

const permissions = [
  { id: 1, permissionCode: 'workorder:view', permissionName: 'View workorders' },
  { id: 2, permissionCode: 'workorder:assign', permissionName: 'Assign workorders' },
  { id: 3, permissionCode: 'bill:view', permissionName: 'View bills' },
  { id: 4, permissionCode: 'bill:pay', permissionName: 'Pay bills' },
  { id: 5, permissionCode: 'finance:dashboard:view', permissionName: 'View finance dashboard' },
  { id: 6, permissionCode: 'finance:bill:export', permissionName: 'Export finance bills' },
  { id: 7, permissionCode: 'workorder:*', permissionName: 'All workorder permissions' },
  { id: 8, permissionCode: 'bill:*', permissionName: 'All bill permissions' },
  { id: 9, permissionCode: 'workorder:update', permissionName: 'Update workorders' },
  { id: 10, permissionCode: 'bill:delete', permissionName: 'Delete draft bills' },
  { id: 11, permissionCode: 'bill:delete-approved', permissionName: 'Delete approved bills' }
]

test('matrix role codes exclude tenant roles and keep the internal role set explicit', () => {
  assert.deepEqual(matrixRoleCodes, [
    'SUPER_ADMIN',
    'ADMIN',
    'INVESTMENT_MANAGER',
    'STAFF',
    'FINANCE',
    'ACCOUNTANT',
    'CASHIER',
    'ENGINEER_MANAGER',
    'ENGINEER',
    'SECURITY_MANAGER',
    'SECURITY'
  ])
  assert.equal(isMatrixRoleCode('TENANT'), false)
  assert.equal(isMatrixRoleCode('TENANT_ADMIN'), false)
  assert.equal(isMatrixRoleCode('TENANT_STAFF'), false)
  assert.equal(isMatrixRoleCode('PROPERTY_MANAGER'), false)
})

test('engineer role template grants workorder permissions without finance permissions', () => {
  const tree = buildRolePermissionTree(menus, permissions)
  const ids = buildRoleTemplatePermissionIds('ENGINEER', tree)

  assert.deepEqual(ids.sort((a, b) => a - b), [1, 2, 9])
})

test('finance role template grants finance permissions without engineering permissions', () => {
  const tree = buildRolePermissionTree(menus, permissions)
  const ids = buildRoleTemplatePermissionIds('FINANCE', tree)

  assert.deepEqual(ids.sort((a, b) => a - b), [3, 4, 5])
})

test('business role templates never grant wildcard permissions', () => {
  const tree = buildRolePermissionTree(menus, permissions)
  const wildcardIds = new Set([7, 8])

  matrixRoleCodes
    .filter(roleCode => roleCode !== 'SUPER_ADMIN')
    .forEach(roleCode => {
      const ids = buildRoleTemplatePermissionIds(roleCode, tree)
      assert.equal(ids.some(id => wildcardIds.has(id)), false, `${roleCode} must not grant wildcard permissions`)
    })

  assert.equal(buildRoleTemplatePermissionIds('SUPER_ADMIN', tree).some(id => wildcardIds.has(id)), true)
})

test('SUPER_ADMIN default template has broader scope than ADMIN default template', () => {
  const tree = buildRolePermissionTree(
    [
      {
        id: 1,
        menuName: 'System',
        path: '/system',
        children: [
          { id: 10, menuName: '系统角色', path: '/system/roles' },
          { id: 11, menuName: '系统用户', path: '/system/users' }
        ]
      },
      {
        id: 2,
        menuName: 'Dashboard',
        path: '/dashboard',
        children: [{ id: 12, menuName: '首页', path: '/dashboard' }]
      },
      {
        id: 3,
        menuName: 'Finance',
        path: '/finance/dashboard',
        children: [{ id: 13, menuName: '财务', path: '/finance/dashboard' }]
      }
    ],
    [
      { id: 1, permissionCode: 'system:role:view', permissionName: '系统角色查看' },
      { id: 2, permissionCode: 'system:user:view', permissionName: '系统用户查看' },
      { id: 3, permissionCode: 'dashboard:view', permissionName: '看板查看' },
      { id: 4, permissionCode: 'finance:dashboard:view', permissionName: '财务看板查看' }
    ]
  )

  const superAdminIds = buildRoleTemplatePermissionIds('SUPER_ADMIN', tree)
  const adminIds = buildRoleTemplatePermissionIds('ADMIN', tree)

  assert.equal(superAdminIds.includes(1), true)
  assert.equal(adminIds.includes(1), false)
  assert.equal(superAdminIds.includes(2), true)
  assert.equal(adminIds.includes(2), false)
  assert.equal(adminIds.includes(3), true)
})

test('template summary exposes confirmation counts used by the role dialog', () => {
  const tree = buildRolePermissionTree(menus, permissions)
  const summary = summarizeTemplateSelection('FINANCE', tree)

  assert.equal(summary.label, '财务主管')
  assert.equal(summary.selected, 3)
  assert.equal(summary.total, 9)
  assert.equal(summary.full, 1)
  assert.equal(summary.readonly, 3)
  assert.equal(summary.none, 23)
})

test('readonly unified space template grants all three view codes without wildcards', () => {
  const tree = buildRolePermissionTree(
    [
      {
        id: 1,
        menuCode: 'asset',
        menuName: '资产管理',
        children: [
          { id: 11, menuCode: 'asset:spaces', menuName: '楼宇空间管理', path: '/assets/spaces' }
        ]
      }
    ],
    [
      { id: 1, permissionCode: 'building:*', permissionName: '楼宇全部权限' },
      { id: 2, permissionCode: 'building:view', permissionName: '查看楼宇' },
      { id: 3, permissionCode: 'floor:*', permissionName: '楼层全部权限' },
      { id: 4, permissionCode: 'floor:view', permissionName: '查看楼层' },
      { id: 5, permissionCode: 'room:*', permissionName: '房间全部权限' },
      { id: 6, permissionCode: 'room:view', permissionName: '查看房间' }
    ]
  )

  assert.deepEqual(buildRoleTemplatePermissionIds('ENGINEER', tree).sort((a, b) => a - b), [2, 4, 6])
})
