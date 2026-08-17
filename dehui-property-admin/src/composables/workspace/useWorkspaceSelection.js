import { computed, ref, watch } from 'vue'

export function useWorkspaceSelection(options = {}) {
  const {
    records,
    rowKey = 'id',
    selectable = row => !row?.__group,
    preserveAcrossPages = false
  } = options

  const selectedRows = ref([])
  const selectedRowKeys = computed(() => selectedRows.value.map((row, index) => rowKeyOf(row, index)))
  const selectedCount = computed(() => selectedRows.value.length)

  if (typeof records === 'function') {
    watch(records, nextRecords => {
      if (preserveAcrossPages) return
      reconcileSelection(nextRecords)
    }, { flush: 'sync' })
  }

  function rowKeyOf(row, index = 0) {
    if (typeof rowKey === 'function') return rowKey(row, index)
    return row?.[rowKey]
  }

  function handleSelectionChange(rows) {
    const seen = new Set()
    selectedRows.value = (Array.isArray(rows) ? rows : []).filter((row, index) => {
      if (!selectable(row)) return false
      const key = rowKeyOf(row, index)
      if (key === undefined || key === null || seen.has(key)) return false
      seen.add(key)
      return true
    })
  }

  function reconcileSelection(nextRecords) {
    const rows = Array.isArray(nextRecords) ? nextRecords : []
    const currentByKey = new Map(rows.map((row, index) => [rowKeyOf(row, index), row]))
    selectedRows.value = selectedRowKeys.value
      .filter(key => currentByKey.has(key))
      .map(key => currentByKey.get(key))
      .filter(selectable)
  }

  function clearSelection() {
    selectedRows.value = []
  }

  function removeSelectionByKeys(keys) {
    const removedKeys = new Set(Array.isArray(keys) ? keys : [keys])
    selectedRows.value = selectedRows.value.filter((row, index) => !removedKeys.has(rowKeyOf(row, index)))
  }

  return {
    selectedRows,
    selectedRowKeys,
    selectedCount,
    rowKeyOf,
    handleSelectionChange,
    reconcileSelection,
    clearSelection,
    removeSelectionByKeys
  }
}
