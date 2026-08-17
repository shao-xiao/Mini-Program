import { workspaceFieldCapability } from './workspaceQueryPlan.js'

const DENSITIES = new Set(['compact', 'standard', 'comfortable', 'spacious'])

export function normalizeWorkspaceGridColumns(columns = []) {
  const seen = new Set()
  return (Array.isArray(columns) ? columns : []).filter(column => {
    const key = column?.key
    if (!key || column.filterOnly || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

export function isWorkspaceGroupRow(row) {
  return row?.__group === true
}

export function workspaceGridRowKey(row, rowKey = 'id') {
  if (isWorkspaceGroupRow(row)) {
    const groupKey = row?.key ?? row?.label
    return groupKey === undefined || groupKey === null
      ? null
      : `__workspace_group__:${String(groupKey)}`
  }

  const value = typeof rowKey === 'function' ? rowKey(row) : row?.[rowKey]
  return value === undefined || value === null || value === '' ? null : value
}

export function workspaceGridSelectable(row, selectable = () => true, rowIndex = 0) {
  if (isWorkspaceGroupRow(row)) return false
  return typeof selectable !== 'function' || selectable(row, rowIndex) !== false
}

export function reconcileWorkspaceSelectionKeys(rows, selectedRowKeys, rowKey = 'id', selectable = () => true) {
  const requested = new Set(Array.isArray(selectedRowKeys) ? selectedRowKeys : [])
  const reconciled = []
  const seen = new Set()

  ;(Array.isArray(rows) ? rows : []).forEach((row, index) => {
    const key = workspaceGridRowKey(row, rowKey)
    if (key === null || seen.has(key) || !requested.has(key) || !workspaceGridSelectable(row, selectable, index)) return
    seen.add(key)
    reconciled.push(key)
  })

  return reconciled
}

export function continuousWorkspaceIndex(rowIndex, currentPage = 1, pageSize = 20) {
  const safeIndex = Number.isFinite(Number(rowIndex)) ? Number(rowIndex) : 0
  const safePage = Math.max(1, Number(currentPage) || 1)
  const safePageSize = Math.max(1, Number(pageSize) || 20)
  return (safePage - 1) * safePageSize + safeIndex + 1
}

export function buildWorkspaceDisplayIndexMap(rows = [], rowKey = 'id', currentPage = 1, pageSize = 20) {
  const indexes = new Map()
  let businessRowIndex = 0

  for (const row of Array.isArray(rows) ? rows : []) {
    if (isWorkspaceGroupRow(row)) continue
    const key = workspaceGridRowKey(row, rowKey)
    if (key === null || indexes.has(key)) continue
    indexes.set(key, continuousWorkspaceIndex(businessRowIndex, currentPage, pageSize))
    businessRowIndex += 1
  }

  return indexes
}

export function workspaceGridGroupSpan({ row, columnIndex, columnCount, groupColumnIndex = 0 }) {
  if (!isWorkspaceGroupRow(row)) return undefined
  if (columnIndex === groupColumnIndex) return [1, Math.max(1, columnCount - groupColumnIndex)]
  return [0, 0]
}

export function workspaceGridSortIntent(payload = {}, columns = [], dataMode = 'client') {
  const fieldKey = payload.column?.columnKey || payload.prop || payload.column?.property || ''
  const field = (Array.isArray(columns) ? columns : []).find(column => column?.key === fieldKey)
  const capability = workspaceFieldCapability(field, 'sort', dataMode)
  const direction = payload.order || ''

  return {
    field: fieldKey,
    prop: fieldKey,
    direction,
    order: direction,
    scope: field ? capability.mode : 'disabled'
  }
}

export function workspaceGridEmptyState({ loading = false, errorText = '', emptyText = '暂无数据' } = {}) {
  if (loading) return { kind: 'loading', text: '' }
  if (String(errorText || '').trim()) return { kind: 'error', text: String(errorText).trim() }
  return { kind: 'empty', text: String(emptyText || '暂无数据') }
}

export function workspaceGridDensityClass(density = 'standard') {
  return `density-${DENSITIES.has(density) ? density : 'standard'}`
}
