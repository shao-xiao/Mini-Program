import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildRoleMenuSelection,
  nextModuleAccessChange,
  permissionCodesFromRoleAccess,
  permissionIdsForCodes,
  resolveModulePermissionCodes
} from './modulePermissionResolver.js'
import { getModulePermissionBundle } from '../config/modulePermissionBundles.js'

const workorders = getModulePermissionBundle('operation-workorders')
const bills = getModulePermissionBundle('finance-bills')
const billDeletion = getModulePermissionBundle('finance-bill-delete')

test('module write selection includes read and clearing read clears write', () => {
  assert.deepEqual(nextModuleAccessChange({}, 'write', true), { write: true, read: true })
  assert.deepEqual(
    nextModuleAccessChange({ read: true, write: true }, 'read', false),
    { read: false, write: false }
  )
  assert.deepEqual(
    nextModuleAccessChange({ read: true, write: true }, 'write', false),
    { read: true, write: false }
  )
})

test('stable permission codes restore current ids and ignore stale numeric ids', () => {
  const permissions = [
    { id: 101, permissionCode: 'workorder:view', status: 'ENABLED' },
    { id: 102, permissionCode: 'workorder:assign', status: 'ENABLED' },
    { id: 103, permissionCode: 'old:enabled', status: 'ENABLED' },
    { id: 104, permissionCode: 'old:disabled', status: 'DISABLED' }
  ]
  const codes = permissionCodesFromRoleAccess(permissions, {
    permissionIds: [9, 103, 104],
    permissionCodes: ['workorder:view', 'workorder:assign', 'missing:code']
  })

  assert.deepEqual(codes, ['old:enabled', 'workorder:view', 'workorder:assign'])
  assert.deepEqual(permissionIdsForCodes(permissions, codes), [101, 102, 103])
})

test('untouched historical partial write and legacy permissions remain byte-for-byte effective', () => {
  const original = ['workorder:view', 'workorder:assign', 'legacy:report:download']
  const resolved = resolveModulePermissionCodes({
    originalCodes: original,
    changes: {},
    availableCodes: [...workorders.readCodes, ...workorders.writeCodes, 'legacy:report:download']
  })

  assert.deepEqual(resolved, original)
})

test('explicitly enabling write expands to the complete concrete read and write bundle', () => {
  const resolved = resolveModulePermissionCodes({
    originalCodes: ['workorder:view', 'workorder:assign', 'legacy:report:download'],
    changes: { 'operation-workorders': { write: true } },
    availableCodes: [...workorders.readCodes, ...workorders.writeCodes, 'workorder:*', 'legacy:report:download']
  })

  assert.deepEqual(new Set(resolved), new Set([
    ...workorders.readCodes,
    ...workorders.writeCodes,
    'legacy:report:download'
  ]))
  assert.equal(resolved.includes('workorder:*'), false)
})

test('explicitly disabling write removes every module write code but retains read', () => {
  const resolved = resolveModulePermissionCodes({
    originalCodes: [...workorders.readCodes, ...workorders.writeCodes, 'legacy:report:download'],
    changes: { 'operation-workorders': { write: false } },
    availableCodes: [...workorders.readCodes, ...workorders.writeCodes, 'legacy:report:download']
  })

  assert.deepEqual(resolved, ['workorder:view', 'legacy:report:download'])
})

test('disabling read also removes write while unrelated and shared permissions survive', () => {
  const original = [
    ...bills.readCodes,
    ...bills.writeCodes,
    'finance:bill:view',
    'legacy:report:download'
  ]
  const resolved = resolveModulePermissionCodes({
    originalCodes: original,
    changes: { 'finance-bills': { read: false, write: false } },
    availableCodes: original
  })

  assert.deepEqual(resolved, ['finance:bill:view', 'legacy:report:download'])
})

test('sensitive bill deletion uses one dependency-safe two-level authorization bundle', () => {
  const full = resolveModulePermissionCodes({
    originalCodes: [],
    changes: { 'finance-bill-delete': { read: true, write: true } },
    availableCodes: [...billDeletion.readCodes, ...billDeletion.writeCodes]
  })
  assert.deepEqual(full, ['bill:delete', 'bill:delete-approved'])

  const draftOnly = resolveModulePermissionCodes({
    originalCodes: full,
    changes: { 'finance-bill-delete': { read: true, write: false } },
    availableCodes: full
  })
  assert.deepEqual(draftOnly, ['bill:delete'])

  const none = resolveModulePermissionCodes({
    originalCodes: full,
    changes: { 'finance-bill-delete': { read: false, write: false } },
    availableCodes: full
  })
  assert.deepEqual(none, [])
})

test('touching a module converts a covering wildcard without losing other known domain permissions', () => {
  const resolved = resolveModulePermissionCodes({
    originalCodes: ['workorder:*'],
    changes: { 'operation-workorders': { write: false } },
    availableCodes: [
      'workorder:*',
      ...workorders.readCodes,
      ...workorders.writeCodes,
      'workorder:legacy-export'
    ]
  })

  assert.deepEqual(resolved, ['workorder:view', 'workorder:legacy-export'])
  assert.equal(resolved.includes('workorder:*'), false)
})

test('ordinary bill changes never expand a historical wildcard into sensitive approved-deletion access', () => {
  const resolved = resolveModulePermissionCodes({
    originalCodes: ['bill:*'],
    changes: { 'finance-bills': { write: false } },
    availableCodes: [
      'bill:*',
      ...bills.readCodes,
      ...bills.writeCodes,
      ...billDeletion.readCodes,
      ...billDeletion.writeCodes
    ]
  })

  assert.deepEqual(resolved, ['bill:view', 'bill:delete'])
  assert.equal(resolved.includes('bill:delete-approved'), false)
})

test('menu selection only includes fully readable active modules and preserves outside active menus', () => {
  const menus = [
    {
      id: 1,
      code: 'operation',
      status: 'ENABLED',
      children: [{ id: 2, code: 'operation:workorder', path: '/workorders', status: 'ENABLED' }]
    },
    { id: 9, code: 'legacy:reports', path: '/legacy/reports', status: 'ENABLED' },
    { id: 10, code: 'legacy:disabled', path: '/legacy/disabled', status: 'DISABLED' }
  ]
  const selection = buildRoleMenuSelection({
    menus,
    permissionCodes: ['workorder:view'],
    originalMenuCodes: ['legacy:reports', 'legacy:disabled']
  })

  assert.deepEqual(selection.menuCodes, ['legacy:reports', 'operation', 'operation:workorder'])
  assert.deepEqual(selection.menuIds, [9, 1, 2])
})
