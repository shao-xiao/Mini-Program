export function summaryCard(label, value, desc, tone = '') {
  return {
    label,
    value: value ?? 0,
    desc: desc || '',
    tone
  }
}

export function buildBusinessSummaryCards({
  totalLabel,
  total,
  totalDesc,
  filtered,
  filteredLabel = '筛选结果',
  filteredDesc = '当前筛选条件命中',
  cards = []
}) {
  return [
    summaryCard(totalLabel, total, totalDesc),
    summaryCard(filteredLabel, filtered, filteredDesc, 'primary'),
    ...cards.filter(Boolean)
  ]
}

export function countBy(rows, predicate) {
  return rows.filter(predicate).length
}

export function sumBy(rows, selector) {
  return rows.reduce((sum, row) => sum + Number(selector(row) || 0), 0)
}

export function moneyValue(value) {
  return `¥ ${Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}
