import test from 'node:test'
import assert from 'node:assert/strict'
import { loadOperationsSources, summarizeWorkOrders, summarizePayments, summarizeContracts, addReportDays } from './operationsReport.js'

test('unauthorized sources are never requested or reported as zero', async () => {
  const sources = await loadOperationsSources(() => { throw Error('must not request') }, () => false, '2026-10-20')
  assert.equal(Object.keys(sources).length, 9)
  assert.ok(Object.values(sources).every(item => item.status === 'denied' && item.data === null))
})
test('one failed source does not hide another and respects room totals across pages', async () => {
  const sources = await loadOperationsSources(async (path, config) => {
    if (path === '/rooms') return { total: config.params.status ? 11 : 39, records: [{}] }
    if (path === '/workorders') throw Error('offline')
    throw Error('unexpected request')
  }, code => ['room:view', 'workorder:view'].includes(code), '2026-10-20')
  assert.equal(sources.rooms.status, 'ready')
  assert.equal(sources.rooms.data.rentalRate, 28.2)
  assert.equal(sources.workorders.status, 'error')
  assert.equal(sources.finance.status, 'denied')
})
test('work backlog excludes closed and cancelled, keeps completed awaiting closure and chronological seven days', () => {
  const result = summarizeWorkOrders([
    { id: 1, status: 'CLOSED', priority: 'HIGH', createdTime: '2026-10-20 08:00:00' },
    { id: 2, status: 'SUBMITTED', priority: 'NORMAL', createdTime: '2026-10-19 08:00:00' },
    { id: 3, status: 'PROCESSING', priority: 'URGENT', createdTime: '2026-10-20 09:00:00' },
    { id: 4, status: 'COMPLETED', priority: 'NORMAL', createdTime: '2026-10-12 09:00:00' },
    { id: 5, status: 'CANCELLED', priority: 'HIGH', createdTime: '2026-10-21 09:00:00' }
  ], '2026-10-20')
  assert.equal(result.today, 2)
  assert.equal(result.pending, 3)
  assert.equal(result.high, 1)
  assert.equal(result.awaitingClose, 1)
  assert.equal(result.rows[0].id, 3)
  assert.deepEqual(result.trend.map(row => row.count), [0,0,0,0,0,1,2])
})
test('income uses paid transaction date, excludes cancelled payments and other months', () => {
  const rows = [{ status: 'PAID', amount: '10.10', paidAt: '2026-10-20T12:00:00' }, { status: 'PAID', amount: 20.2, paidAt: '2026-10-01' }, { status: 'CANCELLED', amount: 999, paidAt: '2026-10-20' }, { status: 'PAID', amount: 100, paidAt: '2026-09-20' }, { status: 'PAID', amount: 999, paidAt: '2026-10-21' }]
  assert.deepEqual(summarizePayments(rows, '2026-10-20'), { today: 10.1, month: 30.3 })
})
test('expiry includes today and day thirty, excludes inactive, expired and missing dates', () => {
  const rows = [{ status: 'ACTIVE', endDate: '2026-10-20' }, { status: 'ACTIVE', endDate: '2026-11-19' }, { status: 'ACTIVE', endDate: '2026-11-20' }, { status: 'ACTIVE', endDate: '2026-10-19' }, { status: 'ACTIVE' }, { status: 'CANCELLED', endDate: '2026-10-21' }]
  assert.equal(summarizeContracts(rows, '2026-10-20').expiring.length, 2)
  assert.equal(addReportDays('2026-12-20',30), '2027-01-19')
})

test('contracts use the established ledger endpoint and normalize its number without dropping rows', async () => {
  const sources = await loadOperationsSources(async path => {
    assert.equal(path, '/contracts')
    return [{ id: 1, contractNumber: 'HT-001', status: 'ACTIVE', endDate: '2026-11-01' }]
  }, code => code === 'contract:view', '2026-10-20')
  assert.equal(sources.contracts.status, 'ready')
  assert.equal(sources.contracts.data[0].contractNo, 'HT-001')
  assert.equal(summarizeContracts(sources.contracts.data, '2026-10-20').expiring.length, 1)
})
