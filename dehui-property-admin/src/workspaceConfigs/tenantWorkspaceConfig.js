import { defineWorkspaceConfig } from './workspaceConfig.js'

export const tenantStatusOptions = [
  { label: '正常', value: 'ACTIVE', tone: 'success' },
  { label: '正常', value: 'ENABLED', tone: 'success' },
  { label: '停用', value: 'INACTIVE', tone: 'info' },
  { label: '停用', value: 'DISABLED', tone: 'info' }
]

export const tenantWorkspaceConfig = defineWorkspaceConfig({
  id: 'tenants',
  title: '租户管理',
  subtitle: '维护租户主档、联系人账号和小程序绑定入口',
  pageKey: '/tenants',
  storageKey: 'dhic-tenant-table-view-v2',
  defaultViewName: '全部租户',
  query: { dataMode: 'client', filterLogics: ['and', 'or'] },
  permissions: {
    view: 'tenant:view',
    create: 'tenant:create',
    update: 'tenant:update',
    delete: 'tenant:delete'
  },
  primaryAction: { id: 'create', label: '新增租户', permission: 'create' },
  fields: [
    { key: 'id', label: 'ID', type: 'number', decimals: 0, sortable: true, width: 90, align: 'right' },
    { key: 'tenantCode', label: '租户编号', type: 'text', sortable: true, minWidth: 140 },
    { key: 'tenantName', label: '租户名称', type: 'text', required: true, sortable: true, minWidth: 180 },
    { key: 'companyShortName', label: '企业简称', type: 'text', sortable: true, minWidth: 130 },
    { key: 'contactName', label: '联系人', type: 'text', sortable: true, minWidth: 120 },
    { key: 'phone', label: '联系电话', type: 'text', minWidth: 155 },
    { key: 'email', label: '邮箱', type: 'text', minWidth: 190 },
    { key: 'creditCode', label: '统一社会信用代码', type: 'text', minWidth: 190 },
    { key: 'invoiceTitle', label: '开票抬头', type: 'text', minWidth: 180 },
    { key: 'taxNumber', label: '纳税人识别号', type: 'text', minWidth: 180 },
    {
      key: 'status',
      label: '状态',
      type: 'status',
      options: tenantStatusOptions,
      sortable: true,
      groupable: true,
      width: 105,
      query: { mode: 'client', operators: ['equals', 'notEquals'] }
    }
  ],
  metrics: [
    { title: '租户总数', field: 'total', type: 'count', desc: '接口返回的全部租户', tone: 'neutral' },
    { title: '当前视图', field: 'filtered', type: 'count', desc: '当前视图命中', tone: 'primary' },
    { title: '正常租户', field: 'active', type: 'count', desc: '可正常服务', tone: 'success' },
    { title: '停用租户', field: 'inactive', type: 'count', desc: '当前不可用', tone: 'warning' },
    { title: '联系方式', field: 'contactReady', type: 'count', desc: '已维护电话或邮箱', tone: 'primary' }
  ],
  table: {
    structuralColumnCount: 2,
    groupColumnIndex: 0,
    defaultPrefs: {
      visibleColumns: ['tenantCode', 'tenantName', 'companyShortName', 'contactName', 'phone', 'email', 'creditCode', 'status'],
      fixedColumns: { tenantName: 'left' },
      rowDensity: 'compact'
    }
  }
})

export function normalizeTenantWorkspaceRecord(record = {}) {
  return {
    ...record,
    tenantCode: record.tenantCode ?? record.code ?? '',
    tenantName: record.tenantName ?? record.companyName ?? record.name ?? '',
    companyShortName: record.companyShortName ?? record.shortName ?? '',
    contactName: record.contactName ?? record.contactPerson ?? '',
    phone: record.phone ?? record.contactPhone ?? '',
    email: record.email ?? record.contactEmail ?? '',
    creditCode: record.unifiedCreditCode ?? record.creditCode ?? record.businessLicense ?? '',
    invoiceTitle: record.invoiceTitle ?? '',
    taxNumber: record.taxNumber ?? ''
  }
}

export function buildTenantMetricSnapshot(rows = [], visibleRows = rows) {
  const records = Array.isArray(rows) ? rows : []
  const visible = Array.isArray(visibleRows) ? visibleRows : []

  return {
    total: records.length,
    filtered: visible.length,
    active: records.filter(row => ['ACTIVE', 'ENABLED'].includes(row.status)).length,
    inactive: records.filter(row => ['INACTIVE', 'DISABLED'].includes(row.status)).length,
    contactReady: records.filter(row => row.phone || row.email).length
  }
}
