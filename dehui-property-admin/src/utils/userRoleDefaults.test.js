import test from 'node:test'
import assert from 'node:assert/strict'
import {
  defaultRoleCodeForUserType,
  defaultRoleIdsForUserType,
  roleCodeOf
} from './userRoleDefaults.js'

const roles = [
  { id: 1, roleCode: 'ADMIN', roleName: '管理员' },
  { id: 2, roleCode: 'PROPERTY_MANAGER', roleName: '物业运营' },
  { id: 3, roleCode: 'FINANCE', roleName: '财务主管' },
  { id: 4, roleCode: 'ENGINEER', roleName: '工程部员工' },
  { id: 5, roleCode: 'TENANT', roleName: '租户账号' },
  { id: 6, roleCode: 'STAFF', roleName: '物业员工' },
  { id: 7, roleCode: 'SECURITY', roleName: '安保员工' }
]
test('roleCodeOf supports frontend and backend role code aliases', () => {
  assert.equal(roleCodeOf({ roleCode: 'SECURITY' }), 'SECURITY')
  assert.equal(roleCodeOf({ code: 'FINANCE' }), 'FINANCE')
})

test('defaultRoleCodeForUserType maps user type to real RBAC role code', () => {
  assert.equal(defaultRoleCodeForUserType('ADMIN'), 'ADMIN')
  assert.equal(defaultRoleCodeForUserType('MANAGER'), 'PROPERTY_MANAGER')
  assert.equal(defaultRoleCodeForUserType('FINANCE'), 'FINANCE')
  assert.equal(defaultRoleCodeForUserType('ENGINEER'), 'ENGINEER')
  assert.equal(defaultRoleCodeForUserType('SECURITY'), 'SECURITY')
  assert.equal(defaultRoleCodeForUserType('TENANT'), 'TENANT')
  assert.equal(defaultRoleCodeForUserType('STAFF'), 'STAFF')
  assert.equal(defaultRoleCodeForUserType('CLEANER'), 'STAFF')
})

test('defaultRoleIdsForUserType returns selectable role id for user creation', () => {
  assert.deepEqual(defaultRoleIdsForUserType('SECURITY', roles), [7])
  assert.deepEqual(defaultRoleIdsForUserType('MANAGER', roles), [2])
  assert.deepEqual(defaultRoleIdsForUserType('CLEANER', roles), [6])
  assert.deepEqual(defaultRoleIdsForUserType('UNKNOWN', roles), [])
})
