import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'

const root = process.cwd()

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('toolbar facade keeps legacy contracts while delegating focused controls', () => {
  const source = read('src/components/DhicDataToolbar.vue')

  ;[
    'update:visibleColumns',
    'update:columnOrder',
    'update:fixedColumns',
    'update:filters',
    'update:sorts',
    'update:groups',
    'update:rowDensity',
    'workspace-action',
    'workspace-file'
  ].forEach(eventName => assert.match(source, new RegExp(`['\"]${eventName}['\"]`)))

  assert.match(source, /provideWorkspaceToolbar/)
  assert.match(source, /<FilterBuilderPopover/)
  assert.match(source, /<SortBuilderPopover/)
  assert.match(source, /<GroupBuilderPopover/)
  assert.match(source, /<RowDensityMenu/)
  assert.match(source, /<FieldManager/)
  assert.match(source, /<ImportExportManager/)
  assert.doesNotMatch(source, /告诉 AI 你想看到什么/)
})

test('field manager delegates visibility, ordering, fixing and custom field form', () => {
  const source = read('src/components/FieldManager.vue')
  const orderList = read('src/components/workspace/FieldOrderList.vue')

  assert.match(source, /<FieldVisibilityList/)
  assert.match(source, /<FieldOrderList/)
  assert.match(source, /<CustomFieldForm/)
  assert.match(source, /useWorkspaceToolbar/)
  assert.match(orderList, /<FixedColumnControls/)
})

test('toolbar controls provide accessible icon actions and safe context fallback', () => {
  const context = read('src/components/workspace/workspaceToolbarContext.js')
  const fieldOrder = read('src/components/workspace/FieldOrderList.vue')
  const fixedColumns = read('src/components/workspace/FixedColumnControls.vue')
  const filters = read('src/components/workspace/FilterBuilderPopover.vue')

  assert.match(context, /inject/)
  assert.match(context, /console\.warn/)
  assert.match(context, /createWorkspaceToolbarFallback/)
  assert.match(fieldOrder, /aria-label/)
  assert.match(fixedColumns, /aria-label/)
  assert.match(filters, /仅当前页/)
})
