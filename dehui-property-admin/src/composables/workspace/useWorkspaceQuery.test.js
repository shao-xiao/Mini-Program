import assert from 'node:assert/strict'
import test from 'node:test'
import { computed, nextTick, reactive } from 'vue'
import { useWorkspaceQuery } from './useWorkspaceQuery.js'

function createServerQuery(queryLifecycle = {}, queryState = {}) {
  const fields = computed(() => [
    {
      key: 'status',
      label: '状态',
      filterable: true,
      query: { mode: 'server', param: 'status', operators: ['equals'] }
    },
    {
      key: 'priority',
      label: '优先级',
      filterable: true,
      query: { mode: 'page', operators: ['equals'] }
    },
    { key: 'name', label: '名称', filterable: true, sortable: true }
  ])
  const tablePrefs = reactive({
    filterLogic: 'and',
    filters: [
      { key: 'status', operator: 'equals', value: 'FAULT' },
      { key: 'priority', operator: 'equals', value: 'HIGH' },
      { key: 'name', operator: 'contains', value: 'A' }
    ],
    groups: [],
    sorts: [{ key: 'name', order: 'ascending' }],
    sortKey: '',
    sortOrder: '',
    groupKey: ''
  })
  const query = useWorkspaceQuery({
    fields,
    fieldByKey: computed(() => new Map(fields.value.map(field => [field.key, field]))),
    tablePrefs,
    queryConfig: { dataMode: 'server', filterLogics: ['and'] },
    queryState,
    queryLifecycle,
    updatePrefs: value => Object.assign(tablePrefs, value),
    isColumnSortable: () => false
  })
  return { tablePrefs, query }
}

test('workspace query rejects local-only conditions in server mode', () => {
  const { query } = createServerQuery()
  assert.deepEqual(query.serverQueryParams.value, { status: 'FAULT' })
  assert.equal(query.queryPlan.value.pageFilters.length, 0)
  assert.equal(query.queryPlan.value.unsupportedFilters.length, 2)
  assert.equal(query.queryPlan.value.unsupportedSorts.length, 1)
  assert.equal(query.activeFilterCount.value, 1)
  assert.doesNotMatch(query.queryNotices.value.join(' '), /仅作用于当前页/)
  assert.match(query.queryNotices.value.join(' '), /2 个筛选条件当前接口不支持/)
  assert.match(query.queryNotices.value.join(' '), /排序条件当前接口不支持/)
})

test('workspace query owns filter mutations and table sort compatibility', () => {
  const { tablePrefs, query } = createServerQuery()
  query.updateFilters([{ key: 'status', operator: 'equals', value: 'NORMAL' }])
  query.updateFilterLogic('or')
  query.handleTableSortChange({ prop: 'name', order: 'descending' })

  assert.deepEqual(tablePrefs.filters, [{ key: 'status', operator: 'equals', value: 'NORMAL' }])
  assert.equal(tablePrefs.filterLogic, 'and')
  assert.deepEqual(tablePrefs.sorts, [{ key: 'name', order: 'ascending' }])
})

test('workspace query owns debounce and latest-request lifecycle state', async () => {
  const calls = []
  const { query } = createServerQuery({
    task: value => calls.push(value),
    delay: 10
  })

  query.debouncedQueryTask.schedule('old')
  query.debouncedQueryTask.schedule('latest')
  await new Promise(resolve => setTimeout(resolve, 30))
  assert.deepEqual(calls, ['latest'])

  const requestId = query.queryRequestGuard.start()
  assert.equal(query.queryRequestGuard.isCurrent(requestId), true)
  query.disposeQueryLifecycle()
  assert.equal(query.queryRequestGuard.isCurrent(requestId), false)
})

test('workspace query resets pagination and debounces one refresh when criteria change', async () => {
  const calls = []
  const queryState = reactive({ page: 4, pageSize: 50, keyword: '' })
  const { query } = createServerQuery({
    autoRefresh: true,
    delay: 5,
    resetPage: page => { queryState.page = page },
    task: plan => calls.push({
      page: plan.page,
      pageSize: plan.pageSize,
      status: plan.serverParams.status
    })
  }, queryState)

  query.updateFilters([{ key: 'status', operator: 'equals', value: 'NORMAL' }])
  await nextTick()
  await new Promise(resolve => setTimeout(resolve, 20))

  assert.equal(queryState.page, 1)
  assert.deepEqual(calls, [{ page: 1, pageSize: 50, status: 'NORMAL' }])
  query.disposeQueryLifecycle()
})

test('workspace query ignores incomplete filters in the active count and query plan', () => {
  const { query } = createServerQuery()
  query.updateFilters([
    { key: 'status', operator: 'equals', value: 'NORMAL' },
    { key: 'status', operator: 'equals', value: '' },
    { key: '', operator: 'equals', value: 'FAULT' }
  ])

  assert.equal(query.activeFilterCount.value, 1)
  assert.deepEqual(query.queryPlan.value.filters, [
    { key: 'status', operator: 'equals', value: 'NORMAL' }
  ])
})

test('workspace query accepts only the latest request response and keeps its request plan snapshot', async () => {
  const applied = []
  let resolveFirst
  let resolveSecond
  const { query } = createServerQuery()

  const first = query.runLatestRequest(
    () => new Promise(resolve => { resolveFirst = resolve }),
    { onSuccess: (value, plan) => applied.push({ value, status: plan.serverParams.status }) }
  )

  query.updateFilters([{ key: 'status', operator: 'equals', value: 'NORMAL' }])
  const second = query.runLatestRequest(
    () => new Promise(resolve => { resolveSecond = resolve }),
    { onSuccess: (value, plan) => applied.push({ value, status: plan.serverParams.status }) }
  )

  resolveSecond('latest')
  const secondResult = await second
  resolveFirst('stale')
  const firstResult = await first

  assert.deepEqual(applied, [{ value: 'latest', status: 'NORMAL' }])
  assert.deepEqual(secondResult, { accepted: true, ok: true, value: 'latest' })
  assert.equal(firstResult.accepted, false)
  assert.equal(firstResult.stale, true)
})
