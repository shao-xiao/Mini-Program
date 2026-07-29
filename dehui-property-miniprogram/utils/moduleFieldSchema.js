const api = require('./request')

const SURFACES = Object.freeze({
  form: 'MINIPROGRAM_FORM',
  detail: 'MINIPROGRAM_DETAIL'
})

async function loadModuleFieldSchema(moduleKey, surface = SURFACES.form) {
  const schema = await api.get(`/mobile/module-fields/${moduleKey}`, { surface }, { silent: true })
  return {
    version: Number(schema && schema.version) || 1,
    moduleKey: String(schema && schema.moduleKey || moduleKey),
    fields: normalizeFields(schema && schema.fields)
  }
}

function normalizeFields(fields) {
  return (Array.isArray(fields) ? fields : []).map((field, index) => {
    const type = String(field.dataType || field.fieldType || field.type || 'TEXT').toUpperCase()
    return {
      ...field,
      fieldKey: String(field.fieldKey || field.apiField || field.key || ''),
      apiField: String(field.apiField || field.fieldKey || field.key || ''),
      dataType: ({ LONG_TEXT: 'TEXTAREA', SINGLE_SELECT: 'SELECT', MULTI_SELECT: 'MULTISELECT', CHECKBOX: 'BOOLEAN' })[type] || type,
      editable: field.editable !== false && field.readonly !== true,
      options: (Array.isArray(field.options) ? field.options : []).map(option => ({
        value: String(option.code ?? option.optionCode ?? option.value ?? option.label),
        label: String(option.label ?? option.optionLabel ?? option.code ?? option.value)
      })),
      validation: field.validation || field.config || {},
      order: Number(field.order ?? field.displayOrder ?? index)
    }
  }).filter(field => field.fieldKey).sort((a, b) => a.order - b.order)
}

function normalizeFileValue(value) {
  let raw = value
  if (typeof raw === 'string') {
    try { raw = JSON.parse(raw) } catch (_) { raw = raw ? [raw] : [] }
  }
  if (raw && !Array.isArray(raw) && Array.isArray(raw.files)) raw = raw.files
  if (!Array.isArray(raw)) raw = raw == null ? [] : [raw]
  const files = raw.map((item, index) => {
    if (typeof item === 'string') return { url: item, sortOrder: index }
    const fileId = Number(item && (item.fileId || item.id))
    return {
      ...(item || {}),
      ...(fileId > 0 ? { fileId } : {}),
      sortOrder: Number(item && item.sortOrder) || index
    }
  }).filter(item => item.fileId || item.url)
  return { version: 1, files }
}

function buildSubmission(schema, values = {}) {
  const payload = {}
  const customFields = {}
  for (const field of schema && schema.fields || []) {
    const value = values[field.fieldKey]
    const normalized = ['IMAGE', 'ATTACHMENT'].includes(field.dataType) ? normalizeFileValue(value) : value
    if (field.source === 'CUSTOM') customFields[field.fieldKey] = normalized
    else payload[field.apiField] = normalized
  }
  payload.customFields = customFields
  return payload
}

function fieldByKey(schema, fieldKey) {
  return (schema && Array.isArray(schema.fields) ? schema.fields : [])
    .find(field => field && field.fieldKey === fieldKey)
}

function fieldOptions(schema, fieldKey, fallback = []) {
  const options = fieldByKey(schema, fieldKey)?.options
  if (!Array.isArray(options) || options.length === 0) return fallback
  return options.map(option => ({
    value: String(option.code ?? option.optionCode ?? option.value),
    label: String(option.label)
  }))
}

function fieldValidation(schema, fieldKey) {
  const validation = fieldByKey(schema, fieldKey)?.validation
  return validation && typeof validation === 'object' ? validation : {}
}

function requiredError(schema, values) {
  const fields = schema && Array.isArray(schema.fields) ? schema.fields : []
  for (const field of fields) {
    if (!field.required || field.readonly) continue
    const apiField = String(field.apiField || field.fieldKey || '')
    if (apiField.includes('.') || apiField.endsWith('FileIds')) continue
    const value = values && values[apiField]
    if (value === undefined || value === null || String(value).trim() === '') {
      return `请填写${field.label || field.fieldKey}`
    }
  }
  return ''
}

module.exports = {
  SURFACES,
  loadModuleFieldSchema,
  fieldByKey,
  fieldOptions,
  fieldValidation,
  requiredError,
  normalizeFields,
  normalizeFileValue,
  buildSubmission
}
