import test from 'node:test'
import assert from 'node:assert/strict'
import { buildBusinessSummaryCards } from './businessSummary.js'

test('buildBusinessSummaryCards returns module business cards without a current-page card', () => {
  const cards = buildBusinessSummaryCards({
    totalLabel: '设备总数',
    total: 502,
    totalDesc: '全部设备档案',
    filtered: 19,
    filteredDesc: '当前筛选条件命中',
    cards: [
      { label: '正常设备', value: 18, desc: '可正常使用', tone: 'success' },
      { label: '需关注', value: 1, desc: '故障/维修/停用', tone: 'danger' }
    ]
  })

  assert.deepEqual(cards.map(card => card.label), ['设备总数', '筛选结果', '正常设备', '需关注'])
  assert.equal(cards[0].value, 502)
  assert.equal(cards[1].value, 19)
  assert.equal(cards.some(card => card.label === '当前页'), false)
  assert.equal(cards[3].tone, 'danger')
})
