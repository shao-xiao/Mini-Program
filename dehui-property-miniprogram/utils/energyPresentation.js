const METER_TYPE_PRESENTATION = {
  ELECTRIC: { label: '电力', shortLabel: '电', unitLabel: '度', tone: 'electric' },
  ELECTRICITY: { label: '电力', shortLabel: '电', unitLabel: '度', tone: 'electric' },
  WATER: { label: '用水', shortLabel: '水', unitLabel: 'm³', tone: 'water' },
  GAS: { label: '燃气', shortLabel: '气', unitLabel: 'm³', tone: 'gas' },
  OTHER: { label: '其他', shortLabel: '能', unitLabel: '', tone: 'other' }
}

const BILL_STATUS_PRESENTATION = {
  NOT_GENERATED: { label: '待生成账单', tone: 'pending' },
  BASIS_CONFIRMED: { label: '费用依据已确认', tone: 'confirmed' },
  GENERATED: { label: '已生成账单', tone: 'generated' },
  POSTED: { label: '已入账', tone: 'posted' },
  WITHDRAWN: { label: '已撤回', tone: 'withdrawn' }
}

function meterTypePresentation(value) {
  const key = String(value || 'OTHER').toUpperCase()
  return METER_TYPE_PRESENTATION[key] || {
    label: value || '其他',
    shortLabel: '能',
    unitLabel: '',
    tone: 'other'
  }
}

function billStatusPresentation(value) {
  const key = String(value || '').toUpperCase()
  return BILL_STATUS_PRESENTATION[key] || {
    label: value || '状态未知',
    tone: 'neutral'
  }
}

function formatEnergyNumber(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  const [integer, decimal] = number.toFixed(2).split('.')
  return `${integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${decimal}`
}

function presentEnergySummary(payload) {
  const source = payload && typeof payload === 'object' ? payload : {}
  const tenantRows = Array.isArray(source.byTenant) ? source.byTenant : []
  const highestSettlement = tenantRows.reduce(
    (maximum, item) => Math.max(maximum, Number(item.settlementAmount) || 0),
    0
  )
  return {
    ...source,
    byType: (Array.isArray(source.byType) ? source.byType : []).map(item => {
      const type = meterTypePresentation(item.meterType)
      return {
        ...item,
        meterTypeLabel: type.label,
        meterTypeShortLabel: type.shortLabel,
        meterTypeUnitLabel: type.unitLabel,
        meterTypeTone: type.tone,
        usageText: formatEnergyNumber(item.usageAmount),
        settlementText: formatEnergyNumber(item.settlementAmount),
        abnormalCount: Number(item.abnormalCount || 0)
      }
    }),
    byTenant: tenantRows.map((item, index) => {
      const settlementAmount = Number(item.settlementAmount) || 0
      return {
        ...item,
        rankText: String(index + 1).padStart(2, '0'),
        rankTone: index < 3 ? `top-${index + 1}` : 'regular',
        settlementText: formatEnergyNumber(item.settlementAmount),
        settlementRatio: highestSettlement > 0
          ? Math.max(settlementAmount > 0 ? 8 : 0, (settlementAmount / highestSettlement) * 100).toFixed(2)
          : '0',
        abnormalCount: Number(item.abnormalCount || 0)
      }
    })
  }
}

function presentEnergyReading(item) {
  const source = item && typeof item === 'object' ? item : {}
  const type = meterTypePresentation(source.meterType)
  const status = billStatusPresentation(source.billStatus)
  return {
    ...source,
    meterTypeLabel: type.label,
    meterTypeShortLabel: type.shortLabel,
    meterTypeUnitLabel: type.unitLabel,
    meterTypeTone: type.tone,
    billStatusLabel: status.label,
    billStatusTone: status.tone,
    previousReadingText: formatEnergyNumber(source.previousReading),
    currentReadingText: formatEnergyNumber(source.currentReading),
    usageText: formatEnergyNumber(source.usageAmount),
    unitPriceText: formatEnergyNumber(source.unitPrice),
    settlementText: formatEnergyNumber(source.settlementAmount),
    isAbnormal: source.abnormal === true || Number(source.abnormal) === 1
  }
}

module.exports = {
  meterTypePresentation,
  billStatusPresentation,
  formatEnergyNumber,
  presentEnergySummary,
  presentEnergyReading
}
