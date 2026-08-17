import { defineWorkspaceConfig } from './workspaceConfig.js'

const serverFilter = (param, operators = ['equals']) => ({ mode: 'server', param, operators })
const serverOrder = param => ({ mode: 'server', param })

export const billTypeOptions = [
  { label: '租金', value: 'RENT' },
  { label: '物业费', value: 'PROPERTY' },
  { label: '水费', value: 'WATER' },
  { label: '电费', value: 'ELECTRICITY' },
  { label: '燃气费', value: 'GAS' },
  { label: '停车费', value: 'PARKING' },
  { label: '会议室', value: 'MEETING_ROOM' },
  { label: '维修/工单服务费', value: 'WORK_ORDER' },
  { label: '保洁费', value: 'CLEANING' },
  { label: '押金', value: 'DEPOSIT' },
  { label: '滞纳金', value: 'LATE_FEE' },
  { label: '调账补差', value: 'ADJUSTMENT' },
  { label: '其他', value: 'OTHER' }
]

export const billSourceTypeOptions = [
  { label: '手工账单', value: 'MANUAL' },
  { label: '租金依据', value: 'RENT' },
  { label: '物业费依据', value: 'PROPERTY' },
  { label: '合同自动出账', value: 'CONTRACT' },
  { label: '周期收费', value: 'FEE_RULE' },
  { label: '能耗抄表', value: 'ENERGY' },
  { label: '停车账单', value: 'PARKING' },
  { label: '会议室预约', value: 'MEETING_ROOM' },
  { label: '会议室预约', value: 'MEETING' },
  { label: '工单服务', value: 'WORK_ORDER' },
  { label: '开发测试', value: 'DEV_FIXTURE' },
  { label: '历史账单', value: 'HISTORY' }
]

export const billPayStatusOptions = [
  { label: '待缴', value: 'UNPAID', tone: 'warning' },
  { label: '已缴', value: 'PAID', tone: 'success' },
  { label: '已逾期', value: 'OVERDUE', tone: 'danger' },
  { label: '已取消', value: 'CANCELLED', tone: 'info' }
]

export const billAuditStatusOptions = [
  { label: '待审核', value: 'PENDING', tone: 'warning' },
  { label: '已发布', value: 'APPROVED', tone: 'success' },
  { label: '已驳回', value: 'REJECTED', tone: 'danger' }
]

export const billInvoiceStatusOptions = [
  { label: '已开票', value: 'INVOICED', tone: 'success' },
  { label: '部分开票', value: 'PARTIALLY_INVOICED', tone: 'warning' },
  { label: '未开票', value: 'NOT_INVOICED', tone: 'info' },
  { label: '未开票', value: 'NONE', tone: 'info' },
  { label: '已红冲/作废', value: 'VOIDED', tone: 'danger' }
]

export const billWorkflowStatusOptions = [
  { label: '草稿', value: 'DRAFT', tone: 'info' },
  { label: '待审核', value: 'PENDING_REVIEW', tone: 'warning' },
  { label: '已审核锁定', value: 'APPROVED', tone: 'success' },
  { label: '已作废', value: 'VOID', tone: 'danger' }
]

export function createBillWorkspaceConfig({ tenantOptions = [] } = {}) {
  return defineWorkspaceConfig({
    id: 'finance-bills',
    title: '财务账单',
    subtitle: '统一核算计费依据、调整草稿、审核锁定、收款与发票版本',
    pageKey: '/bills',
    storageKey: 'dhic-finance-bill-table-view-v2',
    defaultViewName: '全部账单',
    query: { dataMode: 'server', filterLogics: ['and'] },
    permissions: {
      view: 'bill:view',
      create: 'bill:add',
      update: 'bill:update',
      void: 'bill:void',
      delete: 'bill:delete',
      deleteApproved: 'bill:delete-approved',
      audit: 'bill:audit',
      pay: 'bill:pay',
      invoice: 'finance:invoice:upload',
      export: 'bill:view'
    },
    customFields: {
      enabled: true,
      workspaceKey: 'bills',
      managePermission: 'custom-field:manage',
      valueUpdatePermission: 'bill:update'
    },
    primaryAction: { id: 'create', label: '新增账单', permission: 'create' },
    importExport: [
      { id: 'export', kind: 'export', label: '导出 Excel', permission: 'export' }
    ],
    fields: [
      {
        key: 'keyword', label: '关键词', type: 'text', filterOnly: true, fixedOnly: true,
        query: serverFilter('keyword', ['contains', 'equals']),
        valueGetter: row => [row.billNumber, row.title, row.tenantName, row.contractNumber, row.billType, row.sourceType].filter(Boolean).join(' ')
      },
      {
        key: 'tenantId', label: '租户', type: 'select', options: tenantOptions, filterOnly: true, fixedOnly: true,
        query: serverFilter('tenantId')
      },
      {
        key: 'billNumber', label: '账单编号', type: 'text', sortable: true, minWidth: 160,
        query: { filter: serverFilter('billNumber', ['contains', 'equals']), sort: serverOrder('billNumber') }
      },
      {
        key: 'title', label: '账单标题', type: 'text', sortable: true, minWidth: 190,
        query: { filter: serverFilter('title', ['contains', 'equals']), sort: serverOrder('title') }
      },
      {
        key: 'tenantName', label: '租户', type: 'text', sortable: true, groupable: true, minWidth: 160,
        query: { filter: serverFilter('tenantName', ['contains', 'equals']), sort: serverOrder('tenantName'), group: serverOrder('tenantName') }
      },
      {
        key: 'contractNumber', label: '合同编号', type: 'text', sortable: true, groupable: true, minWidth: 155,
        query: { filter: serverFilter('contractNumber', ['contains', 'equals']), sort: serverOrder('contractNumber'), group: serverOrder('contractNumber') }
      },
      {
        key: 'billType', label: '账单类型', type: 'select', options: billTypeOptions, sortable: true, groupable: true, width: 130,
        query: { filter: serverFilter('billType'), sort: serverOrder('billType'), group: serverOrder('billType') }
      },
      {
        key: 'sourceType', label: '来源', type: 'select', options: billSourceTypeOptions, sortable: true, groupable: true, width: 135,
        query: { filter: serverFilter('sourceType'), sort: serverOrder('sourceType'), group: serverOrder('sourceType') }
      },
      {
        key: 'periodStart', label: '账期开始', type: 'date', sortable: true, width: 120, filterable: false,
        query: { sort: serverOrder('periodStart') }
      },
      {
        key: 'periodEnd', label: '账期结束', type: 'date', sortable: true, width: 120, filterable: false,
        query: { sort: serverOrder('periodEnd') }
      },
      {
        key: 'calculatedTotal', label: '计算金额', type: 'money', currency: 'CNY', decimals: 2, sortable: false, width: 125, align: 'right', filterable: false
      },
      {
        key: 'adjustmentTotal', label: '调整金额', type: 'money', currency: 'CNY', decimals: 2, sortable: false, width: 125, align: 'right', filterable: false
      },
      {
        key: 'finalAmount', label: '最终金额', type: 'money', currency: 'CNY', decimals: 2, sortable: false, width: 125, align: 'right', filterable: false
      },
      {
        key: 'amount', label: '应收金额', type: 'money', currency: 'CNY', decimals: 2, sortable: true, width: 125, align: 'right',
        query: { filter: { mode: 'server', operators: ['equals', 'greaterThanOrEqual', 'lessThanOrEqual', 'between'], serialize: (value, condition) => serializeNumberFilter('amount', value, condition) }, sort: serverOrder('amount') }
      },
      {
        key: 'paidAmount', label: '已收金额', type: 'money', currency: 'CNY', decimals: 2, sortable: true, width: 130, align: 'right', filterable: false,
        query: { sort: serverOrder('paidAmount') }
      },
      {
        key: 'dueDate', label: '到期日', type: 'date', sortable: true, width: 120,
        query: { filter: { mode: 'server', operators: ['equals', 'before', 'after', 'dateRange'], serialize: (value, condition) => serializeDateFilter('dueDate', value, condition) }, sort: serverOrder('dueDate') }
      },
      {
        key: 'overdueDays', label: '逾期天数', type: 'number', sortable: true, width: 110, align: 'right', filterable: false,
        query: { sort: serverOrder('overdueDays') }
      },
      {
        key: 'invoiceStatus', label: '开票状态', type: 'status', options: billInvoiceStatusOptions, sortable: true, groupable: true, width: 120,
        query: { filter: serverFilter('invoiceStatus'), sort: serverOrder('invoiceStatus'), group: serverOrder('invoiceStatus') }
      },
      {
        key: 'auditStatus', label: '审核状态', type: 'status', options: billAuditStatusOptions, sortable: true, groupable: true, width: 120,
        query: { filter: serverFilter('auditStatus'), sort: serverOrder('auditStatus'), group: serverOrder('auditStatus') }
      },
      {
        key: 'workflowStatus', label: '账单状态', type: 'status', options: billWorkflowStatusOptions, width: 125, filterable: false
      },
      {
        key: 'status', label: '收款状态', type: 'status', options: billPayStatusOptions, sortable: true, groupable: true, width: 120,
        query: { filter: { mode: 'server', operators: ['equals'], serialize: serializeBillStatusFilter }, sort: serverOrder('status'), group: serverOrder('status') },
        valueGetter: row => (row.overdue && row.status !== 'PAID' && row.status !== 'CANCELLED' ? 'OVERDUE' : row.status)
      },
      {
        key: 'remark', label: '备注', type: 'text', minWidth: 190,
        query: { filter: serverFilter('remark', ['contains', 'equals']) }
      }
    ],
    views: [
      { id: 'pending-audit-bills', name: '草稿/待审核', system: true, prefs: { filters: [{ key: 'auditStatus', operator: 'equals', value: 'PENDING' }] } },
      { id: 'unpaid-bills', name: '待收款账单', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'UNPAID' }] } },
      { id: 'overdue-bills', name: '逾期账单', system: true, prefs: { filters: [{ key: 'status', operator: 'equals', value: 'OVERDUE' }] } },
      { id: 'pending-invoice-bills', name: '待开票', system: true, prefs: { filters: [{ key: 'invoiceStatus', operator: 'equals', value: 'NOT_INVOICED' }] } },
      { id: 'invoiced-bills', name: '已开票', system: true, prefs: { filters: [{ key: 'invoiceStatus', operator: 'equals', value: 'INVOICED' }] } }
    ],
    metrics: [
      { title: '账单总数', field: 'totalCount', type: 'count', desc: '当前筛选范围', tone: 'neutral' },
      { title: '应收合计', field: 'totalAmount', type: 'money', desc: '不含已取消账单', tone: 'money' },
      { title: '待收款', field: 'unpaidCount', type: 'count', desc: '当前筛选范围', tone: 'warning' },
      { title: '待收金额', field: 'unpaidAmount', type: 'money', desc: '当前筛选范围', tone: 'money' },
      { title: '逾期账单', field: 'overdueCount', type: 'count', desc: '已超过到期日', tone: 'danger' },
      { title: '已收金额', field: 'paidAmount', type: 'money', desc: '当前筛选范围', tone: 'success' }
    ],
    table: {
      structuralColumnCount: 2,
      groupColumnIndex: 0,
      defaultPrefs: {
        visibleColumns: [
          'billNumber',
          'title',
          'tenantName',
          'finalAmount',
          'dueDate',
          'workflowStatus',
          'status',
          'invoiceStatus'
        ],
        fixedColumns: { billNumber: 'left' },
        rowDensity: 'compact',
        pageSize: 20
      }
    }
  })
}

function serializeNumberFilter(prefix, value, condition) {
  if (condition.operator === 'equals') return { [prefix]: value }
  if (condition.operator === 'greaterThanOrEqual') return { [`min${capitalize(prefix)}`]: value }
  if (condition.operator === 'lessThanOrEqual') return { [`max${capitalize(prefix)}`]: value }
  if (condition.operator === 'between' && Array.isArray(value) && value.length === 2) {
    return { [`min${capitalize(prefix)}`]: value[0], [`max${capitalize(prefix)}`]: value[1] }
  }
  return {}
}

function serializeDateFilter(prefix, value, condition) {
  if (condition.operator === 'equals') return { [prefix]: value }
  if (condition.operator === 'before') return { [`${prefix}Before`]: value }
  if (condition.operator === 'after') return { [`${prefix}After`]: value }
  if (condition.operator === 'dateRange' && Array.isArray(value) && value.length === 2) {
    return { [`${prefix}From`]: value[0], [`${prefix}To`]: value[1] }
  }
  return {}
}

function serializeBillStatusFilter(value) {
  return value === 'OVERDUE' ? { overdue: true } : { status: value }
}

function capitalize(value) {
  return `${value.slice(0, 1).toUpperCase()}${value.slice(1)}`
}
