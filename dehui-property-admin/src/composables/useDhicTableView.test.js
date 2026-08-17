import assert from 'node:assert/strict'
import test from 'node:test'
import { reactive, ref } from 'vue'
import { useDhicTableView } from './useDhicTableView.js'
import { createWorkspaceCustomFieldRepository } from './workspace/useWorkspaceCustomFields.js'

function createMemoryStorage() {
  const values = new Map()
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null
    },
    setItem(key, value) {
      values.set(key, String(value))
    },
    removeItem(key) {
      values.delete(key)
    },
    clear() {
      values.clear()
    }
  }
}

function createTableView(pageKey) {
  return useDhicTableView({
    storageKey: `legacy:${pageKey}`,
    pageKey,
    username: 'workspace-test-user',
    columns: [
      { key: 'name', label: '名称', type: 'text', sortable: true },
      { key: 'status', label: '状态', type: 'select', sortable: true, options: ['NORMAL', 'FAULT'] }
    ],
    records: () => [],
    explicitSave: true,
    defaultPrefs: {
      visibleColumns: ['name', 'status'],
      columnOrder: ['name', 'status'],
      columnWidths: { name: 160 },
      filters: [{ key: 'status', operator: 'equals', value: ['NORMAL'] }]
    },
    initialViews: [
      {
        id: 'fault',
        name: '故障视图',
        system: true,
        prefs: { filters: [{ key: 'status', operator: 'equals', value: ['FAULT'] }] }
      }
    ]
  })
}

test.beforeEach(() => {
  globalThis.localStorage = createMemoryStorage()
})

test.afterEach(() => {
  delete globalThis.localStorage
})

test('draft changes do not mutate the persisted view and discard restores nested preferences', () => {
  const view = createTableView('/workspace-draft-isolation')

  assert.notStrictEqual(view.persistedPrefs.value, view.draftPrefs)
  assert.notStrictEqual(view.persistedPrefs.value.filters, view.draftPrefs.filters)

  view.tablePrefs.filters[0].value.push('FAULT')
  view.tablePrefs.columnOrder.reverse()
  view.tablePrefs.columnWidths.name = 260

  assert.equal(view.isViewDirty.value, true)
  view.discardCurrentViewChanges()

  assert.deepEqual(view.tablePrefs.filters, [
    { key: 'status', operator: 'equals', value: ['NORMAL'] }
  ])
  assert.deepEqual(view.tablePrefs.columnOrder, ['name', 'status'])
  assert.deepEqual(view.tablePrefs.columnWidths, { name: 160 })
})

test('save commits an isolated snapshot and later draft changes can still be discarded', () => {
  const view = createTableView('/workspace-save-isolation')

  view.updateFilters([{ key: 'status', operator: 'equals', value: ['FAULT'] }])
  view.updateColumnWidth('name', 240)
  view.saveCurrentView()

  view.tablePrefs.filters[0].value.push('NORMAL')
  view.tablePrefs.columnWidths.name = 320
  view.discardCurrentViewChanges()

  assert.deepEqual(view.tablePrefs.filters, [
    { key: 'status', operator: 'equals', value: ['FAULT'] }
  ])
  assert.deepEqual(view.tablePrefs.columnWidths, { name: 240 })
})

test('discarding view A before switching prevents its unsaved draft from leaking back', () => {
  const view = createTableView('/workspace-switch-isolation')

  view.tablePrefs.filters[0].value.push('FAULT')
  view.discardCurrentViewChanges()
  view.switchTableView('fault')
  view.switchTableView('default')

  assert.deepEqual(view.tablePrefs.filters, [
    { key: 'status', operator: 'equals', value: ['NORMAL'] }
  ])
})

test('duplicated views start equal but do not share nested preference references', () => {
  const view = createTableView('/workspace-duplicate-isolation')
  const duplicateId = view.duplicateTableView('default', '默认视图副本')

  view.tablePrefs.filters[0].value.push('FAULT')
  view.saveCurrentView()
  view.switchTableView('default')

  assert.deepEqual(view.tablePrefs.filters, [
    { key: 'status', operator: 'equals', value: ['NORMAL'] }
  ])

  view.switchTableView(duplicateId)
  assert.deepEqual(view.tablePrefs.filters, [
    { key: 'status', operator: 'equals', value: ['NORMAL', 'FAULT'] }
  ])
})

test('server-paginated views expose server params without filtering or sorting the loaded page', () => {
  const records = [
    { id: 1, name: 'A', status: 'NORMAL' },
    { id: 2, name: 'B', status: 'FAULT' }
  ]
  const view = useDhicTableView({
    storageKey: 'legacy:/workspace-server-query',
    pageKey: '/workspace-server-query',
    username: 'workspace-test-user',
    columns: [
      { key: 'name', label: '名称', sortable: true },
      { key: 'status', label: '状态', query: { mode: 'server', param: 'status', operators: ['equals'] } }
    ],
    records: () => records,
    serverTotal: () => 500,
    query: { dataMode: 'server', filterLogics: ['and'] },
    defaultPrefs: {
      filters: [{ key: 'status', operator: 'equals', value: 'NORMAL' }],
      sorts: [{ key: 'name', order: 'ascending' }]
    }
  })

  assert.deepEqual(view.serverQueryParams.value, { status: 'NORMAL' })
  assert.deepEqual(view.tableRows.value, records)
  assert.equal(view.isColumnSortable('name'), false)
  assert.deepEqual(view.resultScope.value, { serverTotal: 500, loadedRecords: 2, visibleRecords: 2 })
  assert.match(view.queryNotices.value.join(' '), /排序条件当前接口不支持/)

  view.updateFilters([{ key: 'name', operator: 'contains', value: 'A' }])
  assert.deepEqual(view.tableRows.value, records)
  assert.deepEqual(view.serverQueryParams.value, {})
  assert.match(view.queryNotices.value.join(' '), /筛选条件当前接口不支持/)
})

test('facade persists page size through draft save and discard semantics', () => {
  const view = createTableView('/workspace-page-size')

  assert.equal(view.tablePrefs.pageSize, 20)
  view.updatePageSize(50)
  assert.equal(view.tablePrefs.pageSize, 50)
  assert.equal(view.isViewDirty.value, true)

  view.discardCurrentViewChanges()
  assert.equal(view.tablePrefs.pageSize, 20)

  view.updatePageSize(100)
  view.saveCurrentView()
  view.updatePageSize(10)
  view.discardCurrentViewChanges()
  assert.equal(view.tablePrefs.pageSize, 100)
})

test('facade delegates server query and visual grouping without changing backend row order', () => {
  const records = ref([
    { id: 1, status: 'PENDING', name: 'B' },
    { id: 2, status: 'PENDING', name: 'A' },
    { id: 3, status: 'DONE', name: 'C' }
  ])
  const queryState = reactive({ page: 2, pageSize: 25, keyword: '检修' })
  const view = useDhicTableView({
    storageKey: 'legacy:/workspace-server-grouping',
    pageKey: '/workspace-server-grouping',
    username: 'workspace-test-user',
    columns: [
      {
        key: 'status',
        label: '状态',
        groupable: true,
        query: { group: { mode: 'server', param: 'status' } }
      },
      {
        key: 'name',
        label: '名称',
        sortable: true,
        query: { sort: { mode: 'server', param: 'name' } }
      }
    ],
    records: () => records.value,
    serverTotal: () => 60,
    query: { dataMode: 'server', filterLogics: ['and'] },
    queryState,
    defaultPrefs: {
      groups: [{ key: 'status', order: 'ascending' }],
      sorts: [{ key: 'name', order: 'descending' }]
    }
  })

  assert.deepEqual(view.serverQueryParams.value, {
    groupBy: 'status:asc',
    keyword: '检修',
    page: 2,
    pageSize: 25,
    sorts: 'status:asc,name:desc'
  })
  assert.deepEqual(view.queryPlan.value.groupBy, [{ key: 'status', order: 'ascending' }])
  assert.deepEqual(view.queryPlan.value.effectiveSorts, [
    { key: 'status', order: 'ascending' },
    { key: 'name', order: 'descending' }
  ])
  assert.deepEqual(view.tableRows.value.filter(row => !row.__group).map(row => row.id), [1, 2, 3])
  assert.equal(view.groupedRows.value.filter(row => row.__group).length, 2)
  assert.deepEqual(view.resultScope.value, { serverTotal: 60, loadedRecords: 3, visibleRecords: 3 })
})

test('facade batch-hydrates complete client datasets once and exposes custom fields to rows', async () => {
  const rows = ref([{ id: 1, name: 'A' }, { id: 2, name: 'B' }])
  const reads = []
  const definitionClient = {
    list: async () => [{
      id: 11,
      fieldCode: 'custom_level',
      fieldName: 'Level',
      fieldType: 'TEXT',
      status: 'ENABLED',
      version: 1
    }]
  }
  const valueClient = {
    read: async (workspaceKey, ids) => {
      reads.push([workspaceKey, ids])
      return { 1: { custom_level: 'A' }, 2: { custom_level: 'B' } }
    },
    save: async () => {},
    batchSave: async () => []
  }
  const view = useDhicTableView({
    storageKey: 'client-custom-fields',
    pageKey: '/client-custom-fields',
    username: 'workspace-test-user',
    columns: [{ key: 'name', label: 'Name' }],
    records: () => rows.value,
    query: { dataMode: 'client' },
    customFields: {
      enabled: true,
      workspaceKey: 'tenants',
      valueSource: 'batch',
      valueUpdatePermission: 'tenant:update',
      permissionCheck: () => true,
      repository: createWorkspaceCustomFieldRepository(definitionClient),
      client: valueClient
    }
  })

  await waitFor(() => reads.length === 1 && view.tableRows.value[0]?.customFields?.custom_level === 'A')

  assert.deepEqual(reads, [['tenants', [1, 2]]])
  assert.deepEqual(view.tableRows.value.map(row => row.customFields.custom_level), ['A', 'B'])
  assert.equal(rows.value[0].customFields, undefined)
  assert.ok(view.visibleOrderedColumns.value.every(field => field.key !== 'custom_level'))
})

test('facade never batch-loads server-projected custom values', async () => {
  let readCount = 0
  const rows = ref([{ id: 1, name: 'A', customFields: { custom_level: 'A' } }])
  const view = useDhicTableView({
    storageKey: 'server-custom-fields',
    pageKey: '/server-custom-fields',
    username: 'workspace-test-user',
    columns: [{ key: 'name', label: 'Name' }],
    records: () => rows.value,
    query: { dataMode: 'server' },
    customFields: {
      enabled: true,
      workspaceKey: 'rooms',
      valueSource: 'projected',
      valueUpdatePermission: 'room:update',
      permissionCheck: () => true,
      repository: createWorkspaceCustomFieldRepository({ list: async () => [] }),
      client: { read: async () => { readCount += 1; return {} }, save: async () => {} }
    }
  })

  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(readCount, 0)
  assert.equal(view.tableRows.value[0].customFields.custom_level, 'A')
})

async function waitFor(predicate, timeout = 500) {
  const startedAt = Date.now()
  while (!predicate()) {
    if (Date.now() - startedAt > timeout) throw new Error('Timed out waiting for workspace state')
    await new Promise(resolve => setTimeout(resolve, 0))
  }
}
