const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const {
  meterTypePresentation,
  billStatusPresentation,
  presentEnergySummary,
  presentEnergyReading
} = require(path.join(root, 'utils/energyPresentation'))

test('staff energy presentation translates backend enums without changing values', () => {
  assert.deepEqual(meterTypePresentation('ELECTRIC'), {
    label: '电力',
    shortLabel: '电',
    unitLabel: '度',
    tone: 'electric'
  })
  assert.deepEqual(billStatusPresentation('NOT_GENERATED'), {
    label: '待生成账单',
    tone: 'pending'
  })

  const summary = presentEnergySummary({
    byType: [{ meterType: 'WATER', usageAmount: 82.4, settlementAmount: 510.88, abnormalCount: 0 }],
    byTenant: [
      { id: 1, tenantName: '测试企业', usageAmount: 478.2, settlementAmount: 571.82, abnormalCount: 0 },
      { id: 2, tenantName: '第二企业', usageAmount: 100, settlementAmount: 285.91, abnormalCount: 0 }
    ]
  })
  assert.equal(summary.byType[0].usageText, '82.40')
  assert.equal(summary.byType[0].meterTypeUnitLabel, 'm³')
  assert.equal(summary.byTenant[0].rankText, '01')
  assert.equal(summary.byTenant[0].rankTone, 'top-1')
  assert.equal(summary.byTenant[0].settlementRatio, '100.00')
  assert.equal(summary.byTenant[0].settlementText, '571.82')
  assert.equal(summary.byTenant[1].settlementRatio, '50.00')
})

test('staff energy reading presentation separates readings and server-calculated facts', () => {
  const reading = presentEnergyReading({
    meterType: 'ELECTRIC',
    billStatus: 'GENERATED',
    previousReading: 14960.9,
    currentReading: 15405.7,
    usageAmount: 444.8,
    unitPrice: 0.82,
    settlementAmount: 364.74,
    abnormal: 0
  })
  assert.equal(reading.meterTypeLabel, '电力')
  assert.equal(reading.billStatusLabel, '已生成账单')
  assert.equal(reading.previousReadingText, '14,960.90')
  assert.equal(reading.settlementText, '364.74')
  assert.equal(reading.isAbnormal, false)
})

test('staff energy page renders readable sections and never exposes raw enums', () => {
  const markup = read('subPackages/staff/pages/energy/index.wxml')
  const script = read('subPackages/staff/pages/energy/index.js')
  assert.match(markup, /能源概览/)
  assert.match(markup, /企业费用依据/)
  assert.match(markup, /抄表记录/)
  assert.match(markup, /meterTypeUnitLabel/)
  assert.doesNotMatch(markup, /物业能耗台账|累计用量/)
  assert.match(markup, /上期读数/)
  assert.match(markup, /单价快照/)
  assert.doesNotMatch(markup, /\{\{item\.meterType\}\}|\{\{item\.billStatus\}\}/)
  assert.match(script, /presentEnergySummary/)
  assert.match(script, /presentEnergyReading/)
  assert.match(script, /energy\.staff\.reading\.create/)
})
