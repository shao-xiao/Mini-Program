import { defineWorkspaceConfig } from './workspaceConfig.js'

export const parkingBillPayStatusOptions = [
  { label: '未支付', value: 'UNPAID', tone: 'warning' },
  { label: '已支付', value: 'PAID', tone: 'success' },
  { label: '已取消', value: 'CANCELLED', tone: 'info' }
]

export const parkingBillBusinessStatusOptions = [
  { label: '生效', value: 'ACTIVE', tone: 'success' },
  { label: '生效', value: 'ENABLED', tone: 'success' },
  { label: '已作废', value: 'VOID', tone: 'info' },
  { label: '已取消', value: 'CANCELLED', tone: 'info' },
  { label: '已停用', value: 'DISABLED', tone: 'info' }
]

export const parkingBillWorkspaceConfig = defineWorkspaceConfig({
  id: 'parking-bills',
  title: '停车账单',
  subtitle: '统一查看停车账单、收款状态与账单业务状态',
  pageKey: '/parking/bills',
  storageKey: 'dhic-parking-bill-table-view-v1',
  defaultViewName: '全部停车账单',
  query: { dataMode: 'client', filterLogics: ['and', 'or'] },
  permissions: {
    view: 'parking-bill:view',
    add: 'parking-bill:add',
    sync: 'parking-bill:add',
    pay: 'parking-bill:pay',
    update: 'parking-bill:update'
  },
  primaryAction: { id: 'create', label: '新增停车账单', permission: 'add' },
  dataActions: [
    { id: 'sync', label: '生成并同步到账单中心', permission: 'sync' }
  ],
  statuses: [
    ...parkingBillPayStatusOptions.map(option => ({ ...option, scope: 'payment' })),
    ...parkingBillBusinessStatusOptions.map(option => ({ ...option, scope: 'business' }))
  ],
  fields: [
    {
      key: 'keyword',
      label: '关键词',
      type: 'text',
      filterOnly: true,
      fixedOnly: true,
      query: { mode: 'client', operators: ['contains', 'equals', 'notEquals'] },
      valueGetter: parkingBillKeywordText
    },
    {
      key: 'billingMonth',
      label: '账期',
      type: 'text',
      required: true,
      sortable: true,
      width: 110,
      query: { mode: 'client', operators: ['equals', 'notEquals'] },
      valueGetter: row => String(row?.billingMonth || '')
    },
    { key: 'billNo', label: '账单编号', type: 'text', required: true, sortable: true, minWidth: 170 },
    { key: 'assignmentCode', label: '分配编号', type: 'text', sortable: true, minWidth: 145 },
    { key: 'spaceNoSnapshot', label: '停车位', type: 'text', sortable: true, groupable: true, minWidth: 125 },
    { key: 'plateNoSnapshot', label: '车牌号', type: 'text', sortable: true, groupable: true, minWidth: 125 },
    { key: 'partyNameSnapshot', label: '租户', type: 'text', sortable: true, groupable: true, minWidth: 170 },
    { key: 'billTypeText', label: '账单类型', type: 'text', sortable: true, groupable: true, width: 110 },
    { key: 'amount', label: '账单金额', type: 'money', currency: 'CNY', decimals: 2, sortable: true, width: 135, align: 'right' },
    { key: 'dueDate', label: '到期日期', type: 'date', sortable: true, width: 125 },
    {
      key: 'payStatus',
      label: '收款状态',
      type: 'status',
      options: parkingBillPayStatusOptions,
      sortable: true,
      groupable: true,
      width: 120,
      query: { mode: 'client', operators: ['equals', 'notEquals'] }
    },
    {
      key: 'status',
      label: '账单状态',
      type: 'status',
      options: parkingBillBusinessStatusOptions,
      sortable: true,
      groupable: true,
      width: 120,
      query: { mode: 'client', operators: ['equals', 'notEquals'] }
    },
    { key: 'createdTime', label: '创建时间', type: 'date', format: 'datetime', sortable: true, width: 165 },
    { key: 'remark', label: '备注', type: 'text', minWidth: 190 }
  ],
  views: [
    {
      id: 'unpaid-parking-bills',
      name: '待收款',
      system: true,
      prefs: { filters: [{ key: 'payStatus', operator: 'equals', value: 'UNPAID' }] }
    },
    {
      id: 'paid-parking-bills',
      name: '已收款',
      system: true,
      prefs: { filters: [{ key: 'payStatus', operator: 'equals', value: 'PAID' }] }
    },
    {
      id: 'void-parking-bills',
      name: '已作废',
      system: true,
      prefs: { filters: [{ key: 'status', operator: 'equals', value: 'VOID' }] }
    }
  ],
  metrics: [
    { title: '账单总数', field: 'total', type: 'count', desc: '接口返回的全部账单', tone: 'neutral' },
    { title: '筛选结果', field: 'filtered', type: 'count', desc: '当前视图命中', tone: 'primary' },
    { title: '待收款', field: 'unpaid', type: 'count', desc: '生效且未支付', tone: context => context.unpaid > 0 ? 'warning' : 'success' },
    { title: '待收款金额', field: 'receivableAmount', type: 'money', desc: '生效未支付账单合计', tone: 'money' },
    { title: '已收款', field: 'paid', type: 'count', desc: '支付状态为已支付', tone: 'success' },
    { title: '已作废', field: 'voided', type: 'count', desc: '账单业务状态为作废', tone: 'neutral' }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: {
      fixedColumns: { billNo: 'left' },
      rowDensity: 'compact'
    }
  }
})

export function parkingBillKeywordText(row = {}) {
  return [
    row.billNo,
    row.code,
    row.assignmentCode,
    row.spaceNoSnapshot,
    row.plateNoSnapshot,
    row.partyNameSnapshot,
    row.remark
  ].filter(Boolean).join(' ')
}

export function parkingBillPaymentStatus(row = {}) {
  return String(row.payStatus || 'UNPAID').toUpperCase()
}

export function parkingBillBusinessStatus(row = {}) {
  return String(row.status || 'ACTIVE').toUpperCase()
}

export function isParkingBillActive(row = {}) {
  return !['VOID', 'CANCELLED', 'DISABLED'].includes(parkingBillBusinessStatus(row))
}

export function parkingBillRowActions(row, { canPay = false, canUpdate = false } = {}) {
  const unpaid = parkingBillPaymentStatus(row) === 'UNPAID'
  const active = isParkingBillActive(row)
  return {
    pay: Boolean(canPay && unpaid && active),
    void: Boolean(canUpdate && unpaid && active)
  }
}

export function buildParkingBillMetricSnapshot(rows = [], visibleRows = rows) {
  const records = Array.isArray(rows) ? rows : []
  const visible = Array.isArray(visibleRows) ? visibleRows : []
  const collectable = records.filter(row => (
    isParkingBillActive(row) && parkingBillPaymentStatus(row) === 'UNPAID'
  ))

  return {
    total: records.length,
    filtered: visible.length,
    unpaid: collectable.length,
    receivableAmount: collectable.reduce((sum, row) => sum + finiteAmount(row.amount), 0),
    paid: records.filter(row => parkingBillPaymentStatus(row) === 'PAID').length,
    voided: records.filter(row => parkingBillBusinessStatus(row) === 'VOID').length
  }
}

function finiteAmount(value) {
  const amount = Number(value)
  return Number.isFinite(amount) ? amount : 0
}
