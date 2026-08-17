import assert from 'node:assert/strict'
import test from 'node:test'
import { reactive, ref } from 'vue'
import { useWorkspaceColumns } from './useWorkspaceColumns.js'

function createColumns() {
  const tablePrefs = reactive({
    visibleColumns: ['status', 'name'],
    columnOrder: ['status', 'name'],
    columnWidths: { name: 160 },
    fixedColumns: { status: 'left' },
    rowDensity: 'standard',
    customColumns: []
  })
  const columns = useWorkspaceColumns({
    columns: [
      { key: 'name', label: '名称', sortable: true },
      { key: 'status', label: '状态', sortable: true, options: [{ label: '正常', value: 'NORMAL' }] },
      { key: 'computed', label: '计算值', valueGetter: row => row.count * 2, filterOnly: true }
    ],
    tablePrefs,
    dataMode: 'client',
    updatePrefs: value => Object.assign(tablePrefs, value)
  })
  return { tablePrefs, columns }
}

test('workspace columns compile field, accessor and option maps', () => {
  const { columns } = createColumns()

  assert.equal(columns.fieldByKey.value.get('status').label, '状态')
  assert.equal(columns.fieldAccessors.value.get('computed')({ count: 3 }), 6)
  assert.equal(columns.optionMaps.value.get('status').get('NORMAL').label, '正常')
  assert.deepEqual(columns.orderedColumns.value.map(column => column.key), ['status', 'name'])
  assert.equal(columns.orderedColumns.value[0].optionMap.get('NORMAL').tone, 'info')
})

test('workspace columns own visibility, width, fixed and density updates', () => {
  const { tablePrefs, columns } = createColumns()
  columns.updateVisibleColumns(['name'])
  columns.updateColumnOrder(['name', 'status'])
  columns.updateColumnWidth('name', 240)
  columns.updateFixedColumns({ name: 'left' })
  columns.updateRowDensity('compact')

  assert.deepEqual(columns.visibleOrderedColumns.value.map(column => column.key), ['name'])
  assert.equal(columns.columnWidth('name'), 240)
  assert.equal(columns.columnFixed('name'), 'left')
  assert.equal(tablePrefs.rowDensity, 'compact')
})

test('required columns stay visible when an older saved view does not contain them', () => {
  const tablePrefs = reactive({
    visibleColumns: ['name'],
    columnOrder: ['name'],
    columnWidths: {},
    fixedColumns: {},
    customColumns: []
  })
  const columns = useWorkspaceColumns({
    columns: [
      { key: 'name', label: '名称' },
      { key: 'reportedAt', label: '报修时间', required: true }
    ],
    tablePrefs,
    updatePrefs: value => Object.assign(tablePrefs, value)
  })

  assert.deepEqual(columns.visibleOrderedColumns.value.map(column => column.key), ['name', 'reportedAt'])
})

test('workspace columns recompile when custom fields change', () => {
  const { tablePrefs, columns } = createColumns()
  tablePrefs.customColumns = [{ key: 'vendor', label: '厂家', custom: true, defaultValue: '-' }]
  tablePrefs.visibleColumns.push('vendor')
  tablePrefs.columnOrder.push('vendor')

  assert.equal(columns.fieldByKey.value.has('vendor'), true)
  assert.equal(columns.fieldAccessors.value.get('vendor')({ customFields: { vendor: 'DHIC' } }), 'DHIC')
  assert.equal(columns.visibleOrderedColumns.value.at(-1).key, 'vendor')
})

test('workspace columns recompile option maps when configured options change', () => {
  const tablePrefs = reactive({
    visibleColumns: ['status'],
    columnOrder: ['status'],
    columnWidths: {},
    fixedColumns: {},
    customColumns: []
  })
  const source = reactive({
    options: [{ label: '正常', value: 'NORMAL', tone: 'success' }]
  })
  const columns = useWorkspaceColumns({
    columns: [{ key: 'status', label: '状态', type: 'status', options: () => source.options }],
    tablePrefs,
    updatePrefs: value => Object.assign(tablePrefs, value)
  })

  assert.equal(columns.visibleOrderedColumns.value[0].optionMap.get('NORMAL').label, '正常')
  source.options = [{ label: '维修', value: 'MAINTENANCE', tone: 'warning' }]
  assert.equal(columns.visibleOrderedColumns.value[0].optionMap.has('NORMAL'), false)
  assert.equal(columns.visibleOrderedColumns.value[0].optionMap.get('MAINTENANCE').label, '维修')
})

test('workspace columns compile remote definitions without storing them in view preferences', () => {
  const tablePrefs = reactive({
    visibleColumns: ['name', 'custom_level'],
    columnOrder: ['name', 'custom_level'],
    columnWidths: {},
    fixedColumns: {},
    customColumns: []
  })
  const customFields = ref([{
    key: 'custom_level',
    label: '客户等级',
    type: 'select',
    custom: true,
    options: [{ value: 'A', label: 'A级' }]
  }])
  const columns = useWorkspaceColumns({
    columns: [
      { key: 'name', label: '名称' },
      { key: 'custom_level', label: '旧字段名称', type: 'text', custom: true, minWidth: 160 }
    ],
    customFields,
    tablePrefs,
    updatePrefs() {}
  })

  assert.deepEqual(columns.allColumns.value.map(item => item.key), ['name', 'custom_level'])
  assert.equal(columns.fieldByKey.value.get('custom_level').label, '客户等级')
  assert.equal(columns.fieldByKey.value.get('custom_level').minWidth, 160)
  assert.equal(columns.fieldAccessors.value.get('custom_level')({ customFields: { custom_level: 'A' } }), 'A')
  assert.equal(columns.optionMaps.value.get('custom_level').get('A').label, 'A级')
  assert.deepEqual(tablePrefs.customColumns, [])
})
