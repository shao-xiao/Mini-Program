import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceQueryPlan } from '../utils/workspaceQueryPlan.js'
import {
  buildContractMetricSnapshot,
  contractStatusOptions,
  contractWorkspaceConfig,
  normalizeContractWorkspaceRecord
} from './contractWorkspaceConfig.js'
import { workspaceTableOptions } from './workspaceConfig.js'

test('contract workspace uses client mode and the existing permission codes', () => {
  assert.equal(contractWorkspaceConfig.query.dataMode, 'client')
  assert.deepEqual(Object.values(contractWorkspaceConfig.permissions), [
    'contract:view',
    'contract:create',
    'contract:update',
    'contract:delete'
  ])
  assert.deepEqual(contractWorkspaceConfig.batchActions, [])
  assert.deepEqual(contractWorkspaceConfig.importExport, [])
})

test('contract workspace only exposes the approved persisted display fields', () => {
  const fieldKeys = contractWorkspaceConfig.fields.map(field => field.key)

  assert.deepEqual(fieldKeys, [
    'contractNo',
    'tenantName',
    'roomSummary',
    'startDate',
    'endDate',
    'rentAmount',
    'depositAmount',
    'status',
    'createdAt'
  ])
  assert.equal(fieldKeys.includes('propertyFeeAmount'), false)
  assert.equal(fieldKeys.includes('paymentCycle'), false)
  assert.equal(fieldKeys.includes('billingLeadDays'), false)
  assert.equal(contractWorkspaceConfig.table.defaultPrefs.visibleColumns.includes('createdAt'), false)
  assert.equal(contractStatusOptions.some(option => option.value === 'PENDING_APPROVAL'), true)
  assert.equal(contractStatusOptions.some(option => option.value === 'SUSPENDED'), true)
})

test('contract response aliases normalize without fabricating unavailable values', () => {
  const record = normalizeContractWorkspaceRecord({
    id: 11,
    code: 'CON-011',
    contractNumber: 'CON-011',
    tenantName: '测试租户',
    roomSummary: '1F：A-101',
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    rentAmount: 10000,
    depositAmount: 20000,
    status: 'ACTIVE'
  })

  assert.equal(record.contractNo, 'CON-011')
  assert.equal(record.contractNumber, 'CON-011')
  assert.equal(record.roomSummary, '1F：A-101')
  assert.equal(record.createdAt, '')
})

test('contract metrics and filters operate on the complete loaded array', () => {
  const rows = [
    { id: 1, status: 'ACTIVE', rentAmount: 10000 },
    { id: 2, status: 'PENDING', rentAmount: 8000 },
    { id: 3, status: 'DRAFT', rentAmount: null },
    { id: 4, status: 'PENDING_APPROVAL', rentAmount: 2000 },
    { id: 5, status: 'SUSPENDED', rentAmount: 3000 }
  ]
  const metrics = buildContractMetricSnapshot(rows, rows.slice(0, 2))
  const plan = buildWorkspaceQueryPlan({
    fields: contractWorkspaceConfig.fields,
    filters: [{ key: 'status', operator: 'equals', value: 'ACTIVE' }],
    sorts: [{ key: 'endDate', order: 'ascending' }],
    query: contractWorkspaceConfig.query
  })
  const options = workspaceTableOptions(contractWorkspaceConfig, {
    records: () => rows,
    serverTotal: () => rows.length
  })

  assert.deepEqual(metrics, {
    total: 5,
    filtered: 2,
    active: 1,
    pending: 3,
    rentTotal: 23000
  })
  assert.equal(plan.clientFilters.length, 1)
  assert.equal(plan.clientSorts.length, 1)
  assert.equal(plan.serverFilters.length, 0)
  assert.equal(options.columns, contractWorkspaceConfig.fields)
})
