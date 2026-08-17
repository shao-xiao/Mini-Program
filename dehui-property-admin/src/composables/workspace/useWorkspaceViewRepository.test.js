import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeTableViewPrefs } from '../../utils/tableViewPrefs.js'
import { useWorkspaceViewRepository } from './useWorkspaceViewRepository.js'

function createMemoryStorage() {
  const values = new Map()
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value))
  }
}

function createRepository(storage, username = 'tech', pageKey = '/equipment') {
  const columns = [{ key: 'name' }, { key: 'status' }]
  const defaultPrefs = {
    visibleColumns: ['name', 'status'],
    columnOrder: ['name', 'status'],
    filters: [],
    customColumns: []
  }
  const availableKeys = (customColumns, prefs = {}) => [
    ...columns.map(column => column.key),
    ...(prefs.visibleColumns || []).filter(key => String(key).startsWith('custom_')),
    ...(prefs.columnOrder || []).filter(key => String(key).startsWith('custom_')),
    ...(customColumns || []).map(column => column.key)
  ]
  const normalizePrefs = prefs => normalizeTableViewPrefs(
    prefs,
    defaultPrefs,
    availableKeys(prefs?.customColumns, prefs)
  )

  return useWorkspaceViewRepository({
    storageKey: `legacy:${pageKey}`,
    pageKey,
    username,
    columns,
    defaultPrefs,
    initialViews: [{ id: 'fault', name: '故障设备', system: true, prefs: { filters: [] } }],
    defaultViewName: '全部设备',
    normalizePrefs,
    availableKeys,
    storage
  })
}

test('view repository isolates persisted workspaces by username and page', () => {
  const storage = createMemoryStorage()
  const techEquipment = createRepository(storage, 'tech', '/equipment')
  techEquipment.createView('我的设备', techEquipment.persistedPrefs.value)

  assert.equal(createRepository(storage, 'tech', '/equipment').views.value.length, 3)
  assert.equal(createRepository(storage, 'admin', '/equipment').views.value.length, 2)
  assert.equal(createRepository(storage, 'tech', '/workorders').views.value.length, 2)
})

test('repository create, copy, rename and delete keep isolated preference objects', () => {
  const repository = createRepository(createMemoryStorage())
  const created = repository.createView('我的设备', {
    ...repository.persistedPrefs.value,
    filters: [{ key: 'status', operator: 'equals', value: ['FAULT'] }]
  })
  const copied = repository.copyView(created.id, '领导查看')

  copied.prefs.filters[0].value.push('NORMAL')
  assert.deepEqual(created.prefs.filters[0].value, ['FAULT'])
  assert.equal(repository.renameView(copied.id, '领导视图'), true)
  assert.equal(repository.deleteView(copied.id), true)
  assert.equal(repository.views.value.some(view => view.id === copied.id), false)
})

test('repository tolerates invalid local data and restores system presets', () => {
  const storage = createMemoryStorage()
  storage.setItem('dhic-data-workspace:v2:tech:%2Fequipment', '{invalid-json')
  const repository = createRepository(storage)

  assert.deepEqual(repository.views.value.map(view => view.id), ['default', 'fault'])
  assert.equal(repository.activeViewId.value, 'default')
})

test('repository preserves persisted platform field layout before definitions load', () => {
  const storage = createMemoryStorage()
  const repository = createRepository(storage)
  repository.persistActivePrefs({
    ...repository.persistedPrefs.value,
    visibleColumns: ['name', 'status', 'custom_acceptance_note'],
    columnOrder: ['name', 'status', 'custom_acceptance_note']
  })

  const reloaded = createRepository(storage)
  assert.deepEqual(
    reloaded.persistedPrefs.value.visibleColumns,
    ['name', 'status', 'custom_acceptance_note']
  )
  assert.deepEqual(
    reloaded.persistedPrefs.value.columnOrder,
    ['name', 'status', 'custom_acceptance_note']
  )
})
