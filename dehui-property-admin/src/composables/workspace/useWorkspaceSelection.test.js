import assert from 'node:assert/strict'
import test from 'node:test'
import { ref } from 'vue'
import { useWorkspaceSelection } from './useWorkspaceSelection.js'

test('workspace selection filters group rows, deduplicates keys and clears state', () => {
  const records = ref([{ id: 1 }, { id: 2 }])
  const selection = useWorkspaceSelection({ records: () => records.value })
  selection.handleSelectionChange([{ id: 1 }, { id: 1 }, { __group: true, key: 'group' }, { id: 2 }])

  assert.deepEqual(selection.selectedRowKeys.value, [1, 2])
  assert.equal(selection.selectedCount.value, 2)
  selection.clearSelection()
  assert.equal(selection.selectedCount.value, 0)
})

test('workspace selection reconciles current instances after data refresh and removes missing rows', () => {
  const records = ref([{ id: 1, version: 1 }, { id: 2, version: 1 }])
  const selection = useWorkspaceSelection({ records: () => records.value })
  selection.handleSelectionChange(records.value)

  records.value = [{ id: 2, version: 2 }, { id: 3, version: 1 }]
  assert.deepEqual(selection.selectedRowKeys.value, [2])
  assert.equal(selection.selectedRows.value[0].version, 2)

  selection.removeSelectionByKeys(2)
  assert.equal(selection.selectedCount.value, 0)
})
