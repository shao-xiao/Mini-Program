import { computed } from 'vue'
import { normalizeCellOption } from '../../utils/cellRenderer.js'
import { workspaceFieldCapability } from '../../utils/workspaceQueryPlan.js'

export function useWorkspaceColumns(options) {
  const {
    columns,
    customFields = { value: [] },
    tablePrefs,
    dataMode = 'client',
    updatePrefs
  } = options

  const allColumns = computed(() => {
    const remoteFields = new Map((customFields.value || []).map(column => [column.key, column]))
    const result = columns.map(column => {
      const remote = remoteFields.get(column.key)
      if (!column.custom || !remote) return column
      remoteFields.delete(column.key)
      return { ...column, ...remote, minWidth: remote.minWidth || column.minWidth, width: remote.width || column.width }
    })
    remoteFields.forEach(column => result.push(column))
    ;(tablePrefs.customColumns || []).forEach(column => {
      if (!result.some(item => item.key === column.key)) result.push(column)
    })
    return result
  })
  const fieldByKey = computed(() => new Map(allColumns.value.map(column => [column.key, column])))
  const fieldAccessors = computed(() => new Map(allColumns.value.map(column => [
    column.key,
    createFieldAccessor(column)
  ])))
  const optionMaps = computed(() => new Map(allColumns.value.map(column => [
    column.key,
    createOptionMap(column)
  ])))
  const visibleColumnSet = computed(() => new Set([
    ...(tablePrefs.visibleColumns || []),
    ...allColumns.value
      .filter(column => column.required && !column.filterOnly)
      .map(column => column.key)
  ]))
  const orderedColumns = computed(() => {
    const order = tablePrefs.columnOrder || []
    const orderMap = new Map(order.map((key, index) => [key, index]))
    return allColumns.value
      .filter(column => !column.filterOnly)
      .map((column, sourceIndex) => ({ column, sourceIndex }))
      .sort((left, right) => {
        const leftIndex = orderMap.has(left.column.key) ? orderMap.get(left.column.key) : Number.MAX_SAFE_INTEGER
        const rightIndex = orderMap.has(right.column.key) ? orderMap.get(right.column.key) : Number.MAX_SAFE_INTEGER
        return leftIndex === rightIndex ? left.sourceIndex - right.sourceIndex : leftIndex - rightIndex
      })
      .map(item => ({
        ...item.column,
        optionMap: optionMaps.value.get(item.column.key)
      }))
  })
  const visibleOrderedColumns = computed(() => (
    orderedColumns.value.filter(column => visibleColumnSet.value.has(column.key))
  ))

  function isColumnVisible(key) {
    return visibleColumnSet.value.has(key)
  }

  function updateVisibleColumns(value) {
    updatePrefs({ ...tablePrefs, visibleColumns: value })
  }

  function updateColumnOrder(value) {
    updatePrefs({ ...tablePrefs, columnOrder: value })
  }

  function updateColumnWidth(key, value) {
    updatePrefs({
      ...tablePrefs,
      columnWidths: { ...tablePrefs.columnWidths, [key]: value }
    })
  }

  function updateFixedColumns(value) {
    updatePrefs({ ...tablePrefs, fixedColumns: value })
  }

  function updateRowDensity(value) {
    updatePrefs({ ...tablePrefs, rowDensity: value })
  }

  function columnWidth(key, fallback) {
    return tablePrefs.columnWidths?.[key] || fallback
  }

  function columnFixed(key, fallback = false) {
    return tablePrefs.fixedColumns?.[key] || fallback
  }

  function handleHeaderDragEnd(newWidth, _oldWidth, column) {
    const key = column?.columnKey || column?.property
    if (key && fieldByKey.value.has(key)) updateColumnWidth(key, newWidth)
  }

  function isColumnSortable(key) {
    const column = fieldByKey.value.get(key)
    return Boolean(column?.sortable) && workspaceFieldCapability(column, 'sort', dataMode).mode !== 'disabled'
  }

  return {
    allColumns,
    fieldByKey,
    fieldAccessors,
    optionMaps,
    visibleColumnSet,
    orderedColumns,
    visibleOrderedColumns,
    isColumnVisible,
    updateVisibleColumns,
    updateColumnOrder,
    updateColumnWidth,
    updateFixedColumns,
    updateRowDensity,
    columnWidth,
    columnFixed,
    handleHeaderDragEnd,
    isColumnSortable
  }
}

function createFieldAccessor(column) {
  if (typeof column.valueGetter === 'function') return column.valueGetter
  if (column.custom) {
    return record => record?.customFields?.[column.key] ?? column.defaultValue ?? ''
  }
  return record => record?.[column.key]
}

function createOptionMap(column) {
  const source = typeof column.options === 'function' ? column.options() : column.options
  if (!Array.isArray(source)) return new Map()
  return new Map(source.map(option => {
    const normalized = normalizeCellOption(option)
    return [String(normalized.value), normalized]
  }))
}
