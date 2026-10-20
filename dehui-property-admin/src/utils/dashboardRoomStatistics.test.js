import test from 'node:test'
import assert from 'node:assert/strict'
import { loadDashboardRoomStatistics } from './dashboardRoomStatistics.js'

function getCounts(total, rented, calls = []) {
  return async (url, config) => {
    calls.push([url, config])
    return { total: config.params.status === 'RENTED' ? rented : total, records: [{ id: 1 }] }
  }
}

test('uses server totals beyond the first page and shares one consistent rental rate', async () => {
  const calls = []
  assert.deepEqual(await loadDashboardRoomStatistics(getCounts(205, 83, calls)), {
    roomCount: 205, rentedRoomCount: 83, availableRoomCount: 122, rentalRate: 40.5
  })
  assert.deepEqual(calls.map(([url, config]) => [url, config.params]), [
    ['/rooms', { page: 1, pageSize: 1 }],
    ['/rooms', { page: 1, pageSize: 1, status: 'RENTED' }]
  ])
})

test('rounds current ledger totals to one decimal', async () => {
  assert.deepEqual(await loadDashboardRoomStatistics(getCounts(39, 11)), {
    roomCount: 39, rentedRoomCount: 11, availableRoomCount: 28, rentalRate: 28.2
  })
})

test('an empty ledger remains a valid zero total', async () => {
  assert.deepEqual(await loadDashboardRoomStatistics(getCounts(0, 0)), {
    roomCount: 0, rentedRoomCount: 0, availableRoomCount: 0, rentalRate: 0
  })
})

test('a failed or forbidden request cannot become a zero occupancy figure', async () => {
  const denied = Object.assign(new Error('forbidden'), { response: { status: 403 } })
  await assert.rejects(() => loadDashboardRoomStatistics(async () => { throw denied }), error => error === denied)
  await assert.rejects(() => loadDashboardRoomStatistics(async (url, config) => {
    if (config.params.status) throw new Error('network')
    return { total: 39 }
  }), /network/)
})

test('invalid or inconsistent counts are rejected instead of showing misleading data', async () => {
  await assert.rejects(() => loadDashboardRoomStatistics(async () => ({ records: [] })), /不完整/)
  await assert.rejects(() => loadDashboardRoomStatistics(getCounts(2, 3)), /变化/)
})
