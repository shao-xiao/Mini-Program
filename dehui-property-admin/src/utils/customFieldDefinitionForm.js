import { isGroupableFieldType } from '../config/fieldTypes.js'

export const CUSTOM_FIELD_DEFINITION_TYPES = Object.freeze([
  { value: 'TEXT', label: '文本', uiType: 'text' },
  { value: 'LONG_TEXT', label: '多行文本', uiType: 'long-text' },
  { value: 'NUMBER', label: '数字', uiType: 'number' },
  { value: 'CURRENCY', label: '金额', uiType: 'money' },
  { value: 'SINGLE_SELECT', label: '单选', uiType: 'select' },
  { value: 'MULTI_SELECT', label: '多选', uiType: 'multi-select' },
  { value: 'DATE', label: '日期', uiType: 'date' },
  { value: 'DATETIME', label: '日期时间', uiType: 'datetime' },
  { value: 'BOOLEAN', label: '是/否', uiType: 'checkbox' },
  { value: 'PHONE', label: '电话号码', uiType: 'phone' },
  { value: 'EMAIL', label: 'Email', uiType: 'email' },
  { value: 'URL', label: 'URL', uiType: 'url' },
  { value: 'IMAGE', label: '图片', uiType: 'image' },
  { value: 'ATTACHMENT', label: '附件', uiType: 'attachment' }
])

const TYPE_BY_BACKEND = new Map(CUSTOM_FIELD_DEFINITION_TYPES.map(item => [item.value, item]))
const DISPLAY_NAME_BY_TYPE = new Map(CUSTOM_FIELD_DEFINITION_TYPES.map(item => [item.value, item.label]))
const OPTION_TYPES = new Set(['SINGLE_SELECT', 'MULTI_SELECT'])
const NUMERIC_TYPES = new Set(['NUMBER', 'CURRENCY'])
const FILE_TYPES = new Set(['IMAGE', 'ATTACHMENT'])
const DEFAULT_SURFACES = Object.freeze({
  adminList: Object.freeze({ visible: true, editable: false, order: 0 }),
  adminForm: Object.freeze({ visible: true, editable: true, order: 0 }),
  adminDetail: Object.freeze({ visible: true, editable: false, order: 0 }),
  miniProgramForm: Object.freeze({ visible: true, editable: true, order: 0 }),
  miniProgramDetail: Object.freeze({ visible: true, editable: false, order: 0 })
})

export function createCustomFieldDefinitionDraft(overrides = {}) {
  const fieldType = normalizeBackendType(overrides.fieldType)
  const config = overrides.config && typeof overrides.config === 'object' ? overrides.config : {}
  return {
    id: overrides.id ?? null,
    fieldCode: String(overrides.fieldCode || ''),
    fieldName: String(overrides.fieldName || ''),
    fieldType,
    description: String(overrides.description || ''),
    required: Boolean(overrides.required),
    uniqueValue: FILE_TYPES.has(fieldType) ? false : Boolean(overrides.uniqueValue),
    defaultValue: FILE_TYPES.has(fieldType) ? '' : (overrides.defaultValue ?? ''),
    version: Number(overrides.version || 0),
    options: normalizeDraftOptions(overrides.options),
    maxLength: nullableNumber(overrides.maxLength ?? config.maxLength),
    decimalPlaces: nullableNumber(overrides.decimalPlaces ?? config.scale ?? config.decimalPlaces) ?? 2,
    currency: String(overrides.currency || config.currency || 'CNY'),
    minimum: nullableNumber(overrides.minimum ?? config.min ?? config.minimum),
    maximum: nullableNumber(overrides.maximum ?? config.max ?? config.maximum),
    dateFormat: String(overrides.dateFormat || config.dateFormat || (fieldType === 'DATETIME' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD')),
    allowMultiple: config.allowMultiple !== false,
    maxFiles: validMaxFiles(overrides.maxFiles ?? config.maxFiles, fieldType === 'IMAGE' ? 9 : 10),
    surfaces: normalizeSurfaces(overrides.surfaces ?? config.surfaces)
  }
}

export function definitionToCustomFieldDraft(definition = {}) {
  return createCustomFieldDefinitionDraft({
    ...definition,
    fieldCode: definition.fieldCode || definition.key,
    fieldName: definition.fieldName || definition.label,
    options: Array.isArray(definition.optionItems)
      ? definition.optionItems.map(option => ({
          optionCode: option.optionCode || option.value || '',
          label: option.optionLabel || option.label || '',
          color: option.color || '',
          status: option.status || 'ENABLED'
        }))
      : definition.options
  })
}

export function customFieldDefinitionValidation(draft = {}) {
  const fieldName = String(draft.fieldName || '').trim()
  if (!fieldName) return invalid('字段名称不能为空')
  if (fieldName.length > 100) return invalid('字段名称最多 100 个字符')
  if (/[<>]/.test(fieldName)) return invalid('字段名称包含不允许的字符')
  const fieldType = normalizeBackendType(draft.fieldType)
  if (!TYPE_BY_BACKEND.has(fieldType)) return invalid('字段类型尚未开放')
  if (OPTION_TYPES.has(fieldType)) {
    const labels = normalizeDraftOptions(draft.options).map(option => option.label.trim()).filter(Boolean)
    if (labels.length === 0) return invalid('单选和多选字段至少需要一个选项')
    if (new Set(labels.map(label => label.toLocaleLowerCase('zh-CN'))).size !== labels.length) return invalid('字段选项不能重复')
  }
  if (NUMERIC_TYPES.has(fieldType)) {
    const minimum = nullableNumber(draft.minimum)
    const maximum = nullableNumber(draft.maximum)
    if (minimum !== null && maximum !== null && minimum > maximum) return invalid('最小值不能大于最大值')
  }
  if (FILE_TYPES.has(fieldType)) {
    const maxFiles = Number(draft.maxFiles)
    if (!Number.isInteger(maxFiles) || maxFiles < 1 || maxFiles > 30) return invalid('文件最大数量必须是 1 到 30 之间的整数')
  }
  return { valid: true, message: '' }
}

export function buildCustomFieldDefinitionPayload(draft = {}) {
  const fieldType = normalizeBackendType(draft.fieldType)
  const typeDefinition = TYPE_BY_BACKEND.get(fieldType) || TYPE_BY_BACKEND.get('TEXT')
  const payload = {
    fieldName: String(draft.fieldName || '').trim(),
    fieldType,
    description: String(draft.description || '').trim(),
    required: Boolean(draft.required),
    uniqueValue: FILE_TYPES.has(fieldType) ? false : Boolean(draft.uniqueValue),
    defaultValue: FILE_TYPES.has(fieldType) ? '' : (draft.defaultValue ?? ''),
    sortable: !FILE_TYPES.has(fieldType),
    groupable: isGroupableFieldType(typeDefinition.uiType),
    config: {
      ...typeConfig(draft, fieldType),
      surfaces: normalizeSurfaces(draft.surfaces)
    }
  }
  const fieldCode = String(draft.fieldCode || '').trim()
  if (fieldCode) payload.fieldCode = fieldCode
  if (Number(draft.version) > 0) payload.version = Number(draft.version)
  if (OPTION_TYPES.has(fieldType)) {
    payload.options = normalizeDraftOptions(draft.options)
      .filter(option => option.label.trim())
      .map((option, index) => ({
        optionCode: option.optionCode || '',
        optionLabel: option.label.trim(),
        color: option.color || '',
        displayOrder: index
      }))
  }
  return payload
}

export function customFieldTypeSupportsOptions(fieldType) {
  return OPTION_TYPES.has(normalizeBackendType(fieldType))
}

export function customFieldTypeIsNumeric(fieldType) {
  return NUMERIC_TYPES.has(normalizeBackendType(fieldType))
}

export function customFieldTypeIsDate(fieldType) {
  return ['DATE', 'DATETIME'].includes(normalizeBackendType(fieldType))
}

export function customFieldTypeIsFile(fieldType) {
  return FILE_TYPES.has(normalizeBackendType(fieldType))
}

export function customFieldDefinitionTypeLabel(fieldType) {
  return DISPLAY_NAME_BY_TYPE.get(normalizeBackendType(fieldType)) || '文本'
}

function normalizeBackendType(value) {
  const normalized = String(value || 'TEXT').trim().toUpperCase().replaceAll('-', '_')
  return TYPE_BY_BACKEND.has(normalized) ? normalized : 'TEXT'
}

function normalizeDraftOptions(options) {
  return (Array.isArray(options) ? options : []).map(option => {
    if (option && typeof option === 'object') {
      return {
        optionCode: String(option.optionCode || option.value || ''),
        label: String(option.label || option.optionLabel || ''),
        color: String(option.color || ''),
        status: String(option.status || 'ENABLED')
      }
    }
    return { optionCode: '', label: String(option || ''), color: '', status: 'ENABLED' }
  })
}

function typeConfig(draft, fieldType) {
  if (FILE_TYPES.has(fieldType)) return { allowMultiple: true, maxFiles: validMaxFiles(draft.maxFiles, fieldType === 'IMAGE' ? 9 : 10) }
  if (['TEXT', 'LONG_TEXT', 'PHONE', 'EMAIL', 'URL'].includes(fieldType)) {
    const maxLength = nullableNumber(draft.maxLength)
    return maxLength === null ? {} : { maxLength }
  }
  if (NUMERIC_TYPES.has(fieldType)) {
    const config = { scale: Math.min(Math.max(Number(draft.decimalPlaces ?? 2), 0), 6) }
    if (fieldType === 'CURRENCY') config.currency = String(draft.currency || 'CNY')
    const minimum = nullableNumber(draft.minimum)
    const maximum = nullableNumber(draft.maximum)
    if (minimum !== null) config.min = minimum
    if (maximum !== null) config.max = maximum
    return config
  }
  if (['DATE', 'DATETIME'].includes(fieldType)) return { dateFormat: String(draft.dateFormat || (fieldType === 'DATETIME' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD')) }
  return {}
}

function validMaxFiles(value, fallback) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= 30 ? parsed : fallback
}

function normalizeSurfaces(value) {
  const configured = value && typeof value === 'object' ? value : {}
  return Object.fromEntries(Object.entries(DEFAULT_SURFACES).map(([key, defaults]) => {
    const surface = configured[key] && typeof configured[key] === 'object' ? configured[key] : {}
    return [key, {
      visible: surface.visible !== false,
      editable: key.endsWith('Form') ? surface.editable !== false : false,
      order: Number.isInteger(Number(surface.order)) ? Number(surface.order) : defaults.order
    }]
  }))
}

function nullableNumber(value) {
  if (value === '' || value === undefined || value === null) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function invalid(message) {
  return { valid: false, message }
}
