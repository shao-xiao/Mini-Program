import test from 'node:test'
import assert from 'node:assert/strict'
import { isProtectedSystemRole } from './systemRoleProtection.js'

test('core administrator role codes are protected regardless of role type casing', () => {
  assert.equal(isProtectedSystemRole({ roleCode: 'ADMIN', roleType: 'BUSINESS' }), true)
  assert.equal(isProtectedSystemRole({ code: 'super_admin', role_type: 'business' }), true)
})

test('all SYSTEM roles are protected in the role management UI', () => {
  assert.equal(isProtectedSystemRole({ roleCode: 'PLATFORM_OPERATOR', roleType: 'SYSTEM' }), true)
  assert.equal(isProtectedSystemRole({ code: 'PLATFORM_AUDITOR', role_type: 'system' }), true)
})

test('business roles keep normal status and delete actions', () => {
  assert.equal(isProtectedSystemRole({ roleCode: 'ENGINEER', roleType: 'BUSINESS' }), false)
  assert.equal(isProtectedSystemRole(null), false)
})
