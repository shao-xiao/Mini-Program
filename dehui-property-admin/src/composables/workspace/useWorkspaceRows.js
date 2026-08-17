import { computed, ref, unref } from 'vue'
import {
  buildGroupedRowsByConditions,
  buildSequentialGroupedRowsByConditions,
  filterRecordsByConditions,
  sortRecordsByConditions
} from '../../utils/tableViewPrefs.js'
import { buildWorkspaceResultScope } from '../../utils/workspaceQueryPlan.js'

export function useWorkspaceRows(options) {
  const {
    records,
    serverTotal,
    tablePrefs,
    fields,
    fieldAccessors,
    localFilters,
    localSorts,
    localGroups,
    serverGroups = () => [],
    groupLabel,
    visibleColumnCount,
    structuralColumnCount = 2,
    groupColumnIndex = 0,
    rowKey = 'id'
  } = options

  const loadedRecords = computed(() => normalizeRecords(records()))
  const filteredRecords = computed(() => filterRecordsByConditions(
    loadedRecords.value,
    resolveValue(localFilters),
    resolveValue(fields),
    tablePrefs.filterLogic,
    resolveValue(fieldAccessors)
  ))
  const sortedRecords = computed(() => sortRecordsByConditions(
    filteredRecords.value,
    resolveValue(localSorts),
    resolveValue(fields),
    resolveValue(fieldAccessors)
  ))
  const visibleRecords = sortedRecords
  const groupedRows = computed(() => {
    const groups = normalizeGroups(resolveValue(localGroups))
    if (groups.length > 0) {
      return buildGroupedRowsByConditions(
        sortedRecords.value,
        groups,
        resolveValue(fields),
        groupLabel,
        resolveValue(fieldAccessors),
        rowKey
      )
    }

    const orderedServerGroups = normalizeGroups(resolveValue(serverGroups))
    if (orderedServerGroups.length > 0) {
      return buildSequentialGroupedRowsByConditions(
        sortedRecords.value,
        orderedServerGroups,
        resolveValue(fields),
        groupLabel,
        resolveValue(fieldAccessors),
        'server',
        rowKey
      )
    }
    return sortedRecords.value
  })
  const collapsedGroupKeys = ref(new Set())
  const tableRows = computed(() => applyCollapsedGroups(groupedRows.value, collapsedGroupKeys.value))
  const resultScope = computed(() => buildWorkspaceResultScope({
    serverTotal: serverTotal(),
    loadedRecords: loadedRecords.value,
    visibleRecords: visibleRecords.value
  }))
  const tableColumnCount = computed(() => visibleColumnCount() + structuralColumnCount)
  const tableEmptyText = computed(() => {
    if (loadedRecords.value.length > 0 && filteredRecords.value.length === 0) {
      return '当前视图筛选无结果，请清除或重置视图筛选'
    }
    return '暂无数据'
  })

  function tableSpanMethod({ row, columnIndex }) {
    if (!row.__group) return undefined
    if (columnIndex === groupColumnIndex) return [1, tableColumnCount.value - groupColumnIndex]
    return [0, 0]
  }

  function recordDisplayIndex(row, pagination) {
    const index = sortedRecords.value.findIndex(item => item === row)
    if (index < 0) return '-'
    if (!pagination) return index + 1
    return (pagination.currentPage - 1) * pagination.pageSize + index + 1
  }

  function toggleGroup(groupKey) {
    const next = new Set(collapsedGroupKeys.value)
    if (next.has(groupKey)) next.delete(groupKey)
    else next.add(groupKey)
    collapsedGroupKeys.value = next
  }

  function isGroupCollapsed(groupKey) {
    return collapsedGroupKeys.value.has(groupKey)
  }

  function clearCollapsedGroups() {
    collapsedGroupKeys.value = new Set()
  }

  return {
    loadedRecords,
    filteredRecords,
    sortedRecords,
    visibleRecords,
    groupedRows,
    tableRows,
    collapsedGroupKeys,
    resultScope,
    tableColumnCount,
    tableEmptyText,
    tableSpanMethod,
    recordDisplayIndex,
    toggleGroup,
    isGroupCollapsed,
    clearCollapsedGroups
  }
}

function resolveValue(source) {
  return unref(source)
}

function normalizeRecords(value) {
  return Array.isArray(value) ? value : []
}

function normalizeGroups(value) {
  return Array.isArray(value) ? value : []
}

function applyCollapsedGroups(rows, collapsedKeys) {
  if (!(collapsedKeys instanceof Set) || collapsedKeys.size === 0) return rows
  const visible = []
  let hiddenAtLevel = null

  for (const row of rows) {
    if (row?.__group) {
      if (hiddenAtLevel !== null && row.level <= hiddenAtLevel) hiddenAtLevel = null
      if (hiddenAtLevel !== null) continue
      visible.push(row)
      if (collapsedKeys.has(row.key)) hiddenAtLevel = row.level
      continue
    }
    if (hiddenAtLevel === null) visible.push(row)
  }

  return visible
}
