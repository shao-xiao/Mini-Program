import test from 'node:test'
import assert from 'node:assert/strict'

import { formatPermissionLabel } from './permissionLabels.js'
import { pathPermissionRules } from './permissionRules.js'

const englishSeedNames = {
  'dashboard:view': 'View Dashboard',
  'energy:update': 'Update Energy Price',
  'energy:meter:view': 'View Energy Meter',
  'energy:meter:create': 'Create Energy Meter',
  'energy:meter:update': 'Update Energy Meter',
  'energy:meter:delete': 'Delete Energy Meter',
  'energy:reading:update': 'Update Energy Reading',
  'energy:reading:delete': 'Delete Energy Reading',
  'inspection:create': 'Create Inspection',
  'inspection:update': 'Update Inspection',
  'mobile:update': 'Update Mini Program User',
  'parking-bill:update': 'Update Parking Bill'
}

test('known English seeded permission names render as Chinese labels', () => {
  assert.equal(formatPermissionLabel({ permissionCode: 'energy:meter:view', permissionName: 'View Energy Meter' }), '查看表具')
  assert.equal(formatPermissionLabel({ permissionCode: 'energy:meter:create', permissionName: 'Create Energy Meter' }), '新增表具')
  assert.equal(formatPermissionLabel({ permissionCode: 'energy:meter:update', permissionName: 'Update Energy Meter' }), '编辑表具')
  assert.equal(formatPermissionLabel({ permissionCode: 'energy:meter:delete', permissionName: 'Delete Energy Meter' }), '删除表具')
  assert.equal(formatPermissionLabel({ permissionCode: 'parking-bill:update', permissionName: 'Update Parking Bill' }), '更新停车账单')
})

test('permission tree display labels do not use English CRUD fallback names', () => {
  const permissions = pathPermissionRules.flatMap(rule => rule.codes).map((code, index) => ({
    id: index + 1,
    permissionCode: code,
    permissionName: englishSeedNames[code] || code
  }))

  const labels = permissions.map(formatPermissionLabel)
  const englishCrudLabels = labels.filter(label => /^(View|Create|Update|Delete)\s+\w+/i.test(label))

  assert.deepEqual(englishCrudLabels, [])
})
