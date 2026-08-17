export function buildMetricCards(definitions = [], data = {}, states = {}) {
  return definitions
    .filter(definition => !resolveSetting(definition.hidden, data, false))
    .map(definition => {
      const rawValue = typeof definition.value === 'function'
        ? definition.value(data)
        : readField(data, definition.field)
      const stateKey = definition.stateKey || definition.field || definition.key
      const metricState = stateKey ? states[stateKey] : null
      const unavailable = metricState?.loading || metricState?.error
      const value = unavailable
        ? '--'
        : typeof definition.format === 'function'
          ? definition.format(rawValue, data)
          : formatMetricValue(rawValue, definition)
      const trend = resolveMetricTrend(definition, data)

      const card = {
        label: definition.title || definition.label || definition.field || '-',
        value,
        desc: metricState?.loading
          ? '加载中'
          : metricState?.error
            ? '指标加载失败'
            : resolveSetting(definition.desc, data, ''),
        tone: unavailable ? 'neutral' : resolveSetting(definition.tone, data, 'neutral')
      }
      if (trend && !unavailable) card.trend = trend
      return card
    })
}

export function formatMetricValue(rawValue, definition = {}) {
  if (rawValue === undefined || rawValue === null) return '--'
  const value = rawValue
  const type = definition.type || 'value'

  if (type === 'count') {
    return formatNumber(value, definition.decimals ?? 0)
  }

  if (type === 'money') {
    const currency = definition.currency || 'CNY'
    const numberValue = toFiniteNumber(value)
    if (numberValue === null) return String(value)
    return new Intl.NumberFormat('zh-CN', {
      style: 'currency',
      currency,
      minimumFractionDigits: definition.decimals ?? 2,
      maximumFractionDigits: definition.decimals ?? 2
    }).format(numberValue)
  }

  if (type === 'ratio') {
    const numberValue = toFiniteNumber(value)
    if (numberValue === null) return String(value)
    const ratioValue = definition.scale === 'fraction' ? numberValue * 100 : numberValue
    return `${formatNumber(ratioValue, definition.decimals ?? 1)}%`
  }

  if (type === 'trend') {
    const numberValue = toFiniteNumber(value)
    if (numberValue === null) return String(value)
    const prefix = numberValue > 0 ? '+' : ''
    return `${prefix}${formatNumber(numberValue, definition.decimals ?? 1)}%`
  }

  return value
}

function readField(data, field) {
  if (!field) return undefined
  return String(field).split('.').reduce((value, key) => value?.[key], data)
}

function resolveSetting(setting, data, fallback) {
  if (typeof setting === 'function') return setting(data)
  return setting ?? fallback
}

function resolveMetricTrend(definition, data) {
  const configuredTrend = definition.trendField
    ? readField(data, definition.trendField)
    : resolveSetting(definition.trend, data, null)
  if (configuredTrend === undefined || configuredTrend === null || configuredTrend === '') return null

  const trend = configuredTrend && typeof configuredTrend === 'object'
    ? configuredTrend
    : { value: configuredTrend }
  const numberValue = toFiniteNumber(trend.value)
  if (numberValue === null) return null
  const direction = trend.direction || (numberValue > 0 ? 'up' : numberValue < 0 ? 'down' : 'flat')
  const favorableDirection = trend.favorableDirection || definition.favorableDirection || 'up'

  return {
    value: trend.label || `${numberValue > 0 ? '+' : ''}${formatNumber(numberValue, trend.decimals ?? 1)}%`,
    direction,
    favorable: direction === 'flat' ? null : direction === favorableDirection
  }
}

function formatNumber(value, decimals) {
  const numberValue = toFiniteNumber(value)
  if (numberValue === null) return String(value)
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals
  }).format(numberValue)
}

function toFiniteNumber(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}
