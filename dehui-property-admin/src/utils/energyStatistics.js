export const energyChartTypes = [
  { value: 'ELECTRIC', label: '电 · kWh', unit: 'kWh', field: 'electricUsage', countField: 'electricRecordCount', color: '#d93025' },
  { value: 'WATER', label: '水 · m³', unit: 'm³', field: 'waterUsage', countField: 'waterRecordCount', color: '#6789a7' },
  { value: 'GAS', label: '燃气 · m³', unit: 'm³', field: 'gasUsage', countField: 'gasRecordCount', color: '#c79a60' }
]

export function energyTrendWindow(rows, selectedMonth = '') {
  const validMonth = month => /^\d{4}-(0[1-9]|1[0-2])$/.test(String(month || ''))
  const valid = rows.filter(row => validMonth(row.periodMonth))
  const end = validMonth(selectedMonth) ? selectedMonth : valid.map(row => row.periodMonth).sort().at(-1)
  if (!end) return []
  const [year, month] = end.split('-').map(Number)
  const byMonth = new Map(valid.map(row => [row.periodMonth, row]))
  return Array.from({ length: 6 }, (_, index) => {
    const date = new Date(year, month - 6 + index, 1)
    const periodMonth = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
    return { periodMonth, record: byMonth.get(periodMonth) || null }
  })
}

export function energyRanking(rows, meterType) {
  return rows.filter(row => row.meterType === meterType)
    .map(row => ({ ...row, usageAmount: Number(row.usageAmount) || 0 }))
    .sort((a, b) => b.usageAmount - a.usageAmount)
    .slice(0, 10)
}

export function energyTrendSeries(window, type) {
  return window.map(item => item.record && Number(item.record[type.countField]) > 0
    ? Number(item.record[type.field]) || 0
    : null)
}

// Cumulative readings belong to one physical meter and must never be summed.
export function energyReadingMeters(rows, meterType = '') {
  const meters = new Map()
  for (const row of rows) {
    if (row.meterId == null || (meterType && row.meterType !== meterType)) continue
    const key = String(row.meterId)
    if (!meters.has(key)) meters.set(key, { ...row, meterId: key, readings: [] })
    meters.get(key).readings.push({ ...row })
  }
  return [...meters.values()].map(meter => ({
    ...meter,
    readings: meter.readings.sort((a, b) => String(a.periodMonth).localeCompare(String(b.periodMonth))
      || String(a.readingDate).localeCompare(String(b.readingDate)) || Number(a.id) - Number(b.id))
  })).sort((a, b) => String(a.meterNo || a.meterName).localeCompare(String(b.meterNo || b.meterName), 'zh-CN', { numeric: true }))
}

export function energyReadingValue(value) {
  return value == null || value === '' || !Number.isFinite(Number(value)) ? null : Number(value)
}
