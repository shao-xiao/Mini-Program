import test from 'node:test'
import assert from 'node:assert/strict'
import { energyChartTypes, energyTrendWindow, energyTrendSeries, energyRanking, energyReadingMeters, energyReadingValue, energyOverallTrend } from './energyStatistics.js'

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


test('keeps cumulative readings separate by physical meter and sorts each period', () => {
  const rows = [
    { id: 2, meterId: 1, meterName: 'same', meterType: 'ELECTRIC', periodMonth: '2026-09', previousReading: 53733.84, currentReading: 62842.05 },
    { id: 3, meterId: 2, meterName: 'same', meterType: 'ELECTRIC', periodMonth: '2026-09', previousReading: 135.33, currentReading: 174.60, multiplier: 30 },
    { id: 1, meterId: 1, meterName: 'same', meterType: 'ELECTRIC', periodMonth: '2026-08', previousReading: 33262.02, currentReading: 53733.84 },
    { id: 4, meterId: 3, meterType: 'WATER', periodMonth: '2026-09', previousReading: 0, currentReading: 36 }
  ]
  const meters = energyReadingMeters(rows, 'ELECTRIC')
  assert.equal(meters.length, 2)
  assert.deepEqual(meters.find(row => row.meterId === '1').readings.map(row => row.currentReading), [53733.84, 62842.05])
  assert.equal(meters.find(row => row.meterId === '2').readings[0].currentReading, 174.60)
  assert.equal(rows[0].id, 2)
  assert.equal(energyReadingMeters(rows, 'WATER')[0].readings[0].previousReading, 0)
})

test('missing or invalid meter readings remain empty while genuine zero is preserved', () => {
  assert.deepEqual([null, undefined, '', 'bad', '0', 0, '12.30'].map(energyReadingValue), [null, null, null, null, 0, 0, 12.3])
  assert.deepEqual(energyReadingMeters([{ meterId: null }]), [])
})


test('overall usage compares all-meter totals by type and the immediately previous month', () => {
  const rows = [{ periodMonth:'2026-09',electricUsage:29249.27,electricRecordCount:9,waterUsage:45,waterRecordCount:2 }, { periodMonth:'2026-08',electricUsage:45176.16,electricRecordCount:9,waterUsage:39,waterRecordCount:2 }]
  const result = energyOverallTrend(rows, energyChartTypes[0])
  assert.equal(result[1].usage,29249.27)
  assert.equal(result[1].change,-15926.89)
  assert.equal(result[1].changeRate.toFixed(1),'-35.3')
  assert.equal(result[1].recordCount,9)
  assert.equal(energyOverallTrend(rows,energyChartTypes[1])[1].change,6)
  assert.equal(rows[0].periodMonth,'2026-09')
})
test('overall month comparison does not bridge missing months or invent a percentage from zero', () => {
  const rows = [{ periodMonth:'2026-08',electricUsage:0,electricRecordCount:1 }, { periodMonth:'2026-09',electricUsage:10,electricRecordCount:1 }, { periodMonth:'2026-11',electricUsage:20,electricRecordCount:1 },{periodMonth:'2026-12',electricUsage:0,electricRecordCount:0}]
  const result=energyOverallTrend(rows,energyChartTypes[0])
  assert.equal(result.length,3)
  assert.equal(result[1].change,10)
  assert.equal(result[1].changeRate,null)
  assert.equal(result[2].change,null)
})
