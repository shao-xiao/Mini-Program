import { computed, reactive, ref, unref, watch } from 'vue'
import { cloneWorkspaceValue } from '../../utils/workspaceClone.js'

export function useWorkspaceDraft(options) {
  const {
    persistedPrefs,
    normalizePrefs,
    explicitSave = false
  } = options

  const draftPrefs = reactive(cloneWorkspaceValue(normalizePrefs(resolveValue(persistedPrefs))))
  const revision = ref(0)
  const savedRevision = ref(0)

  watch(draftPrefs, () => {
    revision.value += 1
  }, { deep: true, flush: 'sync' })

  const isDirty = computed(() => explicitSave && revision.value !== savedRevision.value)

  function replaceDraft(prefs, { markSaved = false } = {}) {
    const normalized = cloneWorkspaceValue(normalizePrefs(prefs))
    Object.keys(draftPrefs).forEach(key => {
      if (!Object.hasOwn(normalized, key)) delete draftPrefs[key]
    })
    Object.assign(draftPrefs, normalized)
    if (markSaved) savedRevision.value = revision.value
    return draftPrefs
  }

  function updateDraft(prefs) {
    return replaceDraft(prefs)
  }

  function markSaved(prefs = draftPrefs) {
    return replaceDraft(prefs, { markSaved: true })
  }

  function discardDraft() {
    return replaceDraft(resolveValue(persistedPrefs), { markSaved: true })
  }

  function resetDraft(prefs) {
    return replaceDraft(prefs)
  }

  return {
    persistedPrefs,
    draftPrefs,
    tablePrefs: draftPrefs,
    revision,
    savedRevision,
    isDirty,
    updateDraft,
    replaceDraft,
    markSaved,
    discardDraft,
    resetDraft
  }
}

function resolveValue(source) {
  if (typeof source === 'function') return source()
  return unref(source)
}
