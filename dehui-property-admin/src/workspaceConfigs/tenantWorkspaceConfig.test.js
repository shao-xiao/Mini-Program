import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceQueryPlan } from '../utils/workspaceQueryPlan.js'
import {
  buildTenantMetricSnapshot,
  normalizeTenantWorkspaceRecord,
  tenantWorkspaceConfig
} from './tenantWorkspaceConfig.js'
import { workspaceTableOptions } from './workspaceConfig.js'

test('tenant workspace uses the complete client dataset and existing permission codes', () => {
  assert.equal(tenantWorkspaceConfig.query.dataMode, 'client')
  assert.deepEqual(Object.values(tenantWorkspaceConfig.permissions), [
    'tenant:view',
    'tenant:create',
    'tenant:update',
    'tenant:delete'
  ])
  assert.deepEqual(tenantWorkspaceConfig.batchActions, [])
  assert.deepEqual(tenantWorkspaceConfig.importExport, [])
})

test('tenant workspace only declares the approved list fields', () => {
  assert.deepEqual(tenantWorkspaceConfig.fields.map(field => field.key), [
    'id',
    'tenantCode',
    'tenantName',
    'companyShortName',
    'contactName',
    'phone',
    'email',
    'creditCode',
    'invoiceTitle',
    'taxNumber',
    'status'
  ])
  assert.equal(tenantWorkspaceConfig.fields.filter(field => field.label === '统一社会信用代码').length, 1)
  assert.deepEqual(tenantWorkspaceConfig.metrics.map(metric => metric.field), [
    'total',
    'filtered',
    'active',
    'inactive',
    'contactReady'
  ])
  assert.equal(tenantWorkspaceConfig.table.defaultPrefs.visibleColumns.includes('id'), false)
})

test('tenant API aliases normalize at the page boundary without dropping original fields', () => {
  const record = normalizeTenantWorkspaceRecord({
    id: 7,
    tenantCode: 'TENANT-007',
    tenantName: '测试租户',
    contactPerson: '联系人',
    contactPhone: '13800000000',
    contactEmail: 'contact@example.com',
    businessLicense: '91310000TEST',
    companyShortName: '测试',
    invoiceTitle: '测试租户',
    taxNumber: '91310000TEST',
    status: 'ACTIVE'
  })

  assert.equal(record.contactName, '联系人')
  assert.equal(record.phone, '13800000000')
  assert.equal(record.email, 'contact@example.com')
  assert.equal(record.creditCode, '91310000TEST')
  assert.equal(record.companyShortName, '测试')
  assert.equal(record.invoiceTitle, '测试租户')
  assert.equal(record.taxNumber, '91310000TEST')
  assert.equal(record.contactPerson, '联系人')
  assert.equal(record.businessLicense, '91310000TEST')
})

test('tenant metrics and client query plan use the loaded tenant records', () => {
  const rows = [
    { id: 1, status: 'ACTIVE', phone: '13800000000', email: '' },
    { id: 2, status: 'DISABLED', phone: '', email: '' }
  ]
  const metrics = buildTenantMetricSnapshot(rows, rows.slice(0, 1))
  const plan = buildWorkspaceQueryPlan({
    fields: tenantWorkspaceConfig.fields,
    filters: [{ key: 'status', operator: 'equals', value: 'ACTIVE' }],
    query: tenantWorkspaceConfig.query
  })
  const options = workspaceTableOptions(tenantWorkspaceConfig, {
    records: () => rows,
    serverTotal: () => rows.length
  })

  assert.deepEqual(metrics, {
    total: 2,
    filtered: 1,
    active: 1,
    inactive: 1,
    contactReady: 1
  })
  assert.equal(plan.clientFilters.length, 1)
  assert.equal(plan.serverFilters.length, 0)
  assert.equal(options.columns, tenantWorkspaceConfig.fields)
  assert.equal(options.query.dataMode, 'client')
})
