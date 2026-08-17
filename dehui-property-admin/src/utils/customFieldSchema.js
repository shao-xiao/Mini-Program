const FIELD_TYPE_MAP = Object.freeze({
  TEXT: 'text',
  LONG_TEXT: 'long-text',
  NUMBER: 'number',
  CURRENCY: 'money',
  SINGLE_SELECT: 'select',
  MULTI_SELECT: 'multi-select',
  DATE: 'date',
  DATETIME: 'datetime',
  BOOLEAN: 'checkbox',
  PHONE: 'phone',
  EMAIL: 'email',
  URL: 'url',
  IMAGE: 'image',
  ATTACHMENT: 'attachment'
})

export function normalizeCustomFieldType(fieldType) {
  const normalized = String(fieldType || 'TEXT').trim().replaceAll('-', '_').toUpperCase()
  const mapped = {
    TEXT: 'text',
    LONG_TEXT: 'textarea',
    NUMBER: 'number',
    MONEY: 'number',
    CURRENCY: 'number',
    SINGLE_SELECT: 'select',
    MULTI_SELECT: 'multiSelect',
    DATE: 'date',
    DATETIME: 'datetime',
    BOOLEAN: 'boolean',
    CHECKBOX: 'boolean',
    PHONE: 'text',
    EMAIL: 'text',
    URL: 'text',
    IMAGE: 'image',
    ATTACHMENT: 'attachment'
  }
  return mapped[normalized] || String(fieldType || 'text')
}

export function normalizeCustomFieldDefinitions(definitions = []) {
  return (Array.isArray(definitions) ? definitions : [])
    .filter(definition => definition?.fieldCode || definition?.key)
    .map(normalizeCustomFieldDefinition)
    .sort((left, right) => left.displayOrder - right.displayOrder || left.label.localeCompare(right.label, 'zh-CN'))
}

export function normalizeCustomFieldDefinition(definition = {}) {
  const fieldCode = String(definition.fieldCode || definition.key || '').trim()
  const fieldType = canonicalizeFieldType(definition.fieldType || definition.type || '')
  const options = normalizeCustomFieldOptions(definition.optionItems, definition.options)
  const config = definition.config && typeof definition.config === 'object' ? definition.config : {}
  return {
    ...definition,
    key: fieldCode,
    fieldCode,
    label: String(definition.fieldName || definition.label || fieldCode).trim(),
    fieldName: String(definition.fieldName || definition.label || fieldCode).trim(),
    type: FIELD_TYPE_MAP[fieldType] || definition.type || 'text',
    fieldType: fieldType || 'TEXT',
    custom: true,
    platformCustom: true,
    editable: definition.editable !== false,
    filterable: definition.filterable !== false,
    sortable: definition.sortable !== false,
    groupable: Boolean(definition.groupable),
    required: Boolean(definition.required),
    config,
    decimals: integerOrUndefined(config.scale ?? config.decimalPlaces),
    currency: config.currency || undefined,
    maxLength: integerOrUndefined(config.maxLength),
    displayOrder: Number(definition.displayOrder ?? definition.sortOrder ?? 0) || 0,
    options,
    optionItems: Array.isArray(definition.optionItems) ? definition.optionItems : []
  }
}

function canonicalizeFieldType(fieldType) {
  const normalized = String(fieldType || 'TEXT').trim().toUpperCase().replaceAll('-', '_')
  return normalized
}

function integerOrUndefined(value) {
  const number = Number(value)
  return Number.isInteger(number) && number >= 0 ? number : undefined
}

export function normalizeCustomFieldOptions(optionItems, legacyOptions) {
  if (Array.isArray(optionItems) && optionItems.length > 0) {
    return optionItems
      .filter(option => option?.optionCode || option?.optionLabel)
      .sort((left, right) => Number(left.displayOrder || 0) - Number(right.displayOrder || 0))
      .map(option => ({
        value: String(option.optionCode || option.optionLabel),
        label: String(option.optionLabel || option.optionCode),
        color: option.color || '',
        disabled: option.status === 'DISABLED'
      }))
  }
  return (Array.isArray(legacyOptions) ? legacyOptions : []).map(option => {
    if (option && typeof option === 'object') {
      const value = option.value ?? option.optionCode ?? option.label ?? option.optionLabel ?? ''
      return {
        ...option,
        value,
        label: option.label ?? option.optionLabel ?? value
      }
    }
    return { value: option, label: String(option) }
  })
}
