const assert = require('node:assert/strict')
const test = require('node:test')

const { presentEnergyDetailRows } = require('../utils/energyDetailPresentation')

test('energy detail uses Chinese labels and readable business status values', () => {
  const rows = presentEnergyDetailRows({
    id: 35,
    periodMonth: '2026-07',
    readingDate: '2026-07-30',
    previousReading: 20,
    currentReading: 100,
    usage: 96,
    unitPriceSnapshot: 1,
    amount: 96,
    basisStatus: 'NOT_GENERATED',
    meterNo: 'test-03',
    meterType: 'WATER',
    unit: 'm³',
    customFields: {}
  })

  assert.deepEqual(rows.map(item => item.label), [
    '账期',
    '抄表日期',
    '表具编号',
    '能耗类型',
    '上期读数',
    '本次读数',
    '本期用量',
    '计量单位',
    '单价快照',
    '结算金额',
    '计费状态'
  ])
  assert.equal(rows.find(item => item.key === 'meterType').value, '用水')
  assert.equal(rows.find(item => item.key === 'basisStatus').value, '待生成账单')
  assert.equal(rows.some(item => item.key === 'id' || item.key === 'customFields'), false)
})
