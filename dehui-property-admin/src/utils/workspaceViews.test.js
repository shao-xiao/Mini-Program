import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildWorkspaceStorageKey,
  createWorkspaceView,
  deleteWorkspaceView,
  duplicateWorkspaceView,
  normalizeWorkspaceState,
  renameWorkspaceView
} from './workspaceViews.js'

const defaultPrefs = {
  visibleColumns: ['name', 'status'],
  columnOrder: ['name', 'status'],
  rowDensity: 'standard',
  filterLogic: 'and',
  filters: [],
  groups: [],
  sorts: []
}

test('workspace storage is isolated by username and page route', () => {
  assert.notEqual(
    buildWorkspaceStorageKey('tech', '/equipment'),
    buildWorkspaceStorageKey('admin', '/equipment')
  )
  assert.notEqual(
    buildWorkspaceStorageKey('tech', '/equipment'),
    buildWorkspaceStorageKey('tech', '/workorders')
  )
})

test('normalizeWorkspaceState creates default and preset views from legacy preferences', () => {
  const state = normalizeWorkspaceState({}, {
    defaultPrefs,
    availableColumnKeys: ['name', 'status'],
    legacyPrefs: { ...defaultPrefs, rowDensity: 'compact' },
    initialViews: [
      {
        id: 'fault',
        name: '故障设备',
        prefs: { filters: [{ key: 'status', operator: 'equals', value: 'FAULT' }] }
      }
    ]
  })

  assert.equal(state.activeViewId, 'default')
  assert.equal(state.views[0].prefs.rowDensity, 'compact')
  assert.deepEqual(state.views[1].prefs.filters, [{ key: 'status', operator: 'equals', value: 'FAULT' }])
})

test('workspace views can be created, renamed and deleted without deleting the default view', () => {
  const state = normalizeWorkspaceState({}, {
    defaultPrefs,
    availableColumnKeys: ['name', 'status']
  })
  const view = createWorkspaceView(state, '维修设备', {
    ...defaultPrefs,
    filters: [{ key: 'status', operator: 'equals', value: 'MAINTENANCE' }]
  }, ['name', 'status'])

  assert.equal(state.activeViewId, view.id)
  assert.equal(renameWorkspaceView(state, view.id, '维护中设备'), true)
  assert.equal(state.views.find(item => item.id === view.id)?.name, '维护中设备')
  assert.equal(deleteWorkspaceView(state, view.id), true)
  assert.equal(state.activeViewId, 'default')
  assert.equal(deleteWorkspaceView(state, 'default'), false)
})

test('saved workspaces receive missing system presets without replacing saved preferences', () => {
  const state = normalizeWorkspaceState({
    activeViewId: 'default',
    views: [
      {
        id: 'default',
        name: '默认视图',
        system: true,
        prefs: { ...defaultPrefs, rowDensity: 'compact' }
      }
    ]
  }, {
    defaultPrefs,
    availableColumnKeys: ['name', 'status'],
    initialViews: [
      {
        id: 'maintenance',
        name: '维修设备',
        system: true,
        prefs: { filters: [{ key: 'status', operator: 'equals', value: 'MAINTENANCE' }] }
      }
    ]
  })

  assert.equal(state.views[0].prefs.rowDensity, 'compact')
  assert.equal(state.views[1].name, '维修设备')
  assert.equal(state.views[1].system, true)
  assert.deepEqual(state.views[1].resetPrefs.filters, [
    { key: 'status', operator: 'equals', value: 'MAINTENANCE' }
  ])
})

test('workspace can rename the default view and duplicate a complete view state', () => {
  const state = normalizeWorkspaceState({}, {
    defaultPrefs: { ...defaultPrefs, rowDensity: 'compact' },
    availableColumnKeys: ['name', 'status'],
    defaultViewName: '全部设备'
  })
  state.views[0].prefs.filters = [{ key: 'status', operator: 'equals', value: 'FAULT' }]
  state.views[0].prefs.columnWidths = { name: 220 }

  const duplicate = duplicateWorkspaceView(state, 'default', '故障设备', ['name', 'status'])

  assert.equal(state.views[0].name, '全部设备')
  assert.equal(duplicate.name, '故障设备')
  assert.equal(duplicate.prefs.rowDensity, 'compact')
  assert.deepEqual(duplicate.prefs.filters, [{ key: 'status', operator: 'equals', value: 'FAULT' }])
  assert.deepEqual(duplicate.prefs.columnWidths, { name: 220 })
})

test('duplicated view names receive a stable numeric suffix', () => {
  const state = normalizeWorkspaceState({}, {
    defaultPrefs,
    availableColumnKeys: ['name', 'status']
  })
  duplicateWorkspaceView(state, 'default', '默认视图 副本', ['name', 'status'])
  const second = duplicateWorkspaceView(state, 'default', '默认视图 副本', ['name', 'status'])

  assert.equal(second.name, '默认视图 副本 2')
})

test('duplicateWorkspaceView can copy the current unsaved draft without changing its source view', () => {
  const state = normalizeWorkspaceState({}, {
    defaultPrefs,
    availableColumnKeys: ['name', 'status']
  })
  const savedPrefs = structuredClone(state.views[0].prefs)
  const draftPrefs = {
    ...savedPrefs,
    rowDensity: 'compact',
    filterLogic: 'or',
    filters: [{ key: 'status', operator: 'equals', value: 'FAULT' }]
  }

  const duplicate = duplicateWorkspaceView(state, 'default', '故障草稿', ['name', 'status'], draftPrefs)

  assert.equal(state.views[0].prefs.rowDensity, savedPrefs.rowDensity)
  assert.deepEqual(state.views[0].prefs.filters, savedPrefs.filters)
  assert.equal(duplicate.prefs.rowDensity, 'compact')
  assert.equal(duplicate.prefs.filterLogic, 'or')
  assert.deepEqual(duplicate.prefs.filters, draftPrefs.filters)
})
