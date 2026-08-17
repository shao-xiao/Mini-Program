import { isGroupableFieldType, normalizeFieldType } from '../config/fieldTypes.js'

export function configurableWorkspaceFields(columns = []) {
  return columns.filter(column => !column.fixedOnly)
}

export function orderWorkspaceFields(columns = [], columnOrder = []) {
  const orderMap = new Map(columnOrder.map((key, index) => [key, index]))
  return configurableWorkspaceFields(columns)
    .map((column, sourceIndex) => ({ column, sourceIndex }))
    .sort((left, right) => {
      const leftIndex = orderMap.has(left.column.key) ? orderMap.get(left.column.key) : Number.MAX_SAFE_INTEGER
      const rightIndex = orderMap.has(right.column.key) ? orderMap.get(right.column.key) : Number.MAX_SAFE_INTEGER
      return leftIndex === rightIndex ? left.sourceIndex - right.sourceIndex : leftIndex - rightIndex
    })
    .map(item => item.column)
}

export function toggleWorkspaceFieldVisibility(columns = [], visibleColumns = [], key, checked) {
  const requiredKeys = configurableWorkspaceFields(columns)
    .filter(column => column.required)
    .map(column => column.key)
  const next = checked
    ? [...visibleColumns, key]
    : visibleColumns.filter(item => item !== key)
  return [...new Set([...requiredKeys, ...next])]
}

export function moveWorkspaceField(columnOrder = [], key, direction) {
  const next = [...columnOrder]
  const index = next.indexOf(key)
  const targetIndex = index + direction
  if (index < 0 || targetIndex < 0 || targetIndex >= next.length) return next
  ;[next[index], next[targetIndex]] = [next[targetIndex], next[index]]
  return next
}

export function reorderWorkspaceField(columnOrder = [], sourceKey, targetKey, insertAfter = false) {
  if (!sourceKey || !targetKey || sourceKey === targetKey) return [...columnOrder]
  const next = columnOrder.filter(key => key !== sourceKey)
  const targetIndex = next.indexOf(targetKey)
  if (targetIndex < 0) return [...columnOrder]
  next.splice(targetIndex + (insertAfter ? 1 : 0), 0, sourceKey)
  return next
}

export function setWorkspaceFixedColumn(fixedColumns = {}, key, side = '') {
  const next = { ...fixedColumns }
  if (side === 'left' || side === 'right') next[key] = side
  else delete next[key]
  return next
}

export function buildWorkspaceCustomField(form = {}, key = `custom_${Date.now()}`) {
  const label = String(form.label || '').trim()
  if (!label) return null
  const type = normalizeFieldType(form.type)
  const options = [...new Set(String(form.optionsText || '')
    .split(',')
    .map(item => item.trim())
    .filter(Boolean))]
  return {
    key,
    label,
    type,
    defaultValue: form.defaultValue ?? '',
    options,
    custom: true,
    editable: true,
    sortable: true,
    groupable: isGroupableFieldType(type)
  }
}

export function workspaceFieldTypeIcon(type = 'text') {
  return {
    text: 'A=',
    number: '#',
    money: '¥',
    select: '◎',
    status: '●',
    'multi-select': '☷',
    date: '▣',
    checkbox: '☑',
    user: '@',
    image: '图片',
    attachment: '附件',
    formula: 'fx',
    relation: '关联',
    phone: '☎',
    email: '✉',
    url: '↗'
  }[normalizeFieldType(type)] || 'A='
}
