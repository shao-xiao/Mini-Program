import assert from 'node:assert/strict'
import test from 'node:test'
import { computed, reactive, ref } from 'vue'
import { useWorkspaceRows } from './useWorkspaceRows.js'

function createRows() {
  const records = ref([
    { id: 1, name: 'B', status: 'NORMAL' },
    { id: 2, name: 'A', status: 'FAULT' },
    { id: 3, name: 'C', status: 'FAULT' }
  ])
  const fields = computed(() => [
    { key: 'name', label: '名称' },
    { key: 'status', label: '状态' }
  ])
  const rows = useWorkspaceRows({
    records: () => records.value,
    serverTotal: () => 30,
    tablePrefs: reactive({ filterLogic: 'and' }),
    fields,
    fieldAccessors: computed(() => new Map([
      ['name', row => row.name],
      ['status', row => row.status]
    ])),
    localFilters: computed(() => [{ key: 'status', operator: 'equals', value: 'FAULT' }]),
    localSorts: computed(() => [{ key: 'name', order: 'descending' }]),
    localGroups: computed(() => [{ key: 'status', order: 'ascending' }]),
    groupLabel: value => value,
    visibleColumnCount: () => 2
  })
  return { records, rows }
}

test('workspace rows keep loaded, visible and server totals distinct', () => {
  const { rows } = createRows()
  assert.deepEqual(rows.sortedRecords.value.map(row => row.id), [3, 2])
  assert.deepEqual(rows.resultScope.value, { serverTotal: 30, loadedRecords: 3, visibleRecords: 2 })
})

test('workspace rows group with stable keys and preserve display indexes', () => {
  const { rows } = createRows()
  const firstRenderKey = rows.tableRows.value[0].key
  assert.equal(rows.tableRows.value[0].__group, true)
  assert.equal(rows.tableRows.value[0].count, 2)
  assert.equal(rows.tableRows.value[0].key, firstRenderKey)
  assert.equal(rows.recordDisplayIndex(rows.sortedRecords.value[0], { currentPage: 2, pageSize: 20 }), 21)
})

test('server grouping preserves backend order and never filters or re-sorts the loaded page', () => {
  const records = ref([
    { id: 1, building: 'B', status: 'ACTIVE' },
    { id: 2, building: 'B', status: 'PENDING' },
    { id: 3, building: 'A', status: 'ACTIVE' }
  ])
  const rows = useWorkspaceRows({
    records: () => records.value,
    serverTotal: () => 80,
    tablePrefs: reactive({ filterLogic: 'and' }),
    fields: computed(() => [
      { key: 'building', label: '楼宇' },
      { key: 'status', label: '状态' }
    ]),
    fieldAccessors: computed(() => new Map([
      ['building', row => row.building],
      ['status', row => row.status]
    ])),
    localFilters: computed(() => []),
    localSorts: computed(() => []),
    localGroups: computed(() => []),
    serverGroups: computed(() => [{ key: 'building' }, { key: 'status' }]),
    groupLabel: value => value,
    visibleColumnCount: () => 2
  })

  assert.deepEqual(rows.sortedRecords.value.map(row => row.id), [1, 2, 3])
  assert.deepEqual(rows.tableRows.value.filter(row => !row.__group).map(row => row.id), [1, 2, 3])
  assert.deepEqual(rows.tableRows.value.filter(row => row.__group).map(row => row.key), [
    'group:0:"building":["B"]:1',
    'group:1:"status":["B","ACTIVE"]:1',
    'group:1:"status":["B","PENDING"]:2',
    'group:0:"building":["A"]:3',
    'group:1:"status":["A","ACTIVE"]:3'
  ])
  assert.equal(rows.resultScope.value.serverTotal, 80)
  assert.equal(rows.resultScope.value.visibleRecords, 3)
})

test('collapsing a group only hides visual rows and never changes real records or totals', () => {
  const { rows } = createRows()
  const group = rows.tableRows.value.find(row => row.__group)
  rows.toggleGroup(group.key)

  assert.equal(rows.isGroupCollapsed(group.key), true)
  assert.deepEqual(rows.tableRows.value.filter(row => !row.__group), [])
  assert.deepEqual(rows.visibleRecords.value.map(row => row.id), [3, 2])
  assert.deepEqual(rows.resultScope.value, { serverTotal: 30, loadedRecords: 3, visibleRecords: 2 })

  rows.toggleGroup(group.key)
  assert.deepEqual(rows.tableRows.value.filter(row => !row.__group).map(row => row.id), [3, 2])
  rows.clearCollapsedGroups()
  assert.equal(rows.collapsedGroupKeys.value.size, 0)
})
