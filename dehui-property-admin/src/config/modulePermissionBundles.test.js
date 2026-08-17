import assert from 'node:assert/strict'
import test from 'node:test'

import {
  canReadModule,
  canWriteModule,
  getModulePermissionBundle,
  getModulePermissionBundleForPath,
  modulePermissionBundles,
  modulePermissionSections
} from './modulePermissionBundles.js'

const expectedModuleKeys = [
  'dashboard',
  'asset-spaces',
  'asset-equipment',
  'asset-energy',
  'asset-parking',
  'asset-meetings',
  'investment-manage',
  'investment-leads',
  'investment-announcements',
  'tenant-workspace',
  'operation-workorders',
  'operation-inspections',
  'operation-visitors',
  'staff-mobile-access',
  'staff-mobile-workorders',
  'staff-mobile-inspections',
  'staff-mobile-energy',
  'staff-mobile-meetings',
  'staff-mobile-visitors',
  'staff-mobile-investment',
  'finance-bills',
  'finance-dashboard',
  'finance-bill-delete',
  'ai-daily-report',
  'system-users',
  'system-roles',
  'system-mobile-users'
]

test('module permission registry exactly covers the active secondary menu modules', () => {
  assert.deepEqual(modulePermissionBundles.map(item => item.key), expectedModuleKeys)
  assert.equal(new Set(modulePermissionBundles.map(item => item.key)).size, modulePermissionBundles.length)
  const routedModules = modulePermissionBundles.filter(item => item.route)
  assert.equal(new Set(routedModules.map(item => item.route)).size, routedModules.length)

  const routes = modulePermissionBundles.map(item => item.route)
  ;['/fee-rules', '/billing-plans', '/parking/bills', '/energy/records', '/contracts'].forEach(route => {
    assert.equal(routes.includes(route), false, `${route} must remain a legacy redirect rather than an active module`)
  })

  assert.deepEqual(modulePermissionSections.map(item => item.title), [
    '驾驶舱',
    '资产管理',
    '招商公告',
    '租户管理',
    '运营管理',
    '物业移动工作站',
    '财务管理',
    '敏感财务操作',
    'AI分析',
    '系统管理'
  ])
})

test('read-only modules do not expose a fabricated write bundle', () => {
  const readonlyKeys = modulePermissionBundles
    .filter(item => item.writeCodes.length === 0)
    .map(item => item.key)

  assert.deepEqual(readonlyKeys, [
    'dashboard',
    'staff-mobile-access',
    'finance-dashboard',
    'ai-daily-report'
  ])
})

test('finance dashboard uses the dedicated backend dashboard permission', () => {
  const dashboard = getModulePermissionBundle('finance-dashboard')

  assert.deepEqual(dashboard.readCodes, ['finance:dashboard:view'])
  assert.equal(dashboard.readCodes.includes('finance:bill:view'), false)
})

test('all ordinary module bundles use concrete permission codes', () => {
  modulePermissionBundles.forEach(module => {
    assert.equal(module.readCodes.length > 0, true, `${module.key} requires a read bundle`)
    ;[...module.readCodes, ...module.writeCodes].forEach(code => {
      assert.equal(code === '*' || code.endsWith(':*'), false, `${module.key} must not grant ${code}`)
    })
  })
})

test('sensitive permission boundaries stay outside unrelated write bundles', () => {
  const tenant = getModulePermissionBundle('tenant-workspace')
  const bills = getModulePermissionBundle('finance-bills')
  const billDeletion = getModulePermissionBundle('finance-bill-delete')
  const parking = getModulePermissionBundle('asset-parking')
  const energy = getModulePermissionBundle('asset-energy')

  assert.equal(tenant.writeCodes.includes('bill:add'), false)
  assert.equal(tenant.writeCodes.includes('bill:audit'), false)
  assert.equal(tenant.writeCodes.includes('bill:pay'), false)
  assert.equal(parking.writeCodes.some(code => code.startsWith('parking-bill:')), false)
  assert.equal(energy.writeCodes.some(code => code.startsWith('bill:')), false)
  assert.equal(modulePermissionBundles.some(module => module.writeCodes.includes('custom-field:manage')), false)

  assert.deepEqual(bills.writeCodes, [
    'bill:add',
    'bill:update',
    'bill:void',
    'bill:audit',
    'bill:pay',
    'finance:invoice:upload'
  ])
  assert.equal(bills.writeCodes.includes('bill:delete'), false)
  assert.deepEqual(billDeletion.readCodes, ['bill:delete'])
  assert.deepEqual(billDeletion.writeCodes, ['bill:delete-approved'])
  assert.equal(billDeletion.writeRequiresExact, true)
  assert.equal(billDeletion.readLabel, '草稿删除')
  assert.equal(billDeletion.writeLabel, '已审核删除')
  assert.equal(
    modulePermissionSections.find(section => section.key === 'finance-sensitive')?.permissionOnly,
    true
  )
  assert.equal(
    modulePermissionSections.find(section => section.key === 'finance-sensitive')?.bulkAccess,
    false
  )
})

test('sensitive approved-deletion write state requires an exact grant instead of a bill wildcard', () => {
  const billDeletion = getModulePermissionBundle('finance-bill-delete')

  assert.equal(canWriteModule(billDeletion, ['bill:*']), false)
  assert.equal(canWriteModule(billDeletion, ['bill:*', 'bill:delete-approved']), true)
  assert.equal(canWriteModule(billDeletion, ['*']), false)
})

test('module route lookup and complete bundle checks use all required codes', () => {
  const spaces = getModulePermissionBundleForPath('/assets/spaces')
  const readCodes = ['building:view', 'floor:view', 'room:view']
  const writeCodes = [...readCodes, ...spaces.writeCodes]

  assert.equal(spaces.key, 'asset-spaces')
  assert.equal(getModulePermissionBundleForPath('/assets/spaces/rooms/9').key, 'asset-spaces')
  assert.equal(canReadModule('asset-spaces', ['building:view']), false)
  assert.equal(canReadModule('asset-spaces', readCodes), true)
  assert.equal(canWriteModule('asset-spaces', readCodes), false)
  assert.equal(canWriteModule('asset-spaces', writeCodes), true)
  assert.equal(canReadModule('asset-spaces', ['building:*', 'floor:*', 'room:*']), true)
})

test('property mobile workstation has one explicit entry and isolated secondary permissions', () => {
  const section = modulePermissionSections.find(item => item.key === 'staff-mobile')

  assert.equal(section.permissionOnly, true)
  assert.equal(section.accessModule.readCodes[0], 'staff-mobile:access')
  assert.deepEqual(section.modules.map(item => item.title), [
    '工单管理',
    '巡检管理',
    '能耗管理',
    '会议管理',
    '访客管理',
    '招商看房'
  ])
  section.modules.forEach(module => {
    assert.equal(module.writeRequiresExact, false)
    assert.equal([...module.readCodes, ...module.writeCodes]
      .every(code => code.startsWith('staff-mobile:')), true)
    assert.equal(module.route, undefined)
    assert.equal(module.menuCode, undefined)
  })
  assert.equal(canWriteModule('staff-mobile-workorders', ['staff-mobile:*']), true)
})
