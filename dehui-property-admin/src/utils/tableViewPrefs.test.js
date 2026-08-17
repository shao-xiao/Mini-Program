import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildGroupedRowsByConditions,
  buildSequentialGroupedRowsByConditions,
  buildGroupedRows,
  filterRecordsByConditions,
  normalizeTableViewPrefs,
  sortRecordsByConditions,
  sortRecordsByField
} from './tableViewPrefs.js'

const columns = ['name', 'type', 'status', 'location']

test('normalizeTableViewPrefs keeps valid saved view and removes stale columns', () => {
  const prefs = normalizeTableViewPrefs(
    {
      visibleColumns: ['name', 'removed', 'status'],
      rowDensity: 'compact',
      sortKey: 'name',
      sortOrder: 'descending',
      groupKey: 'status'
    },
    {
      visibleColumns: ['name', 'type', 'status'],
      rowDensity: 'standard',
      sortKey: '',
      sortOrder: '',
      groupKey: ''
    },
    columns
  )

  assert.deepEqual(prefs, {
    visibleColumns: ['name', 'status'],
    columnOrder: ['name', 'type', 'status', 'location'],
    columnWidths: {},
    fixedColumns: {},
    rowDensity: 'compact',
    pageSize: 20,
    sortKey: 'name',
    sortOrder: 'descending',
    groupKey: 'status',
    customColumns: [],
    filterLogic: 'and',
    filters: [],
    groups: [],
    sorts: []
  })
})

test('normalizeTableViewPrefs falls back when saved view is empty or invalid', () => {
  const prefs = normalizeTableViewPrefs(
    {
      visibleColumns: ['removed'],
      rowDensity: 'tiny',
      sortKey: 'removed',
      sortOrder: 'sideways',
      groupKey: 'removed'
    },
    {
      visibleColumns: ['name', 'type'],
      rowDensity: 'comfortable',
      sortKey: 'type',
      sortOrder: 'ascending',
      groupKey: ''
    },
    columns
  )

  assert.deepEqual(prefs, {
    visibleColumns: ['name', 'type'],
    columnOrder: ['name', 'type', 'status', 'location'],
    columnWidths: {},
    fixedColumns: {},
    rowDensity: 'comfortable',
    pageSize: 20,
    sortKey: 'type',
    sortOrder: 'ascending',
    groupKey: '',
    customColumns: [],
    filterLogic: 'and',
    filters: [],
    groups: [],
    sorts: []
  })
})

test('normalizeTableViewPrefs keeps incomplete view filters for editing', () => {
  const prefs = normalizeTableViewPrefs(
    {
      visibleColumns: ['name', 'status'],
      filters: [
        { key: 'name', operator: 'contains', value: '' },
        { key: 'status', operator: 'equals', value: 'NORMAL' },
        { key: 'location', operator: 'empty', value: '' }
      ]
    },
    {
      visibleColumns: ['name', 'status'],
      rowDensity: 'standard'
    },
    columns
  )

  assert.deepEqual(prefs.filters, [
    { key: 'name', operator: 'contains', value: '' },
    { key: 'status', operator: 'equals', value: 'NORMAL' },
    { key: 'location', operator: 'empty', value: '' }
  ])
})

test('normalizeTableViewPrefs upgrades legacy select fields and keeps option metadata', () => {
  const prefs = normalizeTableViewPrefs({
    filterLogic: 'or',
    customColumns: [
      {
        key: 'custom_status',
        label: '处理状态',
        type: 'singleSelect',
        options: [{ label: '故障', value: 'FAULT', tone: 'danger' }]
      }
    ]
  }, {
    visibleColumns: ['name']
  }, ['name'])

  assert.equal(prefs.customColumns[0].type, 'select')
  assert.equal(prefs.filterLogic, 'or')
  assert.deepEqual(prefs.customColumns[0].options, [
    { label: '故障', value: 'FAULT', tone: 'danger' }
  ])
  assert.equal(prefs.customColumns[0].groupable, true)
})

test('filterRecordsByConditions ignores incomplete view filters', () => {
  const records = [
    { id: 1, name: 'A-01', status: 'NORMAL' },
    { id: 2, name: 'B-01', status: 'FAULT' }
  ]
  const columns = [
    { key: 'name', label: '名称' },
    { key: 'status', label: '状态' }
  ]

  const filtered = filterRecordsByConditions(
    records,
    [
      { key: 'name', operator: 'contains', value: '' },
      { key: 'status', operator: 'equals', value: 'NORMAL' }
    ],
    columns
  )

  assert.deepEqual(filtered.map(item => item.name), ['A-01'])
})

test('filterRecordsByConditions supports AND and OR condition groups', () => {
  const records = [
    { id: 1, name: '消防泵', status: 'NORMAL' },
    { id: 2, name: '客梯', status: 'FAULT' },
    { id: 3, name: '排风机', status: 'NORMAL' }
  ]
  const filterColumns = [
    { key: 'name', label: '名称', type: 'text' },
    { key: 'status', label: '状态', type: 'status' }
  ]
  const filters = [
    { key: 'name', operator: 'contains', value: '消防' },
    { key: 'status', operator: 'equals', value: 'FAULT' }
  ]

  assert.deepEqual(filterRecordsByConditions(records, filters, filterColumns, 'and'), [])
  assert.deepEqual(
    filterRecordsByConditions(records, filters, filterColumns, 'or').map(item => item.id),
    [1, 2]
  )
})

test('sortRecordsByField sorts numbers and text without mutating source records', () => {
  const records = [
    { name: 'B', order: 3 },
    { name: 'A', order: 12 },
    { name: 'C', order: 1 }
  ]

  const byNumber = sortRecordsByField(records, 'order', 'ascending')
  const byName = sortRecordsByField(records, 'name', 'descending')

  assert.deepEqual(byNumber.map(item => item.order), [1, 3, 12])
  assert.deepEqual(byName.map(item => item.name), ['C', 'B', 'A'])
  assert.deepEqual(records.map(item => item.name), ['B', 'A', 'C'])
})

test('buildGroupedRows returns group headers with original rows', () => {
  const rows = buildGroupedRows(
    [
      { id: 1, status: 'NORMAL' },
      { id: 2, status: 'FAULT' },
      { id: 3, status: 'NORMAL' }
    ],
    'status',
    value => (value === 'NORMAL' ? '正常' : '异常')
  )

  assert.deepEqual(rows, [
    { __group: true, key: 'NORMAL', label: '正常', count: 2 },
    { id: 1, status: 'NORMAL' },
    { id: 3, status: 'NORMAL' },
    { __group: true, key: 'FAULT', label: '异常', count: 1 },
    { id: 2, status: 'FAULT' }
  ])
})

test('filterRecordsByConditions, sortRecordsByConditions and buildGroupedRowsByConditions support view rules', () => {
  const records = [
    { id: 1, name: 'A-02', status: 'NORMAL', area: 'B' },
    { id: 2, name: 'A-01', status: 'NORMAL', area: 'A' },
    { id: 3, name: 'B-01', status: 'FAULT', area: 'A' }
  ]
  const columns = [
    { key: 'name', label: '名称' },
    { key: 'status', label: '状态' },
    { key: 'area', label: '区域' }
  ]

  const filtered = filterRecordsByConditions(records, [{ key: 'status', operator: 'equals', value: 'NORMAL' }], columns)
  const sorted = sortRecordsByConditions(filtered, [{ key: 'area', order: 'ascending' }, { key: 'name', order: 'ascending' }], columns)
  const grouped = buildGroupedRowsByConditions(sorted, [{ key: 'area', order: 'ascending' }], columns, value => `区域 ${value}`)

  assert.deepEqual(sorted.map(item => item.name), ['A-01', 'A-02'])
  assert.deepEqual(grouped.map(item => item.__group ? item.label : item.name), ['区域 A', 'A-01', '区域 B', 'A-02'])
})

test('filterRecordsByConditions supports select-style array values', () => {
  const records = [
    { id: 1, tenantName: '上海澄木设计事务所', status: '待缴' },
    { id: 2, tenantName: '上海云启科技有限公司', status: '已缴' },
    { id: 3, tenantName: '德汇咖啡服务有限公司', status: '已取消' }
  ]
  const columns = [
    { key: 'tenant', label: '租户', valueGetter: row => row.tenantName },
    { key: 'status', label: '收款状态' }
  ]

  const filtered = filterRecordsByConditions(
    records,
    [{ key: 'status', operator: 'equals', value: ['待缴', '已缴'] }],
    columns
  )

  assert.deepEqual(filtered.map(item => item.tenantName), ['上海澄木设计事务所', '上海云启科技有限公司'])
})

test('filterRecordsByConditions applies multi-select operators to array-backed custom values', () => {
  const records = [
    { id: 1, customFields: { custom_tags: ['urgent', 'vip'] } },
    { id: 2, customFields: { custom_tags: ['urgent'] } },
    { id: 3, customFields: { custom_tags: [] } }
  ]
  const columns = [{
    key: 'custom_tags',
    custom: true,
    type: 'multi-select',
    valueGetter: record => record.customFields.custom_tags
  }]

  assert.deepEqual(filterRecordsByConditions(records, [
    { key: 'custom_tags', operator: 'containsAll', value: ['urgent', 'vip'] }
  ], columns).map(item => item.id), [1])
  assert.deepEqual(filterRecordsByConditions(records, [
    { key: 'custom_tags', operator: 'containsAny', value: ['vip'] }
  ], columns).map(item => item.id), [1])
  assert.deepEqual(filterRecordsByConditions(records, [
    { key: 'custom_tags', operator: 'notContains', value: ['vip'] }
  ], columns).map(item => item.id), [2, 3])
})

test('normalizeTableViewPrefs keeps ordered, resized and fixed columns within safe bounds', () => {
  const prefs = normalizeTableViewPrefs(
    {
      visibleColumns: ['name', 'type', 'status'],
      columnOrder: ['status', 'name', 'removed'],
      columnWidths: { name: 20, status: 900, removed: 120 },
      fixedColumns: { name: 'left', status: 'middle', removed: 'right' }
    },
    {
      visibleColumns: ['name', 'type', 'status'],
      columnOrder: ['name', 'type', 'status', 'location']
    },
    columns
  )

  assert.deepEqual(prefs.columnOrder, ['status', 'name', 'type', 'location'])
  assert.deepEqual(prefs.columnWidths, { name: 70, status: 640 })
  assert.deepEqual(prefs.fixedColumns, { name: 'left' })
})

test('filterRecordsByConditions supports numeric and date ranges', () => {
  const records = [
    { id: 1, amount: 8, installedAt: '2026-07-01' },
    { id: 2, amount: 15, installedAt: '2026-07-10' },
    { id: 3, amount: 28, installedAt: '2026-08-01' }
  ]
  const typedColumns = [
    { key: 'amount', label: '金额', type: 'number' },
    { key: 'installedAt', label: '安装日期', type: 'date' }
  ]

  const filtered = filterRecordsByConditions(
    records,
    [
      { key: 'amount', operator: 'between', value: [10, 20] },
      { key: 'installedAt', operator: 'dateRange', value: ['2026-07-01', '2026-07-31'] }
    ],
    typedColumns
  )

  assert.deepEqual(filtered.map(item => item.id), [2])
})

test('buildGroupedRowsByConditions respects descending group order', () => {
  const grouped = buildGroupedRowsByConditions(
    [
      { id: 1, status: 'A' },
      { id: 2, status: 'C' },
      { id: 3, status: 'B' }
    ],
    [{ key: 'status', order: 'descending' }],
    [{ key: 'status', label: '状态' }]
  )

  assert.deepEqual(grouped.filter(item => item.__group).map(item => item.label), ['C', 'B', 'A'])
})

test('normalizeTableViewPrefs migrates legacy aliases, deduplicates ordering and persists page size', () => {
  const prefs = normalizeTableViewPrefs({
    visibleFields: ['status', 'name', 'removed'],
    fieldOrder: ['status', 'status', 'name'],
    rowHeight: 'compact',
    pageSize: 50,
    groupBy: [
      { key: 'status', order: 'descending' },
      { key: 'status', order: 'ascending' },
      { key: 'removed', order: 'ascending' }
    ],
    sorts: [
      { key: 'name', order: 'ascending' },
      { key: 'name', order: 'descending' }
    ]
  }, {
    visibleColumns: ['name', 'status'],
    columnOrder: ['name', 'status'],
    pageSize: 20
  }, columns)

  assert.deepEqual(prefs.visibleColumns, ['status', 'name'])
  assert.deepEqual(prefs.columnOrder, ['status', 'name', 'type', 'location'])
  assert.equal(prefs.rowDensity, 'compact')
  assert.equal(prefs.pageSize, 50)
  assert.deepEqual(prefs.groups, [{ key: 'status', order: 'descending' }])
  assert.deepEqual(prefs.sorts, [{ key: 'name', order: 'ascending' }])
})

test('new and removed fields are normalized without invalidating an old view', () => {
  const prefs = normalizeTableViewPrefs({
    visibleColumns: ['name', 'removed'],
    columnOrder: ['removed', 'name'],
    filters: [{ key: 'removed', operator: 'equals', value: 'x' }]
  }, {
    visibleColumns: ['name', 'status'],
    columnOrder: ['name', 'status', 'location']
  }, ['name', 'status', 'location'])

  assert.deepEqual(prefs.visibleColumns, ['name'])
  assert.deepEqual(prefs.columnOrder, ['name', 'status', 'location'])
  assert.deepEqual(prefs.filters, [])
})

test('extended text, numeric, date and option operators execute against complete client data', () => {
  const records = [
    { id: 1, name: '消防泵', amount: 10, date: '2026-07-01', status: 'NORMAL' },
    { id: 2, name: '客梯', amount: 20, date: '2026-07-20', status: 'FAULT' }
  ]
  const fields = [
    { key: 'name', type: 'text' },
    { key: 'amount', type: 'number' },
    { key: 'date', type: 'date' },
    { key: 'status', type: 'select' }
  ]

  assert.deepEqual(filterRecordsByConditions(records, [{ key: 'name', operator: 'notContains', value: '消防' }], fields).map(row => row.id), [2])
  assert.deepEqual(filterRecordsByConditions(records, [{ key: 'amount', operator: 'greaterThanOrEqual', value: 20 }], fields).map(row => row.id), [2])
  assert.deepEqual(filterRecordsByConditions(records, [{ key: 'date', operator: 'before', value: '2026-07-10' }], fields).map(row => row.id), [1])
  assert.deepEqual(filterRecordsByConditions(records, [{ key: 'status', operator: 'containsAny', value: ['FAULT'] }], fields).map(row => row.id), [2])
})

test('sequential grouping keeps backend order, emits stable multi-level headers and labels empty values', () => {
  const records = [
    { id: 1, building: 'A', status: 'ACTIVE' },
    { id: 2, building: 'A', status: 'ACTIVE' },
    { id: 3, building: 'A', status: null },
    { id: 4, building: 'B', status: 'ACTIVE' }
  ]
  const grouped = buildSequentialGroupedRowsByConditions(
    records,
    [{ key: 'building' }, { key: 'status' }],
    [{ key: 'building', label: '楼宇' }, { key: 'status', label: '状态' }],
    value => value,
    undefined,
    'server'
  )

  assert.deepEqual(grouped.filter(row => row.__group).map(row => ({
    key: row.key,
    label: row.label,
    count: row.count,
    level: row.level
  })), [
    { key: 'group:0:"building":["A"]:1', label: '楼宇：A', count: 3, level: 0 },
    { key: 'group:1:"status":["A","ACTIVE"]:1', label: '状态：ACTIVE', count: 2, level: 1 },
    { key: 'group:1:"status":["A","__EMPTY__"]:3', label: '状态：未分组', count: 1, level: 1 },
    { key: 'group:0:"building":["B"]:4', label: '楼宇：B', count: 1, level: 0 },
    { key: 'group:1:"status":["B","ACTIVE"]:4', label: '状态：ACTIVE', count: 1, level: 1 }
  ])
  assert.deepEqual(grouped.filter(row => !row.__group).map(row => row.id), [1, 2, 3, 4])
  assert.ok(grouped.filter(row => row.__group).every(row => row.countScope === 'loaded-page'))
  assert.deepEqual(
    grouped.filter(row => row.__group && row.mayContinueFromPreviousPage).map(row => row.key),
    ['group:0:"building":["A"]:1', 'group:1:"status":["A","ACTIVE"]:1']
  )
  assert.deepEqual(
    grouped.filter(row => row.__group && row.mayContinueOnNextPage).map(row => row.key),
    ['group:0:"building":["B"]:4', 'group:1:"status":["B","ACTIVE"]:4']
  )
})

test('server group keys remain stable and isolate repeated, empty and paged segments', () => {
  const groups = [{ key: 'status' }]
  const columns = [{ key: 'status', label: '状态' }]
  const records = [
    { code: 'ROW-10', status: null },
    { code: 'ROW-11', status: 'ACTIVE' },
    { code: 'ROW-12', status: null }
  ]

  const first = buildSequentialGroupedRowsByConditions(
    records,
    groups,
    columns,
    value => value,
    undefined,
    'server',
    'code'
  ).filter(row => row.__group)
  const second = buildSequentialGroupedRowsByConditions(
    records,
    groups,
    columns,
    value => value,
    undefined,
    'server',
    'code'
  ).filter(row => row.__group)

  assert.deepEqual(first.map(row => row.key), second.map(row => row.key))
  assert.equal(new Set(first.map(row => row.key)).size, first.length)
  assert.match(first[0].key, /group:0:"status":\["__EMPTY__"\]:"ROW-10"/)
  assert.match(first[2].key, /group:0:"status":\["__EMPTY__"\]:"ROW-12"/)

  const nextPage = buildSequentialGroupedRowsByConditions(
    [{ code: 'ROW-30', status: null }],
    groups,
    columns,
    value => value,
    undefined,
    'server',
    'code'
  ).find(row => row.__group)
  assert.notEqual(first[0].key, nextPage.key)
})
