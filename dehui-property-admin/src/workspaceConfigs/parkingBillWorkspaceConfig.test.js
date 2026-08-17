import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { buildWorkspaceQueryPlan } from '../utils/workspaceQueryPlan.js'
import {
  buildParkingBillMetricSnapshot,
  parkingBillBusinessStatus,
  parkingBillPaymentStatus,
  parkingBillRowActions,
  parkingBillWorkspaceConfig
} from './parkingBillWorkspaceConfig.js'
import { workspaceTableOptions } from './workspaceConfig.js'

test('parking bill workspace config uses real fields, client scope and existing permission codes', () => {
  const fieldKeys = parkingBillWorkspaceConfig.fields.map(field => field.key)
  const permissionCodes = Object.values(parkingBillWorkspaceConfig.permissions)
  const options = workspaceTableOptions(parkingBillWorkspaceConfig, { records: () => [] })

  assert.equal(parkingBillWorkspaceConfig.id, 'parking-bills')
  assert.equal(parkingBillWorkspaceConfig.pageKey, '/parking/bills')
  assert.equal(parkingBillWorkspaceConfig.query.dataMode, 'client')
  assert.deepEqual(new Set(permissionCodes), new Set([
    'parking-bill:view',
    'parking-bill:add',
    'parking-bill:pay',
    'parking-bill:update'
  ]))
  assert.equal(permissionCodes.some(code => code.endsWith(':*')), false)
  assert.equal(fieldKeys.includes('syncStatus'), false)
  assert.ok(fieldKeys.includes('payStatus'))
  assert.ok(fieldKeys.includes('status'))
  assert.ok(fieldKeys.includes('amount'))
  assert.ok(fieldKeys.includes('billingMonth'))
  assert.ok(fieldKeys.includes('dueDate'))
  const billingMonthField = parkingBillWorkspaceConfig.fields.find(field => field.key === 'billingMonth')
  assert.equal(billingMonthField.filterOnly, undefined)
  assert.equal(billingMonthField.valueGetter({ billingMonth: '2026-07', dueDate: '2026-08-10' }), '2026-07')
  assert.equal(billingMonthField.valueGetter({ dueDate: '2026-08-10' }), '')
  assert.deepEqual(
    parkingBillWorkspaceConfig.views.map(view => view.id),
    ['unpaid-parking-bills', 'paid-parking-bills', 'void-parking-bills']
  )
  assert.deepEqual(parkingBillWorkspaceConfig.dataActions.map(action => action.id), ['sync'])
  assert.deepEqual(parkingBillWorkspaceConfig.importExport, [])
  assert.deepEqual(parkingBillWorkspaceConfig.batchActions, [])
  assert.equal(options.columns, parkingBillWorkspaceConfig.fields)
  assert.equal(options.initialViews, parkingBillWorkspaceConfig.views)
})

test('parking bill query plan filters and sorts the complete loaded dataset without fake server params', () => {
  const plan = buildWorkspaceQueryPlan({
    fields: parkingBillWorkspaceConfig.fields,
    filters: [
      { key: 'payStatus', operator: 'equals', value: 'UNPAID' },
      { key: 'keyword', operator: 'contains', value: 'B1-003' }
    ],
    sorts: [{ key: 'dueDate', order: 'ascending' }],
    groups: [{ key: 'status', order: 'ascending' }],
    filterLogic: 'and',
    query: parkingBillWorkspaceConfig.query
  })

  assert.equal(plan.scope, 'client')
  assert.equal(plan.clientFilters.length, 2)
  assert.equal(plan.clientSorts.length, 1)
  assert.equal(plan.clientGroups.length, 1)
  assert.equal(plan.serverFilters.length, 0)
  assert.deepEqual(plan.serverParams, {})
  assert.equal(plan.unsupportedFilters.length, 0)
})

test('parking bill payment status and business status remain independent', () => {
  const paidActive = { payStatus: 'PAID', status: 'ACTIVE' }
  const unpaidVoid = { payStatus: 'UNPAID', status: 'VOID' }

  assert.equal(parkingBillPaymentStatus(paidActive), 'PAID')
  assert.equal(parkingBillBusinessStatus(paidActive), 'ACTIVE')
  assert.equal(parkingBillPaymentStatus(unpaidVoid), 'UNPAID')
  assert.equal(parkingBillBusinessStatus(unpaidVoid), 'VOID')
  assert.deepEqual(parkingBillRowActions(paidActive, { canPay: true, canUpdate: true }), {
    pay: false,
    void: false
  })
  assert.deepEqual(parkingBillRowActions(unpaidVoid, { canPay: true, canUpdate: true }), {
    pay: false,
    void: false
  })
})

test('parking bill row actions require both permission and a legal unpaid active state', () => {
  const activeUnpaid = { payStatus: 'UNPAID', status: 'ACTIVE' }

  assert.deepEqual(parkingBillRowActions(activeUnpaid), { pay: false, void: false })
  assert.deepEqual(parkingBillRowActions(activeUnpaid, { canPay: true }), { pay: true, void: false })
  assert.deepEqual(parkingBillRowActions(activeUnpaid, { canUpdate: true }), { pay: false, void: true })
  assert.deepEqual(
    parkingBillRowActions(activeUnpaid, { canPay: true, canUpdate: true }),
    { pay: true, void: true }
  )
})

test('parking bill metrics use the complete API list and never count void bills as receivable', () => {
  const rows = [
    { id: 1, amount: 100, payStatus: 'UNPAID', status: 'ACTIVE' },
    { id: 2, amount: 200, payStatus: 'PAID', status: 'ACTIVE' },
    { id: 3, amount: 300, payStatus: 'UNPAID', status: 'VOID' }
  ]
  const snapshot = buildParkingBillMetricSnapshot(rows, rows.slice(0, 2))

  assert.deepEqual(snapshot, {
    total: 3,
    filtered: 2,
    unpaid: 1,
    receivableAmount: 100,
    paid: 1,
    voided: 1
  })
})

test('parking bill page is wired to DataWorkspace and retains real business handlers', () => {
  const source = fs.readFileSync(
    path.join(process.cwd(), 'src/views/parking/ParkingBillList.vue'),
    'utf8'
  )

  assert.match(source, /<DataWorkspace/)
  assert.match(source, /<WorkspaceDataGrid/)
  assert.match(source, /parkingBillWorkspaceConfig/)
  assert.doesNotMatch(source, /<el-table(?:\s|>)/)
  assert.match(source, /listParkingBills\(\)/)
  assert.match(source, /createParkingBill\(\{ \.\.\.createForm \}\)/)
  assert.match(source, /prop="billingMonth"/)
  assert.match(source, /createForm\.billingMonth = generationMonth\.value/)
  assert.match(source, /syncParkingBills\(\{ month: generationMonth\.value \}\)/)
  assert.match(source, /failureCount/)
  assert.match(source, /syncIssueText/)
  assert.match(source, /payParkingBill\(row\.id\)/)
  assert.match(source, /voidParkingBill\(row\.id\)/)
  assert.match(source, /parkingBillSecondaryActions\(row\)\.length/)
  assert.doesNotMatch(source, /role\s*===\s*['"]ADMIN['"]/)
})
