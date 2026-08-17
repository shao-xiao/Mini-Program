import test from 'node:test'
import assert from 'node:assert/strict'
import { buildMetricCards } from './metricPanel.js'

test('buildMetricCards resolves fields, functions and dynamic tones', () => {
  const cards = buildMetricCards([
    { title: '设备总数', field: 'total', desc: '全部设备' },
    {
      title: '维修中',
      field: 'status.maintenance',
      tone: data => data.status.maintenance > 0 ? 'warning' : 'neutral'
    },
    { title: '占比', value: data => data.rate, format: value => `${value}%` }
  ], {
    total: 12,
    status: { maintenance: 2 },
    rate: 16.7
  })

  assert.deepEqual(cards, [
    { label: '设备总数', value: 12, desc: '全部设备', tone: 'neutral' },
    { label: '维修中', value: 2, desc: '', tone: 'warning' },
    { label: '占比', value: '16.7%', desc: '', tone: 'neutral' }
  ])
})

test('buildMetricCards omits metrics hidden by configuration', () => {
  const cards = buildMetricCards([
    { title: '显示', field: 'visible' },
    { title: '隐藏', field: 'hidden', hidden: data => !data.canSeeHidden }
  ], { visible: 1, hidden: 2, canSeeHidden: false })

  assert.deepEqual(cards.map(card => card.label), ['显示'])
})

test('buildMetricCards formats count, money, ratio and trend metrics', () => {
  const cards = buildMetricCards([
    { title: '设备数量', field: 'count', type: 'count' },
    { title: '应收金额', field: 'amount', type: 'money' },
    { title: '出租率', field: 'rate', type: 'ratio', scale: 'fraction' },
    { title: '环比变化', field: 'change', type: 'trend' }
  ], {
    count: 1234,
    amount: 1234.5,
    rate: 0.856,
    change: 12.4
  })

  assert.deepEqual(cards.map(card => card.value), ['1,234', '¥1,234.50', '85.6%', '+12.4%'])
})

test('buildMetricCards resolves trend direction and favorable semantics', () => {
  const [card] = buildMetricCards([
    {
      title: '故障变化',
      field: 'faults',
      type: 'count',
      trendField: 'faultTrend',
      favorableDirection: 'down'
    }
  ], { faults: 3, faultTrend: -25 })

  assert.deepEqual(card.trend, {
    value: '-25.0%',
    direction: 'down',
    favorable: true
  })
})

test('failed and loading metrics render as unavailable instead of stale zero values', () => {
  const cards = buildMetricCards([
    { title: '成功指标', field: 'success', type: 'count', desc: '正常' },
    { title: '失败指标', field: 'failed', type: 'count', desc: '原说明' },
    { title: '加载指标', field: 'loading', type: 'money', desc: '原说明' }
  ], {
    success: 8,
    failed: null,
    loading: null
  }, {
    success: { loading: false, error: false },
    failed: { loading: false, error: true },
    loading: { loading: true, error: false }
  })

  assert.deepEqual(cards.map(card => [card.value, card.desc]), [
    ['8', '正常'],
    ['--', '指标加载失败'],
    ['--', '加载中']
  ])
})
