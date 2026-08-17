import { normalizeFieldType } from '../config/fieldTypes.js'

export function resolveCellValue(row = {}, field = {}) {
  if (typeof field.valueGetter === 'function') return field.valueGetter(row)
  if (field.custom) return row?.customFields?.[field.key] ?? field.defaultValue
  return row?.[field.key]
}

export function normalizeCellOption(option) {
  if (option && typeof option === 'object') {
    const value = option.value ?? option.label ?? ''
    return {
      value,
      label: String(option.label ?? value),
      tone: option.tone || option.type || 'info'
    }
  }
  return { value: option, label: String(option ?? ''), tone: 'info' }
}

export function resolveCellOption(value, field = {}, optionMap = field.optionMap) {
  if (optionMap instanceof Map) return optionMap.get(String(value)) || unknownCellOption(value)
  const options = typeof field.options === 'function' ? field.options() : field.options
  const normalizedOptions = Array.isArray(options) ? options.map(normalizeCellOption) : []
  return normalizedOptions.find(option => String(option.value) === String(value)) || unknownCellOption(value)
}

function unknownCellOption(value) {
  return { value, label: String(value ?? ''), tone: 'info' }
}

export function normalizeCellUsers(value) {
  const values = Array.isArray(value) ? value : [value]
  return values
    .filter(item => item !== undefined && item !== null && item !== '')
    .map(item => {
      if (item && typeof item === 'object') {
        const name = item.name || item.displayName || item.username || item.label || item.id || '-'
        return {
          id: item.id ?? item.userId ?? name,
          name: String(name),
          avatarUrl: item.avatarUrl || item.avatar || ''
        }
      }
      return { id: item, name: String(item), avatarUrl: '' }
    })
}

export function normalizeCellAttachments(value) {
  const values = Array.isArray(value) ? value : [value]
  return values
    .filter(item => item !== undefined && item !== null && item !== '')
    .map((item, index) => {
      if (item && typeof item === 'object') {
        const url = safeCellUrl(item.url || item.downloadUrl || '')
        return {
          id: item.id ?? url ?? index,
          name: String(item.name || item.fileName || `附件 ${index + 1}`),
          url
        }
      }
      const text = String(item)
      return {
        id: text || index,
        name: text.split('/').pop() || `附件 ${index + 1}`,
        url: safeCellUrl(text)
      }
    })
}

export function normalizeCellRelations(value, field = {}) {
  const values = Array.isArray(value) ? value : [value]
  const relation = field.relation || {}
  const idField = relation.idField || 'id'
  const labelField = relation.labelField || 'label'
  return values
    .filter(item => item !== undefined && item !== null && item !== '')
    .map(item => {
      if (item && typeof item === 'object') {
        const id = item[idField] ?? item.id ?? item.value ?? item.code ?? item[labelField]
        const label = item[labelField] ?? item.label ?? item.name ?? item.title ?? item.code ?? id
        return {
          id: id ?? String(label),
          label: String(label ?? '-'),
          entityType: String(item.entityType || relation.entityType || '')
        }
      }
      return { id: item, label: String(item), entityType: String(relation.entityType || '') }
    })
}

export function formatCellValue(value, field = {}) {
  if (value === undefined || value === null || value === '') return '-'
  const type = normalizeFieldType(field.type)

  if (type === 'number') {
    const numberValue = Number(value)
    if (!Number.isFinite(numberValue)) return String(value)
    const decimals = Number.isInteger(field.decimals) ? field.decimals : undefined
    return new Intl.NumberFormat('zh-CN', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals ?? 6
    }).format(numberValue)
  }

  if (type === 'money') {
    const numberValue = Number(value)
    if (!Number.isFinite(numberValue)) return String(value)
    return new Intl.NumberFormat('zh-CN', {
      style: 'currency',
      currency: field.currency || 'CNY',
      minimumFractionDigits: field.decimals ?? 2,
      maximumFractionDigits: field.decimals ?? 2
    }).format(numberValue)
  }

  if (type === 'date') {
    const text = String(value)
    if (field.format === 'datetime' && /^\d{4}-\d{2}-\d{2}/.test(text)) {
      return text.replace('T', ' ').slice(0, 16)
    }
    return /^\d{4}-\d{2}-\d{2}/.test(text) ? text.slice(0, 10) : text
  }

  if (type === 'datetime') {
    const text = String(value)
    return /^\d{4}-\d{2}-\d{2}T?/.test(text) ? text.replace('T', ' ').slice(0, 19) : text
  }

  if (type === 'checkbox') return value ? '是' : '否'
  if (Array.isArray(value)) return value.length > 0 ? value.join('、') : '-'
  return String(value)
}

export function safeCellUrl(value) {
  if (!value) return ''
  const url = String(value).trim()
  if (url.startsWith('/')) return url
  try {
    const parsed = new URL(url)
    return ['http:', 'https:'].includes(parsed.protocol) ? parsed.href : ''
  } catch {
    return ''
  }
}
