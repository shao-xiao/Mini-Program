import assert from 'node:assert/strict'
import test from 'node:test'
import { createEquipmentWorkspaceConfig } from './equipment.js'
import { createWorkOrderWorkspaceConfig } from './workorder.js'
import {
  defineWorkspaceConfig,
  permittedWorkspaceActions,
  workspaceAction,
  workspacePermission,
  workspaceTableOptions
} from './workspaceConfig.js'

test('workspace config rejects incomplete and duplicate definitions', () => {
  assert.throws(() => defineWorkspaceConfig({}), /requires id/)
  assert.throws(() => defineWorkspaceConfig({
    id: 'duplicate-fields',
    title: 'Duplicate fields',
    pageKey: '/duplicate-fields',
    storageKey: 'duplicate-fields',
    defaultViewName: 'Default',
    fields: [{ key: 'name' }, { key: 'name' }]
  }), /duplicate field keys/)
  assert.throws(() => defineWorkspaceConfig({
    id: 'duplicate-actions',
    title: 'Duplicate actions',
    pageKey: '/duplicate-actions',
    storageKey: 'duplicate-actions',
    defaultViewName: 'Default',
    importExport: [{ id: 'export' }, { id: 'export' }]
  }), /duplicate importExport ids/)
  assert.throws(() => defineWorkspaceConfig({
    id: 'invalid-server-query',
    title: 'Invalid server query',
    pageKey: '/invalid-server-query',
    storageKey: 'invalid-server-query',
    defaultViewName: 'Default',
    query: { dataMode: 'server' },
    fields: [{ key: 'name', query: { mode: 'server', operators: ['equals'] } }]
  }), /requires param or serialize/)
})

test('equipment config owns workspace fields, views, metrics, actions and permissions', () => {
  const config = createEquipmentWorkspaceConfig()
  const options = workspaceTableOptions(config, { records: () => [] })

  assert.equal(config.id, 'equipment')
  assert.equal(config.pageKey, '/equipment')
  assert.equal(workspacePermission(config, 'update'), 'asset:update')
  assert.ok(config.fields.some(field => field.key === 'equipmentName'))
  assert.ok(config.fields.some(field => field.key === 'custom_quantity'))
  assert.deepEqual(config.views.map(view => view.id), ['maintenance-equipment', 'fault-equipment'])
  assert.ok(config.metrics.some(metric => metric.field === 'fault'))
  assert.deepEqual(config.importExport.map(action => action.id), ['import', 'export-current', 'export-template'])
  assert.deepEqual(config.batchActions.map(action => action.id), ['edit', 'export', 'delete'])
  assert.equal(workspaceAction(config, 'export-template', 'importExport')?.permission, 'exportTemplate')
  assert.equal(options.columns, config.fields)
  assert.equal(options.initialViews, config.views)
  assert.equal(options.storageKey, config.storageKey)
  assert.equal(options.query, config.query)
  assert.equal(config.query.dataMode, 'server')
})

test('workorder config isolates current-user views and filters actions by permission code', () => {
  const techConfig = createWorkOrderWorkspaceConfig({ currentUserId: 7 })
  const adminConfig = createWorkOrderWorkspaceConfig({ currentUserId: 1 })
  const techView = techConfig.views.find(view => view.id === 'my-workorders')
  const adminView = adminConfig.views.find(view => view.id === 'my-workorders')

  assert.equal(techView.prefs.filters[0].value, 7)
  assert.equal(adminView.prefs.filters[0].value, 1)
  assert.ok(techConfig.fields.some(field => field.key === 'status' && field.editable))
  assert.ok(techConfig.statusTransitions.some(status => status.value === 'COMPLETED'))
  assert.deepEqual(
    permittedWorkspaceActions(
      techConfig,
      techConfig.batchActions,
      code => ['workorder:view', 'workorder:assign'].includes(code)
    ).map(action => action.id),
    ['assign', 'export']
  )
})

test('workspace config keeps shared custom field definitions separate from view preferences', () => {
  const config = defineWorkspaceConfig({
    id: 'equipment',
    title: '设备台账',
    pageKey: '/equipment',
    storageKey: 'equipment',
    defaultViewName: '全部设备',
    query: { dataMode: 'server' },
    permissions: { view: 'asset:view', update: 'asset:update' },
    customFields: {
      enabled: true,
      workspaceKey: 'equipment',
      managePermission: 'custom-field:manage',
      valueUpdatePermission: 'asset:update'
    },
    fields: [{ key: 'name', label: '名称' }]
  })

  assert.deepEqual(config.customFields, {
    enabled: true,
    workspaceKey: 'equipment',
    managePermission: 'custom-field:manage',
    valueUpdatePermission: 'asset:update',
    entityType: 'equipment',
    viewPermission: 'asset:view',
    queryMode: 'server',
    valueSource: 'projected',
    maximumCustomFields: 50
  })
  assert.equal(Object.hasOwn(config.table.defaultPrefs, 'customFieldDefinitions'), false)
})

test('workspace config rejects enabled custom fields without a stable workspace key', () => {
  assert.throws(() => defineWorkspaceConfig({
    id: 'invalid-custom-fields',
    title: '错误配置',
    pageKey: '/invalid',
    storageKey: 'invalid',
    defaultViewName: '默认视图',
    customFields: { enabled: true, workspaceKey: 'INVALID KEY' }
  }), /custom field workspaceKey/)
})
