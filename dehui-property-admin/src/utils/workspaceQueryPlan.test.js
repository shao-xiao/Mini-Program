import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildWorkspaceQueryPlan,
  buildWorkspaceResultScope,
  workspaceFieldCapability
} from './workspaceQueryPlan.js'
import { createEquipmentWorkspaceConfig } from '../workspaceConfigs/equipment.js'
import { createWorkOrderWorkspaceConfig } from '../workspaceConfigs/workorder.js'
import {
  createEnergyMeterWorkspaceConfig,
  createEnergyReadingWorkspaceConfig
} from '../workspaceConfigs/energyWorkspaceConfig.js'

const serverFields = [
  {
    key: 'status',
    query: { mode: 'server', param: 'status', operators: ['equals'] }
  },
  {
    key: 'createdTime',
    query: {
      mode: 'server',
      operators: ['dateRange'],
      serialize: value => ({ startTime: value?.[0], endTime: value?.[1] })
    }
  },
  { key: 'title', sortable: true }
]

test('server filters serialize only declared operators and never fall back to current-page filtering', () => {
  const plan = buildWorkspaceQueryPlan({
    fields: serverFields,
    filters: [
      { key: 'status', operator: 'equals', value: 'OPEN' },
      { key: 'title', operator: 'contains', value: '漏水' },
      { key: 'status', operator: 'notEquals', value: 'CLOSED' }
    ],
    query: { dataMode: 'server', filterLogics: ['and'] }
  })

  assert.deepEqual(plan.serverParams, { status: 'OPEN' })
  assert.deepEqual(plan.filters, [
    { key: 'status', operator: 'equals', value: 'OPEN' }
  ])
  assert.equal(plan.serverFilters.length, 1)
  assert.equal(plan.pageFilters.length, 0)
  assert.deepEqual(plan.unsupportedFilters.map(item => item.reason), ['disabled', 'unsupported-operator'])
})

test('server filter serializer may map one condition to existing API range params', () => {
  const plan = buildWorkspaceQueryPlan({
    fields: serverFields,
    filters: [{ key: 'createdTime', operator: 'dateRange', value: ['2026-07-01', '2026-07-15'] }],
    query: { dataMode: 'server', filterLogics: ['and'] }
  })

  assert.deepEqual(plan.serverParams, { startTime: '2026-07-01', endTime: '2026-07-15' })
})

test('unsupported OR logic is explicit and produces no server params', () => {
  const plan = buildWorkspaceQueryPlan({
    fields: serverFields,
    filters: [
      { key: 'status', operator: 'equals', value: 'OPEN' },
      { key: 'createdTime', operator: 'dateRange', value: ['2026-07-01', '2026-07-15'] }
    ],
    filterLogic: 'or',
    query: { dataMode: 'server', filterLogics: ['and'] }
  })

  assert.deepEqual(plan.serverParams, {})
  assert.equal(plan.filterLogic, 'or')
  assert.deepEqual(plan.filters, [])
  assert.equal(plan.unsupportedFilters.length, 2)
  assert.ok(plan.unsupportedFilters.every(item => item.reason === 'unsupported-logic'))
})

test('server-paginated sorts reject undeclared and current-page-only scopes', () => {
  const unsupported = buildWorkspaceQueryPlan({
    fields: serverFields,
    sorts: [{ key: 'title', order: 'ascending' }],
    query: { dataMode: 'server' }
  })
  assert.equal(unsupported.unsupportedSorts.length, 1)
  assert.equal(unsupported.pageSorts.length, 0)

  const pageField = { ...serverFields[2], query: { sort: { mode: 'page' } } }
  const page = buildWorkspaceQueryPlan({
    fields: [pageField],
    sorts: [{ key: 'title', order: 'ascending' }],
    query: { dataMode: 'server' }
  })
  assert.equal(page.pageSorts.length, 0)
  assert.equal(page.unsupportedSorts.length, 1)
  assert.equal(page.unsupportedSorts[0].reason, 'server-local-scope-disabled')
  assert.equal(page.scope, 'server')
})

test('client datasets retain local capabilities while server datasets require declarations', () => {
  assert.equal(workspaceFieldCapability({ key: 'name', sortable: true }, 'sort', 'client').mode, 'client')
  assert.equal(workspaceFieldCapability({ key: 'name', sortable: true }, 'sort', 'server').mode, 'disabled')
})

test('result scope keeps server total, loaded rows and visible rows distinct', () => {
  assert.deepEqual(buildWorkspaceResultScope({
    serverTotal: 500,
    loadedRecords: Array.from({ length: 20 }),
    visibleRecords: Array.from({ length: 3 })
  }), {
    serverTotal: 500,
    loadedRecords: 20,
    visibleRecords: 3
  })
})

test('all migrated server workspace presets compile without unsupported query conditions', () => {
  const configs = [
    createEquipmentWorkspaceConfig(),
    createWorkOrderWorkspaceConfig({ currentUserId: 7 }),
    createEnergyMeterWorkspaceConfig(),
    createEnergyReadingWorkspaceConfig()
  ]

  configs.forEach(config => {
    assert.equal(config.query.dataMode, 'server')
    config.views.forEach(view => {
      const plan = buildWorkspaceQueryPlan({
        fields: config.fields,
        filters: view.prefs?.filters,
        sorts: view.prefs?.sorts,
        groups: view.prefs?.groups,
        filterLogic: view.prefs?.filterLogic,
        query: config.query
      })
      assert.deepEqual(plan.unsupportedFilters, [], `${config.id}/${view.id} has unsupported filters`)
      assert.deepEqual(plan.unsupportedSorts, [], `${config.id}/${view.id} has unsupported sorts`)
      assert.deepEqual(plan.unsupportedGroups, [], `${config.id}/${view.id} has unsupported groups`)
    })
  })
})


test('server sorts and groups serialize as whitelisted ordering instructions', () => {
  const fields = [
    { key: 'status', sortable: true, groupable: true, query: { sort: { mode: 'server', param: 'status' }, group: { mode: 'server', param: 'status' } } },
    { key: 'updatedAt', sortable: true, query: { sort: { mode: 'server', param: 'updatedAt' } } }
  ]
  const plan = buildWorkspaceQueryPlan({
    fields,
    sorts: [{ key: 'updatedAt', order: 'descending' }],
    groups: [{ key: 'status', order: 'ascending' }],
    query: { dataMode: 'server' }
  })

  assert.equal(plan.serverParams.sorts, 'status:asc,updatedAt:desc')
  assert.equal(plan.serverParams.groupBy, 'status:asc')
  assert.equal(plan.serverSorts.length, 1)
  assert.equal(plan.serverGroups.length, 1)
})

test('query plan exposes stable client/server structure and explicit pagination state', () => {
  const fields = [
    { key: 'name', sortable: true, groupable: true },
    { key: 'status', query: { mode: 'server', param: 'status', operators: ['equals'] } }
  ]
  const client = buildWorkspaceQueryPlan({
    fields,
    page: 2,
    pageSize: 50,
    keyword: '  设备  ',
    sorts: [{ key: 'name', order: 'descending' }],
    groups: [{ key: 'name', order: 'ascending' }],
    query: { dataMode: 'client' }
  })
  assert.equal(client.mode, 'client')
  assert.equal(client.page, 2)
  assert.equal(client.pageSize, 50)
  assert.equal(client.keyword, '设备')
  assert.equal(client.filterLogic, 'and')
  assert.deepEqual(client.serverParams, {})
  assert.deepEqual(client.effectiveSorts, [{ key: 'name', order: 'ascending' }])

  const server = buildWorkspaceQueryPlan({
    fields,
    page: 3,
    pageSize: 30,
    keyword: '设备',
    filters: [{ key: 'status', operator: 'equals', value: 'ACTIVE' }],
    query: { dataMode: 'server' }
  })
  assert.equal(server.mode, 'server')
  assert.deepEqual(server.serverParams, {
    keyword: '设备',
    page: 3,
    pageSize: 30,
    status: 'ACTIVE'
  })
  assert.match(server.queryKey, /pageSize/)
})

test('group ordering has priority and duplicate group/sort fields are removed deterministically', () => {
  const fields = [
    { key: 'building', sortable: true, groupable: true, query: { sort: { mode: 'server', param: 'building' }, group: { mode: 'server', param: 'building' } } },
    { key: 'status', sortable: true, groupable: true, query: { sort: { mode: 'server', param: 'status' }, group: { mode: 'server', param: 'status' } } },
    { key: 'updatedAt', sortable: true, query: { sort: { mode: 'server', param: 'updatedAt' } } }
  ]
  const plan = buildWorkspaceQueryPlan({
    fields,
    groups: [
      { key: 'building', order: 'ascending' },
      { key: 'status', order: 'descending' },
      { key: 'building', order: 'descending' }
    ],
    sorts: [
      { key: 'status', order: 'ascending' },
      { key: 'updatedAt', order: 'descending' },
      { key: 'updatedAt', order: 'ascending' },
      { key: 'missing', order: 'ascending' }
    ],
    query: { dataMode: 'server' }
  })

  assert.deepEqual(plan.groupBy, [
    { key: 'building', order: 'ascending' },
    { key: 'status', order: 'descending' }
  ])
  assert.deepEqual(plan.sorts, [
    { key: 'status', order: 'ascending' },
    { key: 'updatedAt', order: 'descending' }
  ])
  assert.deepEqual(plan.effectiveSorts, [
    { key: 'building', order: 'ascending' },
    { key: 'status', order: 'descending' },
    { key: 'updatedAt', order: 'descending' }
  ])
  assert.equal(plan.serverParams.groupBy, 'building:asc,status:desc')
  assert.equal(plan.serverParams.sorts, 'building:asc,status:desc,updatedAt:desc')
})

test('query keys are stable when equivalent server params are inserted in different object order', () => {
  const fields = [
    { key: 'createdAt', query: { mode: 'server', operators: ['dateRange'], serialize: value => ({ end: value[1], start: value[0] }) } }
  ]
  const first = buildWorkspaceQueryPlan({
    fields,
    filters: [{ key: 'createdAt', operator: 'dateRange', value: ['2026-07-01', '2026-07-31'] }],
    query: { dataMode: 'server' }
  })
  const secondFields = [
    { key: 'createdAt', query: { mode: 'server', operators: ['dateRange'], serialize: value => ({ start: value[0], end: value[1] }) } }
  ]
  const second = buildWorkspaceQueryPlan({
    fields: secondFields,
    filters: [{ key: 'createdAt', operator: 'dateRange', value: ['2026-07-01', '2026-07-31'] }],
    query: { dataMode: 'server' }
  })

  assert.equal(first.queryKey, second.queryKey)
  assert.deepEqual(Object.keys(first.serverParams), ['end', 'start'])
})

test('unsupported fields never enter the executable query plan', () => {
  const fields = [
    {
      key: 'status',
      sortable: true,
      groupable: true,
      query: {
        mode: 'server',
        param: 'status',
        operators: ['equals'],
        sort: { mode: 'server', param: 'status' },
        group: { mode: 'server', param: 'status' }
      }
    }
  ]
  const plan = buildWorkspaceQueryPlan({
    fields,
    filters: [
      { key: 'missing', operator: 'equals', value: 'x' },
      { key: 'status', operator: 'equals', value: 'ACTIVE' }
    ],
    sorts: [{ key: 'missing', order: 'ascending' }, { key: 'status', order: 'descending' }],
    groups: [{ key: 'missing', order: 'ascending' }, { key: 'status', order: 'ascending' }],
    query: { dataMode: 'server' }
  })

  assert.deepEqual(plan.filters, [{ key: 'status', operator: 'equals', value: 'ACTIVE' }])
  assert.deepEqual(plan.sorts, [{ key: 'status', order: 'descending' }])
  assert.deepEqual(plan.groupBy, [{ key: 'status', order: 'ascending' }])
  assert.equal(plan.serverParams.sorts, 'status:asc')
})

test('filter logic participates in stable query identity', () => {
  const fields = [
    { key: 'name', filterable: true },
    { key: 'status', filterable: true }
  ]
  const input = {
    fields,
    filters: [
      { key: 'name', operator: 'contains', value: 'A' },
      { key: 'status', operator: 'equals', value: 'ACTIVE' }
    ],
    query: { dataMode: 'client', filterLogics: ['and', 'or'] }
  }
  const andPlan = buildWorkspaceQueryPlan({ ...input, filterLogic: 'and' })
  const orPlan = buildWorkspaceQueryPlan({ ...input, filterLogic: 'or' })

  assert.notEqual(andPlan.criteriaKey, orPlan.criteriaKey)
  assert.equal(andPlan.filterLogic, 'and')
  assert.equal(orPlan.filterLogic, 'or')
})

test('server custom fields aggregate filters and namespace ordering without page-local fallback', () => {
  const fields = [
    {
      key: 'customer_level',
      fieldCode: 'customer_level',
      fieldType: 'SINGLE_SELECT',
      custom: true,
      sortable: true,
      groupable: true
    },
    {
      key: 'custom_score',
      fieldType: 'NUMBER',
      custom: true,
      sortable: true,
      groupable: true
    }
  ]
  const plan = buildWorkspaceQueryPlan({
    fields,
    filters: [
      { key: 'customer_level', operator: 'containsAny', value: ['A', 'B'] },
      { key: 'custom_score', operator: 'greaterThanOrEqual', value: 80 }
    ],
    groups: [{ key: 'customer_level', order: 'ascending' }],
    sorts: [
      { key: 'customer_level', order: 'descending' },
      { key: 'custom_score', order: 'descending' }
    ],
    query: { dataMode: 'server', filterLogics: ['and'] }
  })

  assert.deepEqual(JSON.parse(plan.serverParams.customFilters), [
    { field: 'custom.customer_level', operator: 'containsAny', value: ['A', 'B'] },
    { field: 'custom.custom_score', operator: 'greaterThanOrEqual', value: 80 }
  ])
  assert.equal(plan.serverParams.groupBy, 'custom.customer_level:asc')
  assert.equal(plan.serverParams.sorts, 'custom.customer_level:asc,custom.custom_score:desc')
  assert.equal(plan.serverFilters.length, 2)
  assert.deepEqual(plan.clientFilters, [])
  assert.deepEqual(plan.pageFilters, [])
  assert.deepEqual(plan.unsupportedFilters, [])
})

test('client custom fields retain complete-array filtering sorting and grouping', () => {
  const field = {
    key: 'custom_tags',
    fieldType: 'MULTI_SELECT',
    custom: true,
    sortable: true,
    groupable: true
  }
  const plan = buildWorkspaceQueryPlan({
    fields: [field],
    filters: [{ key: field.key, operator: 'containsAll', value: ['urgent', 'vip'] }],
    sorts: [{ key: field.key, order: 'ascending' }],
    groups: [{ key: field.key, order: 'descending' }],
    query: { dataMode: 'client', filterLogics: ['and', 'or'] }
  })

  assert.equal(plan.clientFilters.length, 1)
  assert.equal(plan.clientSorts.length, 1)
  assert.equal(plan.clientGroups.length, 1)
  assert.deepEqual(plan.serverParams, {})
})

test('custom-field capability rejects operators and grouping disabled by the stored definition', () => {
  const field = {
    key: 'custom_note',
    fieldType: 'LONG_TEXT',
    custom: true,
    sortable: false,
    groupable: false
  }
  const plan = buildWorkspaceQueryPlan({
    fields: [field],
    filters: [{ key: field.key, operator: 'greaterThan', value: 1 }],
    sorts: [{ key: field.key, order: 'ascending' }],
    groups: [{ key: field.key, order: 'ascending' }],
    query: { dataMode: 'server' }
  })

  assert.equal(plan.unsupportedFilters[0].reason, 'unsupported-operator')
  assert.equal(plan.unsupportedSorts[0].reason, 'disabled')
  assert.equal(plan.unsupportedGroups[0].reason, 'disabled')
  assert.deepEqual(plan.serverParams, {})
})
