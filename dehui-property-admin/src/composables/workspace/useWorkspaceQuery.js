import { computed, unref, watch } from 'vue'
import { isCompleteFilter } from '../../utils/tableViewPrefs.js'
import {
  buildWorkspaceQueryPlan,
  queryScopeLabel,
  workspaceFieldCapability
} from '../../utils/workspaceQueryPlan.js'
import { createDebouncedTask, createLatestRequestGuard } from '../../utils/requestLifecycle.js'

export function useWorkspaceQuery(options) {
  const {
    fields,
    fieldByKey,
    tablePrefs,
    filters = () => [],
    queryConfig = {},
    queryState = {},
    queryLifecycle = {},
    updatePrefs,
    isColumnSortable
  } = options

  const queryRequestGuard = createLatestRequestGuard()
  const debouncedQueryTask = createDebouncedTask(
    (...args) => queryLifecycle.task?.(...args),
    queryLifecycle.delay ?? 300
  )

  const dataMode = queryConfig.dataMode || 'client'
  const filterLogicOptions = computed(() => (
    queryConfig.filterLogics || (dataMode === 'server' ? ['and'] : ['and', 'or'])
  ))
  const sortOptions = computed(() => resolveValue(fields).filter(column => (
    column.sortable && workspaceFieldCapability(column, 'sort', dataMode).mode !== 'disabled'
  )))
  const groupOptions = computed(() => resolveValue(fields).filter(column => (
    column.groupable && workspaceFieldCapability(column, 'group', dataMode).mode !== 'disabled'
  )))
  const filterOptions = computed(() => resolveValue(fields).filter(column => (
    column.filterable !== false && workspaceFieldCapability(column, 'filter', dataMode).mode !== 'disabled'
  )))
  const requestedSorts = computed(() => {
    if (Array.isArray(tablePrefs.sorts) && tablePrefs.sorts.length > 0) return tablePrefs.sorts
    if (tablePrefs.sortKey && tablePrefs.sortOrder) return [{ key: tablePrefs.sortKey, order: tablePrefs.sortOrder }]
    return []
  })
  const requestedGroups = computed(() => {
    if (Array.isArray(tablePrefs.groups) && tablePrefs.groups.length > 0) return tablePrefs.groups
    if (tablePrefs.groupKey) return [{ key: tablePrefs.groupKey, order: 'ascending' }]
    return []
  })
  const queryPlan = computed(() => buildWorkspaceQueryPlan({
    fields: resolveValue(fields),
    filters: tablePrefs.filters,
    sorts: requestedSorts.value,
    groups: requestedGroups.value,
    filterLogic: tablePrefs.filterLogic,
    page: resolveQueryStateValue(queryState.page),
    pageSize: resolveQueryStateValue(queryState.pageSize),
    keyword: resolveQueryStateValue(queryState.keyword),
    query: queryConfig
  }))
  const localFilters = computed(() => dataMode === 'server' ? [] : [
    ...queryPlan.value.clientFilters,
    ...queryPlan.value.pageFilters
  ].map(item => item.condition))
  const localSorts = computed(() => dataMode === 'server' ? [] : [
    ...queryPlan.value.effectiveClientSorts,
    ...queryPlan.value.effectivePageSorts
  ].map(item => item.condition))
  const localGroups = computed(() => dataMode === 'server' ? [] : [
    ...queryPlan.value.clientGroups,
    ...queryPlan.value.pageGroups
  ].map(item => item.condition))
  const serverGroups = computed(() => queryPlan.value.serverGroups.map(item => item.condition))
  const viewFilterItems = computed(() => (tablePrefs.filters || []).map((filter, index) => ({ filter, index }))
    .filter(item => isCompleteFilter(item.filter))
    .map(({ filter, index }) => {
      const column = resolveValue(fieldByKey).get(filter.key)
      const scope = filterScope(index)
      return {
        key: `view-filter-${index}`,
        index,
        label: column?.label || filter.key,
        value: filterValueText(filter, column),
        scope,
        scopeLabel: queryScopeLabel(scope),
        removable: true,
        source: 'view'
      }
    }))
  const fixedFilterItems = computed(() => filters().filter(isActiveFilterItem).map((item, index) => ({
      ...item,
      key: item.key || `query-filter-${index}`,
      scope: item.scope || dataMode,
      scopeLabel: item.scopeLabel || queryScopeLabel(dataMode === 'server' ? 'server' : 'client'),
      removable: false,
      source: 'query'
    })))
  const activeFilterItems = computed(() => [
    ...fixedFilterItems.value,
    ...viewFilterItems.value
  ])
  const activeFilterCount = computed(() => activeFilterItems.value.filter(item => item.scope !== 'unsupported').length)
  const groupCount = computed(() => supportedConditionCount(queryPlan.value, 'Groups'))
  const sortCount = computed(() => supportedConditionCount(queryPlan.value, 'Sorts'))
  const activeTableSort = computed(() => {
    if (Array.isArray(tablePrefs.sorts) && tablePrefs.sorts.length > 0) return tablePrefs.sorts[0]
    if (tablePrefs.sortKey && tablePrefs.sortOrder) return { key: tablePrefs.sortKey, order: tablePrefs.sortOrder }
    return { key: '', order: '' }
  })
  const serverQueryParams = computed(() => queryPlan.value.serverParams)
  const queryCriteriaKey = computed(() => queryPlan.value.criteriaKey)
  const queryRefreshKey = computed(() => `${queryPlan.value.criteriaKey}|pageSize:${queryPlan.value.pageSize}`)
  const queryNotices = computed(() => {
    const notices = []
    const unsupportedCount = queryPlan.value.unsupportedFilters.length
    const unsupportedSortCount = queryPlan.value.unsupportedSorts.length
    const unsupportedGroupCount = queryPlan.value.unsupportedGroups.length
    const pageCount = queryPlan.value.pageFilters.length + queryPlan.value.pageSorts.length + queryPlan.value.pageGroups.length
    if (unsupportedCount) notices.push(`${unsupportedCount} 个筛选条件当前接口不支持，已停止执行`)
    if (unsupportedSortCount) notices.push(`${unsupportedSortCount} 个排序条件当前接口不支持，已停止执行`)
    if (unsupportedGroupCount) notices.push(`${unsupportedGroupCount} 个分组条件当前数据模式不支持，已停止执行`)
    if (pageCount) notices.push(`${pageCount} 个条件仅作用于当前页，不影响服务端总数`)
    return notices
  })

  function updateFilters(value) {
    updatePrefs({ ...tablePrefs, filters: value })
  }

  function updateFilterLogic(value) {
    const allowed = filterLogicOptions.value
    updatePrefs({ ...tablePrefs, filterLogic: allowed.includes(value) ? value : allowed[0] || 'and' })
  }

  function removeViewFilter(index) {
    updateFilters(tablePrefs.filters.filter((_, filterIndex) => filterIndex !== index))
  }

  function updateGroups(value) {
    updatePrefs({ ...tablePrefs, groups: value, groupKey: '' })
  }

  function updateSorts(value) {
    updatePrefs({ ...tablePrefs, sorts: value, sortKey: '', sortOrder: '' })
  }

  function updateSortKey(value) {
    updatePrefs({ ...tablePrefs, sortKey: value })
  }

  function updateSortOrder(value) {
    updatePrefs({ ...tablePrefs, sortOrder: value })
  }

  function updateGroupKey(value) {
    updatePrefs({ ...tablePrefs, groupKey: value })
  }

  function handleTableSortChange({ column, prop, order } = {}) {
    const key = column?.columnKey || prop || column?.property
    if (!key || !order) {
      updateSorts([])
      return
    }
    if (!isColumnSortable(key)) return
    updateSorts([{ key, order }])
  }

  function filterScope(index) {
    const plan = queryPlan.value
    if (plan.serverFilters.some(item => item.index === index)) return 'server'
    if (plan.clientFilters.some(item => item.index === index)) return 'client'
    if (plan.pageFilters.some(item => item.index === index)) return 'page'
    return 'unsupported'
  }

  async function runLatestRequest(task, handlers = {}) {
    const requestId = queryRequestGuard.start()
    const requestPlan = queryPlan.value
    try {
      const value = await task(requestId, requestPlan)
      if (!queryRequestGuard.isCurrent(requestId)) return { accepted: false, stale: true }
      await handlers.onSuccess?.(value, requestPlan)
      return { accepted: true, ok: true, value }
    } catch (error) {
      if (!queryRequestGuard.isCurrent(requestId)) return { accepted: false, stale: true, error }
      await handlers.onError?.(error, requestPlan)
      return { accepted: true, ok: false, error }
    }
  }

  const stopAutoRefresh = queryLifecycle.autoRefresh === true && typeof queryLifecycle.task === 'function'
    ? watch(queryRefreshKey, (next, previous) => {
      if (previous === undefined || next === previous) return
      queryLifecycle.resetPage?.(1)
      debouncedQueryTask.schedule(queryPlan.value)
    })
    : () => {}

  function disposeQueryLifecycle() {
    stopAutoRefresh()
    debouncedQueryTask.cancel()
    queryRequestGuard.dispose()
  }

  return {
    dataMode,
    filterLogicOptions,
    sortOptions,
    groupOptions,
    filterOptions,
    requestedSorts,
    requestedGroups,
    queryPlan,
    localFilters,
    localSorts,
    localGroups,
    serverGroups,
    viewFilterItems,
    activeFilterItems,
    activeFilterCount,
    groupCount,
    sortCount,
    activeTableSort,
    serverQueryParams,
    queryCriteriaKey,
    queryRefreshKey,
    queryNotices,
    queryRequestGuard,
    runLatestRequest,
    debouncedQueryTask,
    disposeQueryLifecycle,
    updateFilters,
    updateFilterLogic,
    removeViewFilter,
    updateGroups,
    updateSorts,
    updateSortKey,
    updateSortOrder,
    updateGroupKey,
    handleTableSortChange,
    filterScope
  }
}

function resolveValue(source) {
  return unref(source)
}

function resolveQueryStateValue(source) {
  return typeof source === 'function' ? source() : unref(source)
}

function isActiveFilterItem(item) {
  if (!item || item.active === false) return false
  if (item.value === 0 || item.value === false) return true
  if (Array.isArray(item.value)) return item.value.length > 0
  return item.value !== undefined && item.value !== null && String(item.value).trim() !== ''
}

function supportedConditionCount(plan, plural) {
  return ['server', 'client', 'page'].reduce((count, mode) => (
    count + (plan[`${mode}${plural}`]?.length || 0)
  ), 0)
}

function filterValueText(filter, column) {
  const operator = operatorText(filter.operator)
  if (['empty', 'notEmpty', 'today', 'thisWeek', 'thisMonth'].includes(filter.operator)) return operator
  const values = Array.isArray(filter.value) ? filter.value : [filter.value]
  const separator = ['between', 'dateRange'].includes(filter.operator) ? ' 至 ' : '、'
  const value = values.map(item => fieldOptionLabel(column, item)).join(separator)
  return `${operator} ${value}`
}

function fieldOptionLabel(column, value) {
  const source = typeof column?.options === 'function' ? column.options() : column?.options
  if (!Array.isArray(source)) return value
  const option = source.find(item => {
    const optionValue = item && typeof item === 'object' ? (item.value ?? item.label) : item
    return String(optionValue) === String(value)
  })
  if (option && typeof option === 'object') return option.label ?? option.value ?? value
  return option ?? value
}

function operatorText(operator) {
  return {
    contains: '包含',
    notContains: '不包含',
    containsAny: '包含任一',
    equals: '等于',
    notEquals: '不等于',
    greaterThan: '大于',
    greaterThanOrEqual: '大于等于',
    lessThan: '小于',
    lessThanOrEqual: '小于等于',
    before: '早于',
    after: '晚于',
    between: '介于',
    empty: '为空',
    notEmpty: '不为空',
    today: '今天',
    thisWeek: '本周',
    thisMonth: '本月',
    dateRange: '日期范围'
  }[operator] || operator
}
