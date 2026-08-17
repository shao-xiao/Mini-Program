import { defineWorkspaceConfig } from './workspaceConfig.js'

export const contractStatusOptions = [
  { label: '草稿', value: 'DRAFT', tone: 'info' },
  { label: '待审批', value: 'PENDING', tone: 'warning' },
  { label: '待审批', value: 'PENDING_APPROVAL', tone: 'warning' },
  { label: '履约中', value: 'ACTIVE', tone: 'success' },
  { label: '已暂停', value: 'SUSPENDED', tone: 'warning' },
  { label: '已到期', value: 'EXPIRED', tone: 'info' },
  { label: '已终止', value: 'TERMINATED', tone: 'danger' },
  { label: '已作废', value: 'CANCELLED', tone: 'info' }
]

export const contractWorkspaceConfig = defineWorkspaceConfig({
  id: 'contracts',
  title: '合同管理',
  subtitle: '统一查看合同周期、租赁空间、费用与履约状态',
  pageKey: '/contracts',
  storageKey: 'dhic-contract-table-view-v1',
  defaultViewName: '全部合同',
  query: { dataMode: 'client', filterLogics: ['and', 'or'] },
  permissions: {
    view: 'contract:view',
    create: 'contract:create',
    update: 'contract:update',
    delete: 'contract:delete'
  },
  primaryAction: { id: 'create', label: '新增合同', permission: 'create' },
  fields: [
    { key: 'contractNo', label: '合同编号', type: 'text', required: true, sortable: true, minWidth: 170 },
    { key: 'tenantName', label: '租户名称', type: 'text', sortable: true, groupable: true, minWidth: 180 },
    { key: 'roomSummary', label: '租赁房间', type: 'text', sortable: true, minWidth: 210 },
    { key: 'startDate', label: '开始日期', type: 'date', sortable: true, width: 125 },
    { key: 'endDate', label: '结束日期', type: 'date', sortable: true, width: 125 },
    { key: 'rentAmount', label: '月租金额', type: 'money', currency: 'CNY', decimals: 2, sortable: true, width: 135, align: 'right' },
    { key: 'depositAmount', label: '押金', type: 'money', currency: 'CNY', decimals: 2, sortable: true, width: 125, align: 'right' },
    {
      key: 'status',
      label: '状态',
      type: 'status',
      options: contractStatusOptions,
      sortable: true,
      groupable: true,
      width: 115,
      query: { mode: 'client', operators: ['equals', 'notEquals'] }
    },
    { key: 'createdAt', label: '创建时间', type: 'date', format: 'datetime', sortable: true, width: 165 }
  ],
  metrics: [
    { title: '合同总数', field: 'total', type: 'count', desc: '接口返回的全部合同', tone: 'neutral' },
    { title: '当前视图', field: 'filtered', type: 'count', desc: '当前视图命中', tone: 'primary' },
    { title: '生效合同', field: 'active', type: 'count', desc: '当前履约中', tone: 'success' },
    { title: '待处理', field: 'pending', type: 'count', desc: '草稿或待审批', tone: 'warning' },
    { title: '月租合计', field: 'rentTotal', type: 'money', desc: '全部合同月租金额', tone: 'money' }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: {
      visibleColumns: [
        'contractNo',
        'tenantName',
        'roomSummary',
        'startDate',
        'endDate',
        'rentAmount',
        'depositAmount',
        'status'
      ],
      fixedColumns: { contractNo: 'left' },
      rowDensity: 'compact'
    }
  }
})

export function normalizeContractWorkspaceRecord(record = {}) {
  return {
    ...record,
    contractNo: record.contractNo ?? record.contractNumber ?? record.code ?? '',
    roomSummary: record.roomSummary ?? record.roomName ?? '',
    createdAt: record.createdAt ?? ''
  }
}

export function buildContractMetricSnapshot(rows = [], visibleRows = rows) {
  const records = Array.isArray(rows) ? rows : []
  const visible = Array.isArray(visibleRows) ? visibleRows : []

  return {
    total: records.length,
    filtered: visible.length,
    active: records.filter(row => row.status === 'ACTIVE').length,
    pending: records.filter(row => ['DRAFT', 'PENDING', 'PENDING_APPROVAL'].includes(row.status)).length,
    rentTotal: records.reduce((sum, row) => sum + finiteAmount(row.rentAmount), 0)
  }
}

function finiteAmount(value) {
  const amount = Number(value)
  return Number.isFinite(amount) ? amount : 0
}
