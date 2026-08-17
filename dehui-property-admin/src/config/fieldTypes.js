const FIELD_TYPE_DEFINITIONS = [
  { value: 'text', label: '文本', backendType: 'text', groupable: true },
  { value: 'long-text', label: '多行文本', backendType: 'longText' },
  { value: 'number', label: '数字', backendType: 'number', numeric: true },
  { value: 'money', label: '金额', backendType: 'currency', numeric: true },
  { value: 'date', label: '日期', backendType: 'date', groupable: true },
  { value: 'datetime', label: '日期时间', backendType: 'datetime', groupable: true },
  { value: 'select', label: '单选', backendType: 'singleSelect', option: true, groupable: true },
  { value: 'status', label: '状态', backendType: null, option: true, groupable: true },
  { value: 'multi-select', label: '多选', backendType: 'multiSelect', option: true, multiple: true },
  { value: 'user', label: '人员', backendType: null, groupable: true },
  { value: 'image', label: '图片', backendType: 'image', multiple: true, file: true },
  { value: 'attachment', label: '附件', backendType: 'attachment', multiple: true, file: true },
  { value: 'formula', label: '公式', backendType: null },
  { value: 'relation', label: '关联记录', backendType: null, relation: true, groupable: true },
  { value: 'checkbox', label: '复选框', backendType: 'checkbox', groupable: true },
  { value: 'phone', label: '电话号码', backendType: 'phone' },
  { value: 'email', label: 'Email', backendType: 'email' },
  { value: 'url', label: '超链接', backendType: 'url' }
]

const FIELD_TYPE_ALIASES = {
  singleSelect: 'select',
  multiSelect: 'multi-select'
}

const FIELD_TYPE_MAP = new Map(FIELD_TYPE_DEFINITIONS.map(item => [item.value, item]))

export function normalizeFieldType(type = 'text') {
  const normalized = FIELD_TYPE_ALIASES[type] || type
  return FIELD_TYPE_MAP.has(normalized) ? normalized : 'text'
}

export function fieldTypeDefinition(type = 'text') {
  return FIELD_TYPE_MAP.get(normalizeFieldType(type)) || FIELD_TYPE_MAP.get('text')
}

export function fieldTypeOptions({ backendPersistableOnly = false } = {}) {
  return FIELD_TYPE_DEFINITIONS
    .filter(item => !backendPersistableOnly || item.backendType)
    .map(item => ({ ...item }))
}

export function backendFieldType(type = 'text') {
  return fieldTypeDefinition(type).backendType
}

export function isOptionFieldType(type) {
  return Boolean(fieldTypeDefinition(type).option)
}

export function isMultiValueFieldType(type) {
  return Boolean(fieldTypeDefinition(type).multiple)
}

export function isGroupableFieldType(type) {
  return Boolean(fieldTypeDefinition(type).groupable)
}

export function isNumericFieldType(type) {
  return Boolean(fieldTypeDefinition(type).numeric)
}

export function isDateFieldType(type) {
  return ['date', 'datetime'].includes(normalizeFieldType(type))
}

export function isRelationFieldType(type) {
  return Boolean(fieldTypeDefinition(type).relation)
}

export function isFileFieldType(type) {
  return Boolean(fieldTypeDefinition(type).file)
}
