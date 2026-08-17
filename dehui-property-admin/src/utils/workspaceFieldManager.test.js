import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildWorkspaceCustomField,
  moveWorkspaceField,
  orderWorkspaceFields,
  reorderWorkspaceField,
  setWorkspaceFixedColumn,
  toggleWorkspaceFieldVisibility
} from './workspaceFieldManager.js'

const columns = [
  { key: 'name', label: '名称', required: true },
  { key: 'status', label: '状态' },
  { key: 'owner', label: '负责人' },
  { key: 'actions', label: '操作', fixedOnly: true }
]

test('field visibility protects required fields and removes duplicates', () => {
  const visible = ['name', 'status', 'status']
  const hidden = toggleWorkspaceFieldVisibility(columns, visible, 'name', false)
  const shown = toggleWorkspaceFieldVisibility(columns, hidden, 'owner', true)

  assert.deepEqual(visible, ['name', 'status', 'status'])
  assert.deepEqual(hidden, ['name', 'status'])
  assert.deepEqual(shown, ['name', 'status', 'owner'])
})

test('field ordering is stable and immutable', () => {
  assert.deepEqual(orderWorkspaceFields(columns, ['owner', 'name']).map(item => item.key), ['owner', 'name', 'status'])
  assert.deepEqual(moveWorkspaceField(['name', 'status', 'owner'], 'status', -1), ['status', 'name', 'owner'])
  assert.deepEqual(
    reorderWorkspaceField(['name', 'status', 'owner'], 'name', 'owner', true),
    ['status', 'owner', 'name']
  )
})

test('fixed columns support left, right and cancel without mutating source', () => {
  const fixed = { name: 'left' }
  const right = setWorkspaceFixedColumn(fixed, 'status', 'right')
  const cancelled = setWorkspaceFixedColumn(right, 'name', '')

  assert.deepEqual(fixed, { name: 'left' })
  assert.deepEqual(right, { name: 'left', status: 'right' })
  assert.deepEqual(cancelled, { status: 'right' })
})

test('custom field payload is normalized and detached from form state', () => {
  const form = { label: '  设备等级 ', type: 'singleSelect', defaultValue: '', optionsText: 'A, B, A' }
  const field = buildWorkspaceCustomField(form, 'custom_level')

  assert.equal(field.key, 'custom_level')
  assert.equal(field.label, '设备等级')
  assert.equal(field.type, 'select')
  assert.deepEqual(field.options, ['A', 'B'])
  assert.deepEqual(form, { label: '  设备等级 ', type: 'singleSelect', defaultValue: '', optionsText: 'A, B, A' })
})
