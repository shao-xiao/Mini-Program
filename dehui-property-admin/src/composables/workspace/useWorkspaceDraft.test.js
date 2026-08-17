import assert from 'node:assert/strict'
import test from 'node:test'
import { ref } from 'vue'
import { cloneWorkspaceValue } from '../../utils/workspaceClone.js'
import { useWorkspaceDraft } from './useWorkspaceDraft.js'

function createDraft() {
  const persistedPrefs = ref({
    filters: [{ key: 'status', operator: 'equals', value: ['NORMAL'] }],
    columnWidths: { name: 160 },
    columnOrder: ['name', 'status']
  })
  const draft = useWorkspaceDraft({
    persistedPrefs,
    explicitSave: true,
    normalizePrefs: value => cloneWorkspaceValue(value)
  })
  return { persistedPrefs, draft }
}

test('workspace draft tracks nested mutations without changing the saved snapshot', () => {
  const { persistedPrefs, draft } = createDraft()
  draft.draftPrefs.filters[0].value.push('FAULT')
  draft.draftPrefs.columnWidths.name = 260

  assert.equal(draft.isDirty.value, true)
  assert.deepEqual(persistedPrefs.value.filters[0].value, ['NORMAL'])
  assert.equal(persistedPrefs.value.columnWidths.name, 160)
})

test('workspace draft save baseline, discard and reset have explicit semantics', () => {
  const { persistedPrefs, draft } = createDraft()
  draft.draftPrefs.columnOrder.reverse()
  persistedPrefs.value = cloneWorkspaceValue(draft.draftPrefs)
  draft.markSaved(persistedPrefs.value)
  assert.equal(draft.isDirty.value, false)

  draft.draftPrefs.columnWidths.name = 320
  draft.discardDraft()
  assert.equal(draft.draftPrefs.columnWidths.name, 160)
  assert.equal(draft.isDirty.value, false)

  draft.resetDraft({ ...persistedPrefs.value, filters: [] })
  assert.equal(draft.isDirty.value, true)
  assert.deepEqual(draft.draftPrefs.filters, [])
})
