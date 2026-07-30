const { normalizeFields, normalizeFileValue } = require('./moduleFieldSchema')

const ORDER_TYPE_TEXT = Object.freeze({
  REPAIR: '维修报修',
  PATROL: '巡检任务',
  CLEAN: '保洁服务',
  SECURITY: '安保事件'
})

const CATEGORY_TEXT = Object.freeze({
  WATER: '水路',
  ELECTRIC: '电路',
  AIR_CONDITIONER: '空调',
  DOOR_WINDOW: '门窗',
  NETWORK: '网络',
  CLEANING: '保洁',
  OTHER: '其他'
})

const PRIORITY_TEXT = Object.freeze({
  NORMAL: '普通',
  HIGH: '较高',
  URGENT: '紧急'
})

const STATUS_TEXT = Object.freeze({
  CREATED: '待处理',
  SUBMITTED: '待处理',
  ASSIGNED: '已派单',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
  CANCELLED: '已撤回'
})

const ACTION_TEXT = Object.freeze({
  CREATE: '创建工单',
  CREATED: '创建工单',
  ASSIGN: '物业派单',
  ASSIGNED: '物业派单',
  START: '开始处理',
  PROCESSING: '开始处理',
  PROGRESS: '处理反馈',
  COMPLETE: '处理完成',
  COMPLETED: '处理完成',
  CLOSE: '关闭工单',
  CLOSED: '关闭工单',
  CANCEL: '撤回工单',
  CANCELLED: '撤回工单',
  STATUS_CHANGE: '状态更新',
  EVALUATION: '服务评价'
})

const CORE_FIELDS = Object.freeze([
  { key: 'orderNumber', label: '工单编号', aliases: ['orderNumber', 'code'], schemaKeys: ['code'] },
  { key: 'title', label: '工单标题', aliases: ['title'] },
  { key: 'orderType', label: '工单类型', aliases: ['orderType'] },
  { key: 'category', label: '问题类型', aliases: ['category', 'problemCategory'], schemaKeys: ['problemCategory'] },
  { key: 'priority', label: '优先级', aliases: ['priority'] },
  { key: 'status', label: '处理状态', aliases: ['status'] },
  { key: 'location', label: '报修位置', aliases: ['location'] },
  { key: 'description', label: '问题描述', aliases: ['description'] },
  { key: 'reporterName', label: '报修人', aliases: ['reporterName', 'contactName'], schemaKeys: ['contactName'] },
  { key: 'reporterPhone', label: '联系电话', aliases: ['reporterPhone', 'contactPhone'], schemaKeys: ['contactPhone'] },
  { key: 'companyName', label: '公司名称', aliases: ['companyName'] },
  { key: 'reportedAt', label: '报修时间', aliases: ['reportedAt'] },
  { key: 'handlerName', label: '处理人员', aliases: ['handlerName'] },
  { key: 'createdTime', label: '创建时间', aliases: ['createdTime', 'createdAt'] },
  { key: 'updatedTime', label: '更新时间', aliases: ['updatedTime', 'updatedAt'] },
  { key: 'remark', label: '备注', aliases: ['remark'] }
])

function dateTimeText(value) {
  if (value === null || value === undefined || value === '') return '-'
  return String(value).replace('T', ' ')
}

function valueFrom(source, aliases) {
  for (const key of aliases || []) {
    if (source[key] !== undefined && source[key] !== null) return source[key]
  }
  return undefined
}

function optionText(field, value) {
  const option = field && (field.options || []).find(item => String(item.value) === String(value))
  const label = option && option.label ? String(option.label).trim() : ''
  return /[\u4e00-\u9fff]/.test(label) ? label : ''
}

function localizedValue(field, key, value) {
  if (value === null || value === undefined || value === '') return '-'
  if (key === 'orderType') return ORDER_TYPE_TEXT[value] || '其他工单'
  if (key === 'category') return CATEGORY_TEXT[value] || '其他'
  if (key === 'priority') return PRIORITY_TEXT[value] || '普通'
  if (key === 'status') return STATUS_TEXT[value] || '状态更新'
  const dataType = String(field && field.dataType || '').toUpperCase()
  if (dataType === 'MULTISELECT') {
    const values = Array.isArray(value)
      ? value
      : String(value).split(/[,，;；]/).map(item => item.trim()).filter(Boolean)
    return values.map(item => optionText(field, item) || '其他').join('、') || '-'
  }
  if (dataType === 'BOOLEAN') {
    const normalized = String(value).trim().toLowerCase()
    if (value === true || value === 1 || ['true', '1', 'yes'].includes(normalized)) return '是'
    if (value === false || value === 0 || ['false', '0', 'no'].includes(normalized)) return '否'
    return '未填写'
  }
  const configured = optionText(field, value)
  if (configured) return configured
  if (['SELECT', 'RADIO'].includes(dataType)) return '其他'
  if (['DATETIME', 'DATE', 'TIME'].includes(dataType)) return dateTimeText(value)
  if (Array.isArray(value)) return value.map(() => '其他').join('、') || '-'
  if (typeof value === 'object') return '-'
  return dateTimeText(value)
}

function chineseLabel(value, fallback) {
  const label = String(value || '').trim()
  return /[\u4e00-\u9fff]/.test(label) ? label : fallback
}

function schemaFieldFor(fields, definition) {
  const schemaKeys = [definition.key].concat(definition.schemaKeys || [], definition.aliases || [])
  return fields.find(field =>
    schemaKeys.includes(field.fieldKey) || schemaKeys.includes(field.apiField)
  )
}

function normalizeFiles(value) {
  return normalizeFileValue(value).files.map((file, index) => {
    const fileId = Number(file.fileId || file.id)
    return {
      ...file,
      ...(fileId > 0 ? { fileId } : {}),
      name: String(file.name || file.originalName || `附件${index + 1}`),
      url: String(file.url || ''),
      mimeType: String(file.mimeType || ''),
      attachmentType: String(file.attachmentType || ''),
      sortOrder: Number(file.sortOrder) || index
    }
  })
}

function isImageFile(file) {
  const mimeType = String(file && file.mimeType || '').toLowerCase()
  const attachmentType = String(file && file.attachmentType || '').toUpperCase()
  const name = String(file && file.name || '').toLowerCase()
  const url = String(file && file.url || '').toLowerCase()
  return mimeType.startsWith('image/')
    || attachmentType.includes('IMAGE')
    || /\.(png|jpe?g|gif|webp|bmp)(?:$|\?)/i.test(name)
    || /\.(png|jpe?g|gif|webp|bmp)(?:$|\?)/i.test(url)
}

function documentFileType(filename) {
  const matched = String(filename || '').toLowerCase().match(/\.([a-z0-9]+)(?:$|[?#])/)
  const suffix = matched ? matched[1] : ''
  return ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(suffix) ? suffix : ''
}

function dedupeFiles(...values) {
  const seen = new Set()
  const files = []
  values.flatMap(normalizeFiles).forEach(file => {
    const identity = file.fileId ? `id:${file.fileId}` : `url:${file.url}`
    if (!identity || identity === 'url:' || seen.has(identity)) return
    seen.add(identity)
    files.push(file)
  })
  return files
}

function evaluationContent(value) {
  const text = String(value || '').trim()
  const matched = text.match(/^rating=(\d+)\s*;?\s*(.*)$/i)
  if (!matched) return text
  const comment = matched[2].trim()
  return `${matched[1]} 星${comment ? ` · ${comment}` : ''}`
}

function actionContent(record) {
  if (record.content) {
    return record.action === 'EVALUATION'
      ? evaluationContent(record.content)
      : String(record.content)
  }
  return ({
    ASSIGN: '物业已完成派单',
    ASSIGNED: '物业已完成派单',
    START: '维修人员已开始处理',
    PROCESSING: '维修人员已开始处理',
    COMPLETE: '本次维修处理已完成',
    COMPLETED: '本次维修处理已完成',
    CLOSE: '工单已关闭',
    CLOSED: '工单已关闭',
    CANCEL: '报修已撤回',
    CANCELLED: '报修已撤回'
  })[record.action] || ''
}

function presentRecords(records) {
  return (Array.isArray(records) ? records : []).map(record => {
    const attachments = dedupeFiles(record.attachments)
    return {
      ...record,
      actionText: ACTION_TEXT[record.action] || '处理记录',
      contentText: actionContent(record),
      operatorText: String(record.operatorName || '系统'),
      createdTime: String(record.createdTime || ''),
      createdTimeText: dateTimeText(record.createdTime),
      imageGroupLabel: '处理记录照片',
      images: attachments.filter(isImageFile),
      files: attachments.filter(file => !isImageFile(file))
    }
  }).sort((left, right) => {
    const byTime = String(right.createdTime).localeCompare(String(left.createdTime))
    return byTime || Number(right.id || 0) - Number(left.id || 0)
  })
}

function presentCustomFields(fields, customFields) {
  const rows = []
  const imageGroups = []
  const fileGroups = []
  fields.filter(field => field.source === 'CUSTOM').forEach((field, index) => {
    const label = chineseLabel(field.label, `补充信息${index + 1}`)
    const value = customFields && customFields[field.fieldKey]
    if (field.dataType === 'IMAGE') {
      const files = dedupeFiles(value)
      if (files.length) imageGroups.push({ key: field.fieldKey, label, files })
      return
    }
    if (field.dataType === 'ATTACHMENT') {
      const files = dedupeFiles(value)
      if (files.length) fileGroups.push({ key: field.fieldKey, label, files })
      return
    }
    rows.push({
      key: `custom:${field.fieldKey}`,
      label,
      value: localizedValue(field, field.fieldKey, value)
    })
  })
  return { rows, imageGroups, fileGroups }
}

function presentWorkOrderDetail(source = {}, schema = {}) {
  const fields = normalizeFields(schema && schema.fields || schema)
  const rows = CORE_FIELDS.map(definition => {
    const field = schemaFieldFor(fields, definition)
    const rawValue = valueFrom(source, definition.aliases)
    return {
      key: definition.key,
      label: chineseLabel(field && field.label, definition.label),
      value: localizedValue(field, definition.key, rawValue)
    }
  })

  const custom = presentCustomFields(fields, source.customFields || {})
  const attachments = dedupeFiles(source.attachments)
  const workorderImages = dedupeFiles(source.images, attachments.filter(isImageFile))
  const workorderFiles = attachments.filter(file => !isImageFile(file))
  const workorderRecords = presentRecords(source.records)
  const status = String(source.status || '')

  return {
    workorder: {
      id: Number(source.id || 0),
      orderNumber: String(source.orderNumber || source.code || ''),
      title: String(source.title || '维修工单'),
      status,
      statusText: STATUS_TEXT[status] || '状态更新',
      statusTone: ({
        CREATED: 'pending',
        SUBMITTED: 'pending',
        ASSIGNED: 'assigned',
        PROCESSING: 'processing',
        COMPLETED: 'completed',
        CLOSED: 'closed',
        CANCELLED: 'cancelled'
      })[status] || 'neutral',
      categoryText: CATEGORY_TEXT[source.category || source.problemCategory] || '其他',
      priorityText: PRIORITY_TEXT[source.priority] || '普通'
    },
    rows: rows.concat(custom.rows),
    coreRows: rows,
    customRows: custom.rows,
    workorderImages,
    workorderImageLabel: '报修现场照片',
    workorderFiles,
    workorderRecords,
    customImageGroups: custom.imageGroups,
    customFileGroups: custom.fileGroups
  }
}

module.exports = {
  presentWorkOrderDetail,
  isImageFile,
  documentFileType,
  dateTimeText,
  STATUS_TEXT,
  ACTION_TEXT
}
