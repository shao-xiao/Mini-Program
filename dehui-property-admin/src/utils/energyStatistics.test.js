import test from 'node:test'
import assert from 'node:assert/strict'
import { energyChartTypes, energyTrendWindow, energyTrendSeries, energyRanking } from './energyStatistics.js'

test('uses the latest actual period and leaves missing months empty', () => {
  const months = energyTrendWindow([{ periodMonth: '2026-09', electricUsage: 1479.29, electricRecordCount: 10 }])
  assert.deepEqual(months.map(row => row.periodMonth), ['2026-04','2026-05','2026-06','2026-07','2026-08','2026-09'])
  assert.deepEqual(energyTrendSeries(months, energyChartTypes[0]), [null,null,null,null,null,1479.29])
})

test('does not turn another type of reading into a zero usage point', () => {
  const months = energyTrendWindow([{ periodMonth: '2026-09', electricUsage: 0, electricRecordCount: 2, waterUsage: 0, waterRecordCount: 0 }])
  assert.equal(energyTrendSeries(months, energyChartTypes[0]).at(-1), 0)
  assert.equal(energyTrendSeries(months, energyChartTypes[1]).at(-1), null)
})

test('selected period crosses a year boundary without inventing records', () => {
  assert.deepEqual(energyTrendWindow([], '2026-02').map(row => row.periodMonth), ['2025-09','2025-10','2025-11','2025-12','2026-01','2026-02'])
  assert.deepEqual(energyTrendWindow([]), [])
})

test('rankings never compare kWh and cubic metres', () => {
  const rows = [{ meterType:'WATER', usageAmount:9999 }, { meterType:'ELECTRIC', usageAmount:10 }, { meterType:'ELECTRIC', usageAmount:42 }]
  assert.deepEqual(energyRanking(rows, 'ELECTRIC').map(row => row.usageAmount), [42,10])
  assert.equal(rows[0].usageAmount,9999)
})
