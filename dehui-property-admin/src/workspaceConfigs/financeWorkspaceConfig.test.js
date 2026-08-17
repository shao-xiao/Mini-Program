import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceQueryPlan } from '../utils/workspaceQueryPlan.js'
import {
  billAuditStatusOptions,
  billInvoiceStatusOptions,
  billPayStatusOptions,
  createBillWorkspaceConfig
} from './financeWorkspaceConfig.js'

test('bill workspace uses server query semantics and real permission codes', () => {
  const config = createBillWorkspaceConfig()

  assert.equal(config.query.dataMode, 'server')
  assert.deepEqual(config.permissions, {
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
  })
})

test('bill workspace only exposes real or traceably derived list fields', () => {
  const config = createBillWorkspaceConfig()
  const keys = config.fields.map(field => field.key)

  assert.deepEqual(keys, [
    'keyword', 'tenantId', 'billNumber', 'title', 'tenantName', 'contractNumber',
    'billType', 'sourceType', 'periodStart', 'periodEnd', 'calculatedTotal',
    'adjustmentTotal', 'finalAmount', 'amount', 'paidAmount', 'dueDate',
    'overdueDays', 'invoiceStatus', 'auditStatus', 'workflowStatus', 'status', 'remark'
  ])
  assert.equal(keys.includes('lateFee'), false)
  assert.equal(keys.includes('approvedInfo'), false)
  assert.equal(keys.includes('approvedBy'), false)
  assert.equal(keys.includes('approvedTime'), false)
})

test('bill number remains visible by default but is not a mandatory view column', () => {
  const config = createBillWorkspaceConfig()
  const billNumberField = config.fields.find(field => field.key === 'billNumber')

  assert.equal(billNumberField.required, undefined)
  assert.notEqual(billNumberField.filterOnly, true)
  assert.deepEqual(config.table.defaultPrefs.visibleColumns, [
    'billNumber',
    'title',
    'tenantName',
    'finalAmount',
    'dueDate',
    'workflowStatus',
    'status',
    'invoiceStatus'
  ])
  assert.equal(config.table.defaultPrefs.fixedColumns.billNumber, 'left')
  assert.equal(config.table.defaultPrefs.rowDensity, 'compact')
})

test('bill query plan serializes complete server filters, grouping and sorting', () => {
  const config = createBillWorkspaceConfig()
  const plan = buildWorkspaceQueryPlan({
    fields: config.fields,
    filters: [
      { key: 'billType', operator: 'equals', value: 'RENT' },
      { key: 'amount', operator: 'between', value: [1000, 5000] },
      { key: 'dueDate', operator: 'dateRange', value: ['2026-07-01', '2026-07-31'] }
    ],
    groups: [{ key: 'tenantName', order: 'ascending' }],
    sorts: [{ key: 'dueDate', order: 'descending' }],
    page: 3,
    pageSize: 50,
    query: config.query
  })

  assert.deepEqual(plan.serverParams, {
    billType: 'RENT',
    dueDateFrom: '2026-07-01',
    dueDateTo: '2026-07-31',
    groupBy: 'tenantName:asc',
    maxAmount: 5000,
    minAmount: 1000,
    page: 3,
    pageSize: 50,
    sorts: 'tenantName:asc,dueDate:desc'
  })
})

test('bill status option values match backend response values', () => {
  const statusField = createBillWorkspaceConfig().fields.find(field => field.key === 'status')

  assert.deepEqual(billPayStatusOptions.map(option => option.value), ['UNPAID', 'PAID', 'OVERDUE', 'CANCELLED'])
  assert.deepEqual(billAuditStatusOptions.map(option => option.value), ['PENDING', 'APPROVED', 'REJECTED'])
  assert.deepEqual(
    billInvoiceStatusOptions.map(option => option.value),
    ['INVOICED', 'PARTIALLY_INVOICED', 'NOT_INVOICED', 'NONE', 'VOIDED']
  )
  assert.equal(statusField.valueGetter({ status: 'UNPAID', overdue: true }), 'OVERDUE')
  assert.equal(statusField.valueGetter({ status: 'PAID', overdue: true }), 'PAID')
})
