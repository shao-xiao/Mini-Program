import { normalizeCustomFieldType } from './customFieldSchema.js'

const FILE_TYPES = new Set(['image', 'attachment'])

export const isCustomFieldFileType = (fieldOrType) => {
  const rawType = typeof fieldOrType === 'object'
    ? (fieldOrType?.type || fieldOrType?.fieldType)
    : fieldOrType
  return FILE_TYPES.has(normalizeCustomFieldType(rawType))
}

const parseJsonValue = (value) => {
  if (typeof value !== 'string') return value
  const text = value.trim()
  if (!text || (!text.startsWith('[') && !text.startsWith('{'))) return value
  try {
    return JSON.parse(text)
  } catch {
    return value
  }
}

const normalizeUrl = (url) => {
  if (typeof url !== 'string') return ''
  return url.replace(/^\/api\/files\/public\//, '/files/public/')
}

const toFileId = (value) => {
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
}

const normalizeFileItem = (item, index) => {
  if (typeof item === 'number') {
    return { fileId: toFileId(item), id: toFileId(item), name: `文件 ${item}`, url: '', mimeType: '', size: 0, sortOrder: index }
  }
  if (typeof item === 'string') {
    const id = toFileId(item)
    if (id && String(id) === item.trim()) {
      return { fileId: id, id, name: `文件 ${id}`, url: '', mimeType: '', size: 0, sortOrder: index }
    }
    const url = normalizeUrl(item)
    return { fileId: null, id: null, name: url.split('/').pop() || '历史文件', url, mimeType: '', size: 0, sortOrder: index, legacy: true }
  }
  if (!item || typeof item !== 'object') return null
  const fileId = toFileId(item.fileId ?? item.id)
  const url = normalizeUrl(item.url || item.fileUrl || '')
  return {
    fileId,
    id: fileId,
    name: item.name || item.originalName || item.fileName || url.split('/').pop() || (fileId ? `文件 ${fileId}` : '历史文件'),
    url,
    mimeType: item.mimeType || item.contentType || '',
    size: Number(item.size ?? item.fileSize) || 0,
    sortOrder: Number.isFinite(Number(item.sortOrder)) ? Number(item.sortOrder) : index,
    legacy: Boolean(item.legacy || (!fileId && url)),
    status: item.status
  }
}

export const normalizeCustomFieldFiles = (value) => {
  const parsed = parseJsonValue(value)
  let items = parsed
  if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
    items = Array.isArray(parsed.files) ? parsed.files : [parsed]
  }
  if (!Array.isArray(items)) items = items == null || items === '' ? [] : [items]
  return items
    .map(normalizeFileItem)
    .filter(Boolean)
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((item, index) => ({ ...item, sortOrder: index }))
}

export const normalizeAttachmentValues = normalizeCustomFieldFiles

export const serializeCustomFieldFiles = (value) => ({
  version: 1,
  files: normalizeCustomFieldFiles(value).map((file, index) => {
    if (file.fileId) return { fileId: file.fileId, sortOrder: index }
    return {
      url: file.url,
      name: file.name,
      sortOrder: index,
      legacy: true
    }
  })
})

const emptyValue = (field) => {
  const type = normalizeCustomFieldType(field?.type || field?.fieldType)
  if (type === 'multiSelect' || FILE_TYPES.has(type)) return []
  if (type === 'boolean') return false
  return ''
}

export const coerceCustomFieldValue = (field, value) => {
  const type = normalizeCustomFieldType(field?.type || field?.fieldType)
  if (FILE_TYPES.has(type)) return normalizeCustomFieldFiles(value)
  if (type === 'multiSelect') {
    const parsed = parseJsonValue(value)
    return Array.isArray(parsed) ? parsed : (parsed == null || parsed === '' ? [] : [parsed])
  }
  if (type === 'boolean') return value === true || value === 1 || value === '1' || value === 'true'
  if (type === 'number') return value == null || value === '' ? '' : Number(value)
  return value ?? emptyValue(field)
}

export const mergeCustomFieldValues = (fields = [], values = {}) => Object.fromEntries(
  fields.map((field) => {
    const key = field.key || field.fieldKey
    return [key, coerceCustomFieldValue(field, values?.[key])]
  })
)

export const buildCustomFieldForm = (fields = [], values = {}) => mergeCustomFieldValues(fields, values)

export const normalizeCustomFieldPayload = (fields = [], values = {}) => Object.fromEntries(
  fields.map((field) => {
    const key = field.key || field.fieldKey
    const type = normalizeCustomFieldType(field?.type || field?.fieldType)
    const value = values?.[key]
    if (FILE_TYPES.has(type)) return [key, serializeCustomFieldFiles(value)]
    if (type === 'multiSelect') return [key, Array.isArray(value) ? value : []]
    if (type === 'number') return [key, value === '' || value == null ? null : Number(value)]
    return [key, value ?? null]
  })
)

export const buildCustomFieldCellPayload = (field, value) => {
  const key = field.key || field.fieldKey
  return normalizeCustomFieldPayload([field], { [key]: value })
}

export const validateCustomFieldValue = (field, value) => {
  const type = normalizeCustomFieldType(field?.type || field?.fieldType)
  const label = field.label || field.name || field.fieldName || '该字段'
  const empty = value == null || value === '' || (Array.isArray(value) && value.length === 0)
  if (field.required && empty) return `${label}不能为空`
  if (FILE_TYPES.has(type)) {
    const files = normalizeCustomFieldFiles(value)
    const fallback = type === 'image' ? 9 : 10
    const maxFiles = Math.min(30, Math.max(1, Number(field?.config?.maxFiles ?? field?.maxFiles ?? fallback) || fallback))
    if (files.length > maxFiles) return `${label}最多上传${maxFiles}个文件`
    if (files.some((file) => !file.fileId && !file.url)) return `${label}包含无效文件`
  }
  return ''
}

export const formatCustomFieldValue = (field, value) => {
  const type = normalizeCustomFieldType(field?.type || field?.fieldType)
  if (FILE_TYPES.has(type)) {
    const files = normalizeCustomFieldFiles(value)
    return files.length ? `${files.length}个${type === 'image' ? '图片' : '附件'}` : '-'
  }
  if (type === 'multiSelect') return Array.isArray(value) ? value.join('、') : (value || '-')
  if (type === 'boolean') return value ? '是' : '否'
  return value == null || value === '' ? '-' : String(value)
}

export const customFieldRecordDisplayValue = formatCustomFieldValue
