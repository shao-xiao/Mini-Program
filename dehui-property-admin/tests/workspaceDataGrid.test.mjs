import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  buildWorkspaceDisplayIndexMap,
  continuousWorkspaceIndex,
  normalizeWorkspaceGridColumns,
  reconcileWorkspaceSelectionKeys,
  workspaceGridDensityClass,
  workspaceGridEmptyState,
  workspaceGridGroupSpan,
  workspaceGridRowKey,
  workspaceGridSelectable,
  workspaceGridSortIntent
} from '../src/utils/workspaceDataGrid.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('grid columns safely ignore invalid, filter-only, and duplicate definitions', () => {
  const name = { key: 'name', label: '名称' }
  const columns = normalizeWorkspaceGridColumns([
    name,
    null,
    { label: '无 key' },
    { key: 'filter', filterOnly: true },
    { key: 'name', label: '重复名称' },
    { key: 'status', label: '状态' }
  ])

  assert.deepEqual(columns, [name, { key: 'status', label: '状态' }])
})

test('grid row keys use stable business keys and isolate group keys', () => {
  assert.equal(workspaceGridRowKey({ id: 7 }), 7)
  assert.equal(workspaceGridRowKey({ code: 'EQ-001' }, 'code'), 'EQ-001')
  assert.equal(workspaceGridRowKey({ id: 7 }, row => `equipment:${row.id}`), 'equipment:7')
  assert.equal(workspaceGridRowKey({ __group: true, key: 'FAULT' }), '__workspace_group__:FAULT')
  assert.equal(workspaceGridRowKey({ name: 'missing' }), null)
})

test('selection rejects group rows and reconciles by stable keys after refresh', () => {
  const rows = [
    { __group: true, key: 'NORMAL' },
    { id: 1, version: 2 },
    { id: 2, version: 1 }
  ]
  assert.equal(workspaceGridSelectable(rows[0]), false)
  assert.equal(workspaceGridSelectable(rows[1]), true)
  assert.equal(workspaceGridSelectable(rows[2], row => row.id !== 2), false)
  assert.deepEqual(reconcileWorkspaceSelectionKeys(rows, [1, 3, 1]), [1])
})

test('continuous index preserves the current paging convention', () => {
  assert.equal(continuousWorkspaceIndex(0), 1)
  assert.equal(continuousWorkspaceIndex(0, 3, 20), 41)
  assert.equal(continuousWorkspaceIndex(4, 3, 20), 45)
})

test('display index map skips group rows and resolves every business row in constant time', () => {
  const rows = [
    { __group: true, key: 'FAULT' },
    { id: 10 },
    { id: 11 },
    { __group: true, key: 'NORMAL' },
    { id: 12 }
  ]
  const indexes = buildWorkspaceDisplayIndexMap(rows, 'id', 3, 20)

  assert.deepEqual([...indexes.entries()], [[10, 41], [11, 42], [12, 43]])
  assert.equal(indexes.has('__workspace_group__:FAULT'), false)
})

test('group rows span from the group column through all remaining columns', () => {
  const row = { __group: true, key: 'FAULT' }
  assert.deepEqual(workspaceGridGroupSpan({ row, columnIndex: 1, columnCount: 8, groupColumnIndex: 1 }), [1, 7])
  assert.deepEqual(workspaceGridGroupSpan({ row, columnIndex: 2, columnCount: 8, groupColumnIndex: 1 }), [0, 0])
  assert.equal(workspaceGridGroupSpan({ row: { id: 1 }, columnIndex: 1, columnCount: 8, groupColumnIndex: 1 }), undefined)
})

test('sort intent reports field, direction, and declared query scope', () => {
  const columns = [
    { key: 'name', sortable: true, query: { sort: { mode: 'server', param: 'sortBy' } } },
    { key: 'status', sortable: true, query: { sort: 'page' } }
  ]
  assert.deepEqual(
    workspaceGridSortIntent({ prop: 'name', order: 'ascending' }, columns, 'server'),
    { field: 'name', prop: 'name', direction: 'ascending', order: 'ascending', scope: 'server' }
  )
  assert.equal(workspaceGridSortIntent({ prop: 'status', order: 'descending' }, columns, 'server').scope, 'page')
  assert.equal(workspaceGridSortIntent({ prop: 'missing', order: 'ascending' }, columns, 'server').scope, 'disabled')
})

test('empty state and density retain explicit user-facing semantics', () => {
  assert.deepEqual(workspaceGridEmptyState({ loading: true }), { kind: 'loading', text: '' })
  assert.deepEqual(workspaceGridEmptyState({ errorText: '加载失败' }), { kind: 'error', text: '加载失败' })
  assert.deepEqual(workspaceGridEmptyState({ emptyText: '当前页筛选无结果' }), { kind: 'empty', text: '当前页筛选无结果' })
  assert.equal(workspaceGridDensityClass('compact'), 'density-compact')
  assert.equal(workspaceGridDensityClass('unknown'), 'density-standard')
})

test('WorkspaceDataGrid owns only shared table behavior and exposes composition slots', () => {
  const source = read('src/components/workspace/WorkspaceDataGrid.vue')

  assert.match(source, /<el-table\b/)
  assert.match(source, /<CellRenderer\b/)
  assert.match(source, /<CellEditor\b/)
  assert.match(source, /field\.editable/)
  assert.match(source, /canEditCell/)
  assert.match(source, /cell-\$\{field\.key\}/)
  for (const slot of ['operations', 'selection-extra', 'group-header', 'group-summary', 'empty', 'append']) {
    assert.match(source, new RegExp(`["'\\x60]${slot}["'\\x60]`), `missing ${slot} slot`)
  }
  for (const event of ['selection-change', 'sort-change', 'column-resize', 'cell-save', 'cell-cancel', 'cell-error', 'retry']) {
    assert.match(source, new RegExp(`["']${event}["']`), `missing ${event} event`)
  }
  assert.match(source, /defineExpose\([\s\S]*clearSelection/)
  assert.match(source, /selectionSyncRevision/)
  assert.match(source, /aria-label/)
  assert.doesNotMatch(source, /\brequest\s*[.(]|\baxios\b|\bfetch\s*\(/)
  assert.doesNotMatch(source, /hasPermission|permissionCode/)
})
