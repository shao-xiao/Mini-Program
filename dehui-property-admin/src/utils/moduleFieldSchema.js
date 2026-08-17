const TYPE_ALIASES = Object.freeze({
  LONG_TEXT: 'TEXTAREA',
  SINGLE_SELECT: 'SELECT',
  MULTI_SELECT: 'MULTISELECT',
  CHECKBOX: 'BOOLEAN',
  MONEY: 'NUMBER',
  CURRENCY: 'NUMBER',
  PHONE: 'TEXT'
})

export function normalizeModuleSchema(payload = {}) {
  const source = Array.isArray(payload) ? payload : payload.fields
  return (Array.isArray(source) ? source : [])
    .map((field, index) => {
      const fieldKey = String(field.fieldKey || field.apiField || field.key || '').trim()
      const type = String(field.dataType || field.fieldType || field.type || 'TEXT').toUpperCase()
      return {
        ...field,
        fieldKey,
        apiField: field.apiField || fieldKey,
        dataType: TYPE_ALIASES[type] || type,
        source: field.source || (field.custom ? 'CUSTOM' : 'SYSTEM'),
        editable: field.editable !== false && field.readonly !== true,
        order: Number(field.order ?? field.displayOrder ?? index),
        options: normalizeOptions(field.options || field.optionItems),
        validation: field.validation || field.config || {}
      }
    })
    .filter(field => field.fieldKey)
    .sort((a, b) => a.order - b.order)
}

export function splitModuleValues(fields, values = {}) {
  const system = {}
  const customFields = {}
  for (const field of fields) {
    const value = values[field.apiField] ?? values[field.fieldKey]
      ?? values.customFields?.[field.fieldKey]
    if (field.source === 'CUSTOM') customFields[field.fieldKey] = value
    else system[field.apiField] = value
  }
  return { ...system, customFields }
}

export function validateModuleValues(fields, values = {}) {
  const errors = {}
  for (const field of fields) {
    if (!field.editable) continue
    const value = values[field.fieldKey]
    const empty = value == null || value === '' || (Array.isArray(value) && value.length === 0)
    if (field.required && empty) errors[field.fieldKey] = `${field.label}不能为空`
    const maxLength = Number(field.validation?.maxLength)
    if (!empty && maxLength > 0 && String(value).length > maxLength) {
      errors[field.fieldKey] = `${field.label}不能超过${maxLength}个字符`
    }
  }
  return { valid: Object.keys(errors).length === 0, errors }
}

function normalizeOptions(options) {
  return (Array.isArray(options) ? options : []).map(option => {
    if (option && typeof option === 'object') {
      const value = option.code ?? option.optionCode ?? option.value ?? option.label
      return { value, label: option.label ?? option.optionLabel ?? String(value) }
    }
    return { value: option, label: String(option) }
  })
}
