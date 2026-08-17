import {
  isDateFieldType,
  isGroupableFieldType,
  isMultiValueFieldType,
  isNumericFieldType,
  isOptionFieldType,
  normalizeFieldType
} from '../config/fieldTypes.js'
import { cloneWorkspaceValue } from './workspaceClone.js'

const ROW_DENSITIES = ['compact', 'standard', 'comfortable', 'spacious']
const SORT_ORDERS = ['ascending', 'descending']
const FILTER_LOGICS = ['and', 'or']
const FILTER_OPERATORS = [
  'contains',
  'notContains',
  'containsAny',
  'containsAll',
  'equals',
  'notEquals',
  'greaterThan',
  'greaterThanOrEqual',
  'lessThan',
  'lessThanOrEqual',
  'before',
  'after',
  'between',
  'empty',
  'notEmpty',
  'today',
  'thisWeek',
  'thisMonth',
  'dateRange'
]
const VALUELESS_FILTER_OPERATORS = ['empty', 'notEmpty', 'today', 'thisWeek', 'thisMonth']
const RANGE_FILTER_OPERATORS = ['between', 'dateRange']
const FILTER_OPERATOR_OPTIONS = {
  text: [
    { label: '包含', value: 'contains' },
    { label: '不包含', value: 'notContains' },
    { label: '等于', value: 'equals' },
    { label: '不等于', value: 'notEquals' },
    { label: '为空', value: 'empty' },
    { label: '不为空', value: 'notEmpty' }
  ],
  number: [
    { label: '等于', value: 'equals' },
    { label: '不等于', value: 'notEquals' },
    { label: '大于', value: 'greaterThan' },
    { label: '大于等于', value: 'greaterThanOrEqual' },
    { label: '小于', value: 'lessThan' },
    { label: '小于等于', value: 'lessThanOrEqual' },
    { label: '区间', value: 'between' },
    { label: '为空', value: 'empty' },
    { label: '不为空', value: 'notEmpty' }
  ],
  date: [
    { label: '等于', value: 'equals' },
    { label: '不等于', value: 'notEquals' },
    { label: '早于', value: 'before' },
    { label: '晚于', value: 'after' },
    { label: '今天', value: 'today' },
    { label: '本周', value: 'thisWeek' },
    { label: '本月', value: 'thisMonth' },
    { label: '自定义范围', value: 'dateRange' },
    { label: '为空', value: 'empty' },
    { label: '不为空', value: 'notEmpty' }
  ],
  option: [
    { label: '等于', value: 'equals' },
    { label: '不等于', value: 'notEquals' },
    { label: '多选包含', value: 'containsAny' },
    { label: '为空', value: 'empty' },
    { label: '不为空', value: 'notEmpty' }
  ]
}

FILTER_OPERATOR_OPTIONS.multiOption = [
  { label: '包含任一', value: 'containsAny' },
  { label: '包含全部', value: 'containsAll' },
  { label: '不包含', value: 'notContains' },
  { label: '为空', value: 'empty' },
  { label: '不为空', value: 'notEmpty' }
]

FILTER_OPERATOR_OPTIONS.attachment = [
  { label: '空', value: 'empty' },
  { label: '不为空', value: 'notEmpty' }
]

export function filterOperatorsForType(type = 'text') {
  const normalizedType = normalizeFieldType(type)
  if (normalizedType === 'attachment') return FILTER_OPERATOR_OPTIONS.attachment
  if (isNumericFieldType(normalizedType)) return FILTER_OPERATOR_OPTIONS.number
  if (isDateFieldType(normalizedType)) return FILTER_OPERATOR_OPTIONS.date
  if (isMultiValueFieldType(normalizedType)) return FILTER_OPERATOR_OPTIONS.multiOption
  if (isOptionFieldType(normalizedType) || normalizedType === 'checkbox') return FILTER_OPERATOR_OPTIONS.option
  return FILTER_OPERATOR_OPTIONS.text
}

export function defaultFilterOperator(type = 'text') {
  const normalizedType = normalizeFieldType(type)
  if (normalizedType === 'attachment') return 'notEmpty'
  return normalizedType === 'checkbox' || isDateFieldType(normalizedType) || isNumericFieldType(normalizedType) || isOptionFieldType(normalizedType)
    ? 'equals'
    : 'contains'
}

export function emptyFilterValue(operator, type = 'text') {
  if (normalizeFieldType(type) === 'attachment') return ''
  if (RANGE_FILTER_OPERATORS.includes(operator)) return []
  if (isMultiValueFieldType(type)) return []
  return ''
}

export function normalizeTableViewPrefs(savedPrefs = {}, defaults = {}, availableColumnKeys = []) {
  const source = normalizeLegacyPreferenceAliases(savedPrefs)
  const fallback = normalizeLegacyPreferenceAliases(defaults)
  const customColumns = normalizeCustomColumns(source.customColumns)
  const availableKeys = new Set([...availableColumnKeys, ...customColumns.map(column => column.key)])
  const defaultVisibleColumns = normalizeColumns(fallback.visibleColumns, availableKeys)
  const savedVisibleColumns = normalizeColumns(source.visibleColumns, availableKeys)

  const sortKey = availableKeys.has(source.sortKey)
    ? source.sortKey
    : (availableKeys.has(fallback.sortKey) ? fallback.sortKey : '')

  const groupKey = availableKeys.has(source.groupKey)
    ? source.groupKey
    : (availableKeys.has(fallback.groupKey) ? fallback.groupKey : '')

  return {
    visibleColumns: savedVisibleColumns.length > 0 ? savedVisibleColumns : defaultVisibleColumns,
    columnOrder: normalizeColumnOrder(source.columnOrder, fallback.columnOrder, availableKeys),
    columnWidths: normalizeColumnWidths(source.columnWidths, availableKeys),
    fixedColumns: normalizeFixedColumns(source.fixedColumns, fallback.fixedColumns, availableKeys),
    rowDensity: ROW_DENSITIES.includes(source.rowDensity) ? source.rowDensity : (fallback.rowDensity || 'standard'),
    pageSize: normalizePageSize(source.pageSize, fallback.pageSize),
    sortKey,
    sortOrder: SORT_ORDERS.includes(source.sortOrder) ? source.sortOrder : (fallback.sortOrder || ''),
    groupKey,
    customColumns,
    filterLogic: FILTER_LOGICS.includes(source.filterLogic) ? source.filterLogic : (fallback.filterLogic || 'and'),
    filters: normalizeFilters(source.filters, availableKeys),
    groups: normalizeGroups(source.groups, availableKeys),
    sorts: normalizeSorts(source.sorts, availableKeys)
  }
}

export function sortRecordsByField(records = [], key, order = '') {
  if (!key || !SORT_ORDERS.includes(order)) {
    return [...records]
  }

  const direction = order === 'ascending' ? 1 : -1

  return [...records].sort((left, right) => {
    return compareValues(left?.[key], right?.[key]) * direction
  })
}

export function filterRecordsByConditions(records = [], filters = [], columns = [], filterLogic = 'and', fieldAccessors) {
  const enabledFilters = Array.isArray(filters)
    ? filters.filter(filter => filter?.key && filter?.operator && isCompleteFilter(filter))
    : []

  if (enabledFilters.length === 0) return [...records]

  const matcher = filterLogic === 'or' ? 'some' : 'every'
  return records.filter(record => enabledFilters[matcher](filter => {
    const value = readRecordValue(record, filter.key, columns, fieldAccessors)
    return matchFilter(value, filter.operator, filter.value)
  }))
}

export function sortRecordsByConditions(records = [], sorts = [], columns = [], fieldAccessors) {
  const enabledSorts = Array.isArray(sorts)
    ? sorts.filter(sort => sort?.key && SORT_ORDERS.includes(sort.order))
    : []

  if (enabledSorts.length === 0) return [...records]

  return [...records].sort((left, right) => {
    for (const sort of enabledSorts) {
      const direction = sort.order === 'ascending' ? 1 : -1
      const result = compareValues(
        readRecordValue(left, sort.key, columns, fieldAccessors),
        readRecordValue(right, sort.key, columns, fieldAccessors)
      )

      if (result !== 0) return result * direction
    }

    return 0
  })
}

export function buildGroupedRowsByConditions(
  records = [],
  groups = [],
  columns = [],
  getLabel = value => value || '未分组',
  fieldAccessors,
  rowKey = 'id'
) {
  const enabledGroups = normalizeRuntimeGroups(groups)
  if (enabledGroups.length === 0) return [...records]
  const orderedRecords = sortRecordsByConditions(records, enabledGroups, columns, fieldAccessors)
  return buildSequentialGroupedRowsByConditions(
    orderedRecords,
    enabledGroups,
    columns,
    getLabel,
    fieldAccessors,
    'client',
    rowKey
  )
}

export function buildSequentialGroupedRowsByConditions(
  records = [],
  groups = [],
  columns = [],
  getLabel = value => value || '未分组',
  fieldAccessors,
  scope = 'server',
  rowKey = 'id'
) {
  const enabledGroups = normalizeRuntimeGroups(groups)
  if (enabledGroups.length === 0) return [...records]

  const rows = []
  const lastParts = records.length > 0
    ? enabledGroups.map(group => normalizeGroupKey(
      readRecordValue(records[records.length - 1], group.key, columns, fieldAccessors)
    ))
    : []
  let previousParts = []
  records.forEach((record, recordIndex) => {
    const parts = enabledGroups.map(group => normalizeGroupKey(
      readRecordValue(record, group.key, columns, fieldAccessors)
    ))
    const changedAt = firstChangedGroupLevel(previousParts, parts)

    for (let level = changedAt; level < parts.length; level += 1) {
      const path = parts.slice(0, level + 1)
      const group = enabledGroups[level]
      const column = columns.find(item => item.key === group.key)
      const rawValue = parts[level]
      const displayValue = rawValue === '__EMPTY__' ? '未分组' : getLabel(rawValue, group.key, level)
      rows.push({
        __group: true,
        key: groupRowKey(level, group.key, path, stableRecordKey(record, rowKey)),
        path,
        level,
        fieldKey: group.key,
        label: enabledGroups.length > 1 && column?.label ? `${column.label}：${displayValue}` : displayValue,
        count: countSequentialGroup(records, recordIndex, enabledGroups, path, columns, fieldAccessors),
        countScope: scope === 'server' ? 'loaded-page' : 'visible-result',
        scope,
        mayContinueFromPreviousPage: scope === 'server' && recordIndex === 0,
        mayContinueOnNextPage: scope === 'server' && path.every((part, index) => lastParts[index] === part)
      })
    }
    rows.push(record)
    previousParts = parts
  })

  return rows
}

export function buildGroupedRows(records = [], key, getLabel = value => value || '未分组') {
  if (!key) {
    return [...records]
  }

  const groups = new Map()

  records.forEach(record => {
    const groupKey = normalizeGroupKey(record?.[key])

    if (!groups.has(groupKey)) {
      groups.set(groupKey, [])
    }

    groups.get(groupKey).push(record)
  })

  return Array.from(groups.entries()).flatMap(([groupKey, groupRecords]) => [
    {
      __group: true,
      key: groupKey,
      label: getLabel(groupKey),
      count: groupRecords.length
    },
    ...groupRecords
  ])
}

function normalizeColumns(columns, availableKeys) {
  if (!Array.isArray(columns)) {
    return []
  }

  const normalized = []

  columns.forEach(key => {
    if (availableKeys.has(key) && !normalized.includes(key)) {
      normalized.push(key)
    }
  })

  return normalized
}

function normalizeColumnOrder(savedOrder, defaultOrder, availableKeys) {
  const normalized = normalizeColumns(savedOrder, availableKeys)
  const fallback = normalizeColumns(defaultOrder, availableKeys)

  return [
    ...normalized,
    ...fallback.filter(key => !normalized.includes(key)),
    ...Array.from(availableKeys).filter(key => !normalized.includes(key) && !fallback.includes(key))
  ]
}

function normalizeColumnWidths(widths, availableKeys) {
  if (!widths || typeof widths !== 'object' || Array.isArray(widths)) return {}

  return Object.entries(widths).reduce((result, [key, value]) => {
    const width = Number(value)
    if (availableKeys.has(key) && Number.isFinite(width)) {
      result[key] = Math.min(Math.max(Math.round(width), 70), 640)
    }
    return result
  }, {})
}

function normalizeFixedColumns(savedFixed, defaultFixed, availableKeys) {
  const source = savedFixed && typeof savedFixed === 'object' && !Array.isArray(savedFixed)
    ? savedFixed
    : (defaultFixed || {})

  return Object.entries(source).reduce((result, [key, value]) => {
    if (availableKeys.has(key) && ['left', 'right'].includes(value)) {
      result[key] = value
    }
    return result
  }, {})
}

function normalizeCustomColumns(columns) {
  if (!Array.isArray(columns)) return []

  const normalized = []

  columns.forEach(column => {
    if (!column?.key || !column?.label || normalized.some(item => item.key === column.key)) return

    const type = normalizeFieldType(column.type)
    normalized.push({
      key: String(column.key),
      label: String(column.label),
      type,
      options: Array.isArray(column.options)
        ? column.options.map(normalizeFieldOption).filter(Boolean)
        : [],
      defaultValue: cloneWorkspaceValue(column.defaultValue ?? ''),
      custom: true,
      editable: column.editable !== false,
      sortable: true,
      groupable: isGroupableFieldType(type)
    })
  })

  return normalized
}

function normalizeFieldOption(option) {
  if (option && typeof option === 'object') {
    const value = option.value ?? option.label
    if (value === undefined || value === null || String(value).trim() === '') return null
    return {
      ...option,
      value,
      label: String(option.label ?? value).trim()
    }
  }

  const value = String(option ?? '').trim()
  return value || null
}

function normalizeFilters(filters, availableKeys) {
  if (!Array.isArray(filters)) return []

  return filters
    .filter(filter => availableKeys.has(filter?.key) && FILTER_OPERATORS.includes(filter?.operator))
    .map(filter => ({
      key: filter.key,
      operator: filter.operator,
      value: cloneWorkspaceValue(filter.value ?? '')
    }))
}

export function isCompleteFilter(filter) {
  if (!filter?.key || !filter?.operator) return false
  if (VALUELESS_FILTER_OPERATORS.includes(filter.operator)) return true
  if (RANGE_FILTER_OPERATORS.includes(filter.operator)) {
    return Array.isArray(filter.value) && filter.value.length === 2 && filter.value.every(hasFilterValue)
  }
  if (Array.isArray(filter.value)) return filter.value.length > 0
  return hasFilterValue(filter.value)
}

function normalizeGroups(groups, availableKeys) {
  if (!Array.isArray(groups)) return []
  return uniqueConditions(groups, availableKeys, group => ({
    key: group.key,
    order: group.order === 'descending' ? 'descending' : 'ascending'
  }))
}

function normalizeSorts(sorts, availableKeys) {
  if (!Array.isArray(sorts)) return []
  return uniqueConditions(
    sorts.filter(sort => SORT_ORDERS.includes(sort?.order)),
    availableKeys,
    sort => ({ key: sort.key, order: sort.order })
  )
}

function normalizeLegacyPreferenceAliases(prefs = {}) {
  const groupBy = Array.isArray(prefs.groupBy)
    ? prefs.groupBy
    : (prefs.groupBy ? [{ key: prefs.groupBy, order: 'ascending' }] : undefined)
  return {
    ...prefs,
    visibleColumns: prefs.visibleColumns ?? prefs.visibleFields,
    columnOrder: prefs.columnOrder ?? prefs.fieldOrder,
    rowDensity: prefs.rowDensity ?? prefs.rowHeight,
    groups: prefs.groups ?? groupBy
  }
}

function normalizePageSize(value, fallback) {
  const candidate = Number(value ?? fallback ?? 20)
  if (!Number.isFinite(candidate)) return 20
  return Math.min(Math.max(Math.round(candidate), 1), 200)
}

function uniqueConditions(conditions, availableKeys, mapper) {
  const seen = new Set()
  const normalized = []
  for (const condition of conditions) {
    if (!availableKeys.has(condition?.key) || seen.has(condition.key)) continue
    seen.add(condition.key)
    normalized.push(mapper(condition))
  }
  return normalized
}

function normalizeRuntimeGroups(groups) {
  const seen = new Set()
  return (Array.isArray(groups) ? groups : []).filter(group => {
    if (!group?.key || seen.has(group.key)) return false
    seen.add(group.key)
    return true
  }).map(group => ({
    key: group.key,
    order: group.order === 'descending' ? 'descending' : 'ascending'
  }))
}

function firstChangedGroupLevel(previous, current) {
  const limit = Math.min(previous.length, current.length)
  for (let index = 0; index < limit; index += 1) {
    if (previous[index] !== current[index]) return index
  }
  return previous.length === current.length ? current.length : limit
}

function countSequentialGroup(records, startIndex, groups, path, columns, fieldAccessors) {
  let count = 0
  for (let index = startIndex; index < records.length; index += 1) {
    const candidatePath = groups.slice(0, path.length).map(group => normalizeGroupKey(
      readRecordValue(records[index], group.key, columns, fieldAccessors)
    ))
    if (candidatePath.some((value, pathIndex) => value !== path[pathIndex])) break
    count += 1
  }
  return count
}

function groupRowKey(level, fieldKey, path, segmentAnchor) {
  return `group:${level}:${JSON.stringify(fieldKey)}:${JSON.stringify(path)}:${JSON.stringify(segmentAnchor)}`
}

function stableRecordKey(record, rowKey) {
  const configured = typeof rowKey === 'function' ? rowKey(record) : record?.[rowKey]
  if (configured !== undefined && configured !== null && configured !== '') return configured
  if (record?.id !== undefined && record?.id !== null) return record.id
  if (record?.code !== undefined && record?.code !== null) return record.code
  if (record?.uuid !== undefined && record?.uuid !== null) return record.uuid
  return JSON.stringify(Object.entries(record || {}).sort(([left], [right]) => left.localeCompare(right)))
}

function compareValues(left, right) {
  const leftValue = normalizeComparable(left)
  const rightValue = normalizeComparable(right)

  if (leftValue === rightValue) return 0
  if (leftValue === null) return 1
  if (rightValue === null) return -1

  if (typeof leftValue === 'number' && typeof rightValue === 'number') {
    return leftValue - rightValue
  }

  return String(leftValue).localeCompare(String(rightValue), 'zh-CN', {
    numeric: true,
    sensitivity: 'base'
  })
}

function normalizeComparable(value) {
  if (value === undefined || value === null || value === '') {
    return null
  }

  const numberValue = Number(value)
  if (Number.isFinite(numberValue) && String(value).trim() !== '') {
    return numberValue
  }

  return value
}

function readRecordValue(record, key, columns, fieldAccessors) {
  const accessor = fieldAccessors?.get?.(key)
  if (typeof accessor === 'function') return accessor(record)
  const column = columns.find(item => item.key === key)

  if (typeof column?.valueGetter === 'function') {
    return column.valueGetter(record)
  }

  if (column?.custom) {
    return record?.customFields?.[key] ?? column.defaultValue ?? ''
  }

  return record?.[key]
}

function matchFilter(value, operator, expected) {
  const normalizedValue = value === undefined || value === null ? '' : String(value)
  if (Array.isArray(expected)) {
    const normalizedExpected = expected
      .filter(item => item !== undefined && item !== null)
      .map(item => String(item))
    const normalizedValues = (Array.isArray(value) ? value : [value])
      .filter(item => item !== undefined && item !== null)
      .map(item => String(item))

    if (operator === 'equals' || operator === 'containsAny') {
      return normalizedExpected.some(item => normalizedValues.includes(item))
    }
    if (operator === 'notEquals' || operator === 'notContains') {
      return normalizedExpected.every(item => !normalizedValues.includes(item))
    }
    if (operator === 'containsAll') {
      return normalizedExpected.every(item => normalizedValues.includes(item))
    }
    if (operator === 'contains') {
      return normalizedExpected.some(item => normalizedValue.toLowerCase().includes(item.toLowerCase()))
    }
  }

  const normalizedExpected = expected === undefined || expected === null ? '' : String(expected)

  if (operator === 'empty') return normalizedValue.trim() === ''
  if (operator === 'notEmpty') return normalizedValue.trim() !== ''
  if (operator === 'greaterThan') return compareFilterValues(value, expected) > 0
  if (operator === 'greaterThanOrEqual') return compareFilterValues(value, expected) >= 0
  if (operator === 'lessThan') return compareFilterValues(value, expected) < 0
  if (operator === 'lessThanOrEqual') return compareFilterValues(value, expected) <= 0
  if (operator === 'before') return compareFilterValues(value, expected) < 0
  if (operator === 'after') return compareFilterValues(value, expected) > 0
  if (operator === 'between' || operator === 'dateRange') {
    return Array.isArray(expected) && expected.length === 2 &&
      compareFilterValues(value, expected[0]) >= 0 &&
      compareFilterValues(value, expected[1]) <= 0
  }
  if (operator === 'today') return isDateInRange(value, startOfToday(), endOfToday())
  if (operator === 'thisWeek') return isDateInRange(value, startOfWeek(), endOfToday())
  if (operator === 'thisMonth') return isDateInRange(value, startOfMonth(), endOfToday())
  if (operator === 'equals') return normalizedValue === normalizedExpected
  if (operator === 'notEquals') return normalizedValue !== normalizedExpected
  if (operator === 'notContains') return !normalizedValue.toLowerCase().includes(normalizedExpected.toLowerCase())
  if (operator === 'containsAny') return normalizedValue === normalizedExpected

  return normalizedValue.toLowerCase().includes(normalizedExpected.toLowerCase())
}

function hasFilterValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

function compareFilterValues(left, right) {
  const leftDate = parseDateValue(left)
  const rightDate = parseDateValue(right)
  if (leftDate !== null && rightDate !== null) return leftDate - rightDate

  const leftNumber = Number(left)
  const rightNumber = Number(right)
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) return leftNumber - rightNumber

  return String(left ?? '').localeCompare(String(right ?? ''), 'zh-CN', {
    numeric: true,
    sensitivity: 'base'
  })
}

function parseDateValue(value) {
  if (value instanceof Date && !Number.isNaN(value.getTime())) return value.getTime()
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}(?:[ T].*)?$/.test(value)) return null
  const timestamp = new Date(value).getTime()
  return Number.isNaN(timestamp) ? null : timestamp
}

function startOfToday() {
  const date = new Date()
  date.setHours(0, 0, 0, 0)
  return date.getTime()
}

function endOfToday() {
  const date = new Date()
  date.setHours(23, 59, 59, 999)
  return date.getTime()
}

function startOfWeek() {
  const date = new Date()
  const day = date.getDay() || 7
  date.setDate(date.getDate() - day + 1)
  date.setHours(0, 0, 0, 0)
  return date.getTime()
}

function startOfMonth() {
  const date = new Date()
  date.setDate(1)
  date.setHours(0, 0, 0, 0)
  return date.getTime()
}

function isDateInRange(value, start, end) {
  const timestamp = parseDateValue(value)
  return timestamp !== null && timestamp >= start && timestamp <= end
}

function normalizeGroupKey(value) {
  if (value === undefined || value === null || value === '') {
    return '__EMPTY__'
  }

  return String(value)
}
