import { isCompleteFilter } from './tableViewPrefs.js'

export const WORKSPACE_QUERY_MODES = Object.freeze(['server', 'client', 'page', 'disabled'])

const CUSTOM_FIELD_OPERATORS = Object.freeze({
  text: ['contains', 'notContains', 'equals', 'notEquals', 'empty', 'notEmpty'],
  number: ['equals', 'notEquals', 'greaterThan', 'greaterThanOrEqual', 'lessThan', 'lessThanOrEqual', 'between', 'empty', 'notEmpty'],
  date: ['equals', 'notEquals', 'before', 'after', 'between', 'dateRange', 'today', 'thisWeek', 'thisMonth', 'empty', 'notEmpty'],
  select: ['equals', 'notEquals', 'containsAny', 'notContains', 'empty', 'notEmpty'],
  multiSelect: ['equals', 'notEquals', 'containsAny', 'containsAll', 'notContains', 'empty', 'notEmpty'],
  boolean: ['equals', 'notEquals', 'empty', 'notEmpty']
})

export function workspaceFieldCapability(field, capability, dataMode = 'client') {
  const query = field?.query
  const directFilterQuery = capability === 'filter' && query && Object.hasOwn(query, 'mode')
  const declared = directFilterQuery ? query : query?.[capability]

  if (declared === false) return { mode: 'disabled' }
  if (typeof declared === 'string') return { mode: declared }
  if (declared && typeof declared === 'object') return declared

  if (field?.custom) return customFieldCapability(field, capability, dataMode)

  if (dataMode !== 'client') return { mode: 'disabled' }
  if (capability === 'filter') return { mode: field?.filterable === false ? 'disabled' : 'client' }
  if (capability === 'sort') return { mode: field?.sortable ? 'client' : 'disabled' }
  if (capability === 'group') return { mode: field?.groupable ? 'client' : 'disabled' }
  return { mode: 'disabled' }
}

export function buildWorkspaceQueryPlan({
  fields = [],
  filters = [],
  sorts = [],
  groups = [],
  filterLogic = 'and',
  page,
  pageSize,
  keyword,
  query = {}
} = {}) {
  const dataMode = query.dataMode === 'server' ? 'server' : 'client'
  const allowedFilterLogics = query.filterLogics || (dataMode === 'server' ? ['and'] : ['and', 'or'])
  const normalizedFilterLogic = filterLogic === 'or' ? 'or' : 'and'
  const fieldMap = new Map(fields.map(field => [field.key, field]))
  const normalizedSorts = normalizeOrderingConditions(sorts, fieldMap)
  const normalizedGroups = normalizeOrderingConditions(groups, fieldMap)
  const completeFilters = filters
    .map((condition, index) => ({ condition, index }))
    .filter(item => isCompleteFilter(item.condition))
  const serializedCustomFilters = []
  const plan = {
    mode: dataMode,
    page: normalizePositiveInteger(page, 1),
    pageSize: normalizePositiveInteger(pageSize, 20, 200),
    keyword: normalizeKeyword(keyword),
    filterLogic: normalizedFilterLogic,
    filters: [],
    sorts: [],
    groupBy: [],
    effectiveSorts: [],
    serverFilters: [],
    clientFilters: [],
    pageFilters: [],
    unsupportedFilters: [],
    serverSorts: [],
    clientSorts: [],
    pageSorts: [],
    unsupportedSorts: [],
    serverGroups: [],
    clientGroups: [],
    pageGroups: [],
    unsupportedGroups: [],
    serverParams: {},
    scope: dataMode,
    criteriaKey: '',
    queryKey: ''
  }

  if (dataMode === 'server') {
    if (page !== undefined) plan.serverParams.page = plan.page
    if (pageSize !== undefined) plan.serverParams.pageSize = plan.pageSize
    if (plan.keyword) plan.serverParams.keyword = plan.keyword
  }

  const unsupportedLogic = completeFilters.length > 1 && !allowedFilterLogics.includes(normalizedFilterLogic)
  completeFilters.forEach(({ condition, index }) => {
    const field = fieldMap.get(condition.key)
    const capability = workspaceFieldCapability(field, 'filter', dataMode)
    const item = { condition, field, capability, index }

    if (!field) {
      plan.unsupportedFilters.push({ ...item, reason: 'unknown-field' })
      return
    }
    if (unsupportedLogic) {
      plan.unsupportedFilters.push({ ...item, reason: 'unsupported-logic' })
      return
    }
    if (capability.mode === 'disabled' || !WORKSPACE_QUERY_MODES.includes(capability.mode)) {
      plan.unsupportedFilters.push({ ...item, reason: 'disabled' })
      return
    }
    if (dataMode === 'server' && capability.mode !== 'server') {
      plan.unsupportedFilters.push({ ...item, reason: 'server-local-scope-disabled' })
      return
    }
    if (Array.isArray(capability.operators) && !capability.operators.includes(condition.operator)) {
      plan.unsupportedFilters.push({ ...item, reason: 'unsupported-operator' })
      return
    }
    if (capability.mode === 'server') {
      if (capability.custom) {
        serializedCustomFilters.push({
          field: capability.param,
          operator: condition.operator,
          value: condition.value ?? null
        })
        plan.serverFilters.push(item)
        return
      }
      const serialized = serializeServerCondition(condition, capability)
      if (!serialized || Object.keys(serialized).length === 0 || hasParamConflict(plan.serverParams, serialized)) {
        plan.unsupportedFilters.push({ ...item, reason: serialized ? 'duplicate-server-param' : 'invalid-serialization' })
        return
      }
      Object.assign(plan.serverParams, serialized)
      plan.serverFilters.push(item)
      return
    }

    plan[`${capability.mode}Filters`].push(item)
  })

  if (serializedCustomFilters.length > 0) {
    plan.serverParams.customFilters = stableJsonStringify(serializedCustomFilters)
  }

  classifyConditions({ conditions: normalizedSorts, capability: 'sort', fields: fieldMap, dataMode, plan })
  classifyConditions({ conditions: normalizedGroups, capability: 'group', fields: fieldMap, dataMode, plan })

  plan.filters = conditionsInRequestOrder(
    plan.serverFilters,
    plan.clientFilters,
    plan.pageFilters
  )
  plan.sorts = conditionsInRequestOrder(
    plan.serverSorts,
    plan.clientSorts,
    plan.pageSorts
  )
  plan.groupBy = conditionsInRequestOrder(
    plan.serverGroups,
    plan.clientGroups,
    plan.pageGroups
  )

  const effectiveServerSorts = mergeOrderingItems(plan.serverGroups, plan.serverSorts)
  const effectiveClientSorts = mergeOrderingItems(plan.clientGroups, plan.clientSorts)
  const effectivePageSorts = mergeOrderingItems(plan.pageGroups, plan.pageSorts)
  plan.effectiveServerSorts = effectiveServerSorts
  plan.effectiveClientSorts = effectiveClientSorts
  plan.effectivePageSorts = effectivePageSorts
  plan.effectiveSorts = mergeOrderingConditions(
    [...plan.serverGroups, ...plan.clientGroups, ...plan.pageGroups].map(item => item.condition),
    [...plan.serverSorts, ...plan.clientSorts, ...plan.pageSorts].map(item => item.condition)
  )

  const serverSorts = serializeServerOrdering(effectiveServerSorts)
  const serverGroups = serializeServerOrdering(plan.serverGroups)
  if (serverSorts) plan.serverParams.sorts = serverSorts
  if (serverGroups) plan.serverParams.groupBy = serverGroups

  plan.serverParams = sortObjectKeys(plan.serverParams)
  plan.keyword = plan.keyword || normalizeKeyword(plan.serverParams.keyword)
  plan.criteriaKey = stableWorkspaceQueryKey({
    mode: plan.mode,
    keyword: plan.keyword,
    filterLogic: plan.filterLogic,
    filters: plan.filters,
    sorts: plan.sorts,
    groupBy: plan.groupBy
  })
  plan.queryKey = stableWorkspaceQueryKey({
    criteriaKey: plan.criteriaKey,
    page: plan.page,
    pageSize: plan.pageSize,
    serverParams: plan.serverParams
  })

  if (plan.pageFilters.length || plan.pageSorts.length || plan.pageGroups.length) plan.scope = 'page'
  if (plan.clientFilters.length || plan.clientSorts.length || plan.clientGroups.length) plan.scope = 'client'
  return plan
}

export function stableWorkspaceQueryKey(value) {
  return stableSerialize(value)
}

export function buildWorkspaceResultScope({ serverTotal = 0, loadedRecords = [], visibleRecords = [] } = {}) {
  return {
    serverTotal: Number(serverTotal) || 0,
    loadedRecords: Array.isArray(loadedRecords) ? loadedRecords.length : Number(loadedRecords) || 0,
    visibleRecords: Array.isArray(visibleRecords) ? visibleRecords.length : Number(visibleRecords) || 0
  }
}

export function queryScopeLabel(mode) {
  return {
    server: '全量',
    client: '全部已加载',
    page: '仅当前页',
    unsupported: '不支持'
  }[mode] || ''
}

function classifyConditions({ conditions, capability, fields, dataMode, plan }) {
  ;(conditions || []).forEach((condition, index) => {
    if (!condition?.key) return
    const field = fields.get(condition.key)
    const declaration = workspaceFieldCapability(field, capability, dataMode)
    const item = { condition, field, capability: declaration, index }
    const plural = capability === 'sort' ? 'Sorts' : 'Groups'

    if (!field || declaration.mode === 'disabled' || !WORKSPACE_QUERY_MODES.includes(declaration.mode)) {
      plan[`unsupported${plural}`].push({ ...item, reason: field ? 'disabled' : 'unknown-field' })
      return
    }
    if (dataMode === 'server' && declaration.mode !== 'server') {
      plan[`unsupported${plural}`].push({ ...item, reason: 'server-local-scope-disabled' })
      return
    }
    if (declaration.mode === 'server' && !declaration.param && typeof declaration.serialize !== 'function') {
      plan[`unsupported${plural}`].push({ ...item, reason: 'invalid-serialization' })
      return
    }
    plan[`${declaration.mode}${plural}`].push(item)
  })
}

function serializeServerCondition(condition, capability) {
  const value = typeof capability.serialize === 'function'
    ? capability.serialize(condition.value, condition)
    : condition.value

  if (value === undefined || value === null || value === '') return null
  if (value && typeof value === 'object' && !Array.isArray(value)) return compactParams(value)
  if (!capability.param) return null
  return { [capability.param]: value }
}

function compactParams(params) {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''))
}

function serializeServerOrdering(items) {
  return items
    .map(item => {
      const key = String(item.capability?.param || '').trim()
      if (!key) return ''
      const direction = item.condition?.order === 'descending' ? 'desc' : 'asc'
      return `${key}:${direction}`
    })
    .filter(Boolean)
    .join(',')
}

function customFieldCapability(field, capability, dataMode) {
  const mode = dataMode === 'server' ? 'server' : 'client'
  if (capability === 'filter') {
    if (field.filterable === false) return { mode: 'disabled' }
    return {
      mode,
      custom: dataMode === 'server',
      param: customFieldReference(field),
      operators: customFieldOperators(field)
    }
  }
  if (capability === 'sort') {
    if (!field.sortable) return { mode: 'disabled' }
    return { mode, custom: dataMode === 'server', param: customFieldReference(field) }
  }
  if (capability === 'group') {
    if (!field.groupable) return { mode: 'disabled' }
    return { mode, custom: dataMode === 'server', param: customFieldReference(field) }
  }
  return { mode: 'disabled' }
}

function customFieldReference(field) {
  const rawCode = String(field?.fieldCode || field?.key || '').trim()
  const fieldCode = rawCode.startsWith('custom.') ? rawCode.slice('custom.'.length) : rawCode
  return fieldCode ? `custom.${fieldCode}` : ''
}

function customFieldOperators(field) {
  const rawType = String(field?.fieldType || field?.type || 'TEXT').trim().toUpperCase().replaceAll('-', '_')
  if (['NUMBER', 'CURRENCY', 'MONEY'].includes(rawType)) return CUSTOM_FIELD_OPERATORS.number
  if (['DATE', 'DATETIME'].includes(rawType)) return CUSTOM_FIELD_OPERATORS.date
  if (['SINGLE_SELECT', 'SINGLESELECT', 'SELECT', 'STATUS'].includes(rawType)) return CUSTOM_FIELD_OPERATORS.select
  if (['MULTI_SELECT', 'MULTISELECT'].includes(rawType)) return CUSTOM_FIELD_OPERATORS.multiSelect
  if (['BOOLEAN', 'CHECKBOX'].includes(rawType)) return CUSTOM_FIELD_OPERATORS.boolean
  return CUSTOM_FIELD_OPERATORS.text
}

function normalizeOrderingConditions(conditions, fields) {
  const seen = new Set()
  return (Array.isArray(conditions) ? conditions : []).filter(condition => {
    if (!condition?.key || !fields.has(condition.key) || seen.has(condition.key)) return false
    seen.add(condition.key)
    return true
  }).map(condition => ({
    key: condition.key,
    order: condition.order === 'descending' ? 'descending' : 'ascending'
  }))
}

function mergeOrderingItems(groupItems, sortItems) {
  const seen = new Set()
  return [...groupItems, ...sortItems].filter(item => {
    const key = item?.condition?.key
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function mergeOrderingConditions(groups, sorts) {
  const seen = new Set()
  return [...groups, ...sorts].filter(condition => {
    if (!condition?.key || seen.has(condition.key)) return false
    seen.add(condition.key)
    return true
  })
}

function conditionsInRequestOrder(...collections) {
  return collections
    .flat()
    .sort((left, right) => left.index - right.index)
    .map(item => item.condition)
}

function normalizePositiveInteger(value, fallback, max = Number.MAX_SAFE_INTEGER) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized) || normalized < 1) return fallback
  return Math.min(Math.round(normalized), max)
}

function normalizeKeyword(value) {
  return value === undefined || value === null ? '' : String(value).trim()
}

function sortObjectKeys(value) {
  return Object.fromEntries(Object.keys(value).sort().map(key => [key, value[key]]))
}

function stableSerialize(value) {
  if (Array.isArray(value)) return `[${value.map(stableSerialize).join(',')}]`
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableSerialize(value[key])}`).join(',')}}`
  }
  return JSON.stringify(value)
}

function stableJsonStringify(value) {
  return JSON.stringify(stableJsonValue(value))
}

function stableJsonValue(value) {
  if (Array.isArray(value)) return value.map(stableJsonValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, stableJsonValue(value[key])]))
  }
  return value
}

function hasParamConflict(current, next) {
  return Object.keys(next).some(key => Object.hasOwn(current, key))
}
