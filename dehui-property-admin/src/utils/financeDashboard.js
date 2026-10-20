export const financeNumber = value => Number.isFinite(Number(value)) ? Number(value) : 0
export function financeRatio(value, total) {
  return financeNumber(total) > 0 ? financeNumber(value) / financeNumber(total) * 100 : null
}
export function financeMonthlyWindow(rows) {
  const valid = rows.filter(row => /^\d{4}-(0[1-9]|1[0-2])$/.test(String(row.dimension || '')))
  if (!valid.length) return []
  const months = valid.map(row => row.dimension).sort()
  const [year, month] = months.at(-1).split('-').map(Number)
  const byMonth = new Map(valid.map(row => [row.dimension, row]))
  return Array.from({ length: 12 }, (_, index) => {
    const date = new Date(year, month - 12 + index, 1)
    const dimension = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
    return { dimension, record: byMonth.get(dimension) || null }
  }).filter(item => item.dimension >= months[0])
}
export function financeTenantRows(rows, totalUnpaid) {
  return rows.map((row, index) => ({ ...row, rowId: row.tenantId ?? `unlinked-${index}`,
    tenantName: row.tenantName || '未关联租户',
    collectionRate: financeRatio(row.receivedAmount, row.receivableAmount),
    unpaidShare: financeRatio(row.unpaidAmount, totalUnpaid)
  })).sort((a, b) => financeNumber(b.unpaidAmount) - financeNumber(a.unpaidAmount))
}
export function financeChargeRows(rows) {
  const positiveTotal = rows.reduce((sum, row) => sum + Math.max(0, financeNumber(row.finalAmount)), 0)
  return rows.filter(row => financeNumber(row.finalAmount) !== 0).map(row => ({ ...row,
    amount: financeNumber(row.finalAmount),
    share: financeNumber(row.finalAmount) < 0 ? null : financeRatio(row.finalAmount, positiveTotal)
  })).sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount))
}
