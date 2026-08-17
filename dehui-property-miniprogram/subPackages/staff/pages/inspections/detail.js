const api = require('../../../../utils/request')
const {
  requireStaffFeature,
  has,
  uploadInternalImages,
  message
} = require('../../../../utils/staff')
const {
  loadModuleFieldSchema,
  normalizeFields,
  normalizeFileValue,
  SURFACES
} = require('../../../../utils/moduleFieldSchema')

const CORE_FIELDS = new Set([
  'id', 'code', 'inspectionDate', 'inspector', 'inspectionType', 'inspectionTypeName',
  'area', 'location', 'target', 'inspectionObject', 'result', 'inspectionResult',
  'problemDescription', 'actionTaken', 'handlingMeasures', 'inspectionStatus', 'status',
  'remark', 'attachments', 'images', 'createdAt', 'updatedAt'
])

const RESULT_LABELS = Object.freeze({
  NORMAL: '正常',
  ABNORMAL: '异常'
})

const STATUS_LABELS = Object.freeze({
  OPEN: '待处理',
  CLOSED: '已关闭'
})

function text(value, fallback = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return fallback
  return String(value)
}

function dateTime(value) {
  return text(value).replace('T', ' ')
}

function fieldValue(field, detail) {
  const customFields = detail.customFields || {}
  if (field.source === 'CUSTOM') return customFields[field.fieldKey]
  if (detail[field.apiField] !== undefined) return detail[field.apiField]
  return detail[field.fieldKey]
}

function deduplicateFiles(files) {
  const seen = new Set()
  return files.filter(file => {
    const key = Number(file.fileId || file.id) > 0
      ? `id:${Number(file.fileId || file.id)}`
      : `url:${String(file.url || '')}`
    if (key === 'url:' || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function presentation(detail, fieldSchema) {
  const fields = normalizeFields(fieldSchema && fieldSchema.fields)
  const schemaPhotos = []
  const supplementalFields = []

  fields.forEach(field => {
    const value = fieldValue(field, detail)
    if (field.dataType === 'IMAGE') {
      schemaPhotos.push(...normalizeFileValue(value).files)
      return
    }
    if (!CORE_FIELDS.has(field.fieldKey) && !CORE_FIELDS.has(field.apiField)) {
      supplementalFields.push({ field, value })
    }
  })

  const photoFiles = deduplicateFiles([
    ...normalizeFileValue(detail.attachments).files,
    ...normalizeFileValue(detail.images).files,
    ...schemaPhotos
  ])
  const result = text(detail.inspectionResult || detail.result)
  const status = text(detail.status)

  return {
    title: text(detail.inspectionTypeName || detail.inspectionType, '巡检记录'),
    code: text(detail.code || detail.id),
    createdAt: dateTime(detail.createdAt),
    result,
    resultLabel: RESULT_LABELS[result] || result,
    status,
    statusLabel: STATUS_LABELS[status] || status,
    coreRows: [
      {
        key: 'identity',
        left: { label: '巡检编号', value: text(detail.code || detail.id) },
        right: { label: '巡检日期', value: text(detail.inspectionDate) }
      },
      {
        key: 'people',
        left: { label: '巡检人', value: text(detail.inspector) },
        right: { label: '巡检类型', value: text(detail.inspectionTypeName || detail.inspectionType) }
      },
      {
        key: 'place',
        left: { label: '区域/点位', value: text(detail.location || detail.area) },
        right: { label: '巡检对象', value: text(detail.inspectionObject || detail.target) }
      },
      {
        key: 'result',
        left: { label: '巡检结果', value: RESULT_LABELS[result] || result },
        right: { label: '巡检状态', value: STATUS_LABELS[status] || status }
      }
    ],
    narrativeFields: [
      { label: '问题描述', value: text(detail.problemDescription, '无') },
      { label: '处理措施', value: text(detail.handlingMeasures || detail.actionTaken) },
      { label: '备注', value: text(detail.remark), wide: true }
    ],
    photoField: { fieldKey: 'sitePhotos', dataType: 'IMAGE', label: '现场照片' },
    photoValue: { version: 1, files: photoFiles },
    photoCount: photoFiles.length,
    supplementalFields
  }
}

Page({
  data: {
    id: 0,
    detail: null,
    fieldSchema: { fields: [] },
    display: null,
    canUpdate: false,
    feedback: '',
    feedbackLength: 0,
    resolutionFiles: [],
    resolutionPhotoField: {
      fieldKey: 'resolutionImages',
      dataType: 'IMAGE',
      label: '处理后照片'
    },
    uploadingResolutionImages: false,
    submitting: false,
    error: ''
  },

  async onLoad(options) {
    this.setData({ id: Number(options.id || 0) })
    try {
      const profile = await requireStaffFeature('staffInspection')
      this.setData({ canUpdate: has('inspection.staff.update', profile) })
      const fieldSchema = await loadModuleFieldSchema('inspections', SURFACES.detail)
      this.setData({ fieldSchema })
      await this.load(fieldSchema)
    } catch (error) {
      this.setData({ error: message(error) })
    }
  },

  async load(fieldSchema = this.data.fieldSchema) {
    const detail = await api.get(`/mobile/staff/inspections/${this.data.id}`, null, { silent: true })
    detail.history = (detail.history || []).map(item => {
      const resolutionFiles = deduplicateFiles(normalizeFileValue(item.resolutionImages).files)
      return {
        ...item,
        actionLabel: ({ CREATE: '创建巡检', UPDATE: '补充反馈', CLOSE: '关闭巡检' })[item.action] || item.action,
        resolutionImages: { version: 1, files: resolutionFiles },
        resolutionPhotoCount: resolutionFiles.length
      }
    })
    this.setData({ detail, display: presentation(detail, fieldSchema) })
  },

  input(event) {
    const feedback = event.detail.value
    this.setData({ feedback, feedbackLength: String(feedback || '').length })
  },

  async chooseResolutionImages() {
    if (this.data.submitting || this.data.uploadingResolutionImages) return
    const remaining = Math.max(0, 6 - this.data.resolutionFiles.length)
    if (!remaining) return
    this.setData({ uploadingResolutionImages: true })
    try {
      const files = await uploadInternalImages(remaining)
      this.setData({ resolutionFiles: this.data.resolutionFiles.concat(files) })
    } catch (error) {
      wx.showToast({ title: message(error, '处理后照片上传失败'), icon: 'none' })
    } finally {
      this.setData({ uploadingResolutionImages: false })
    }
  },

  removeResolutionImage(event) {
    if (this.data.submitting || this.data.uploadingResolutionImages) return
    const index = Number(event.currentTarget.dataset.index)
    this.setData({
      resolutionFiles: this.data.resolutionFiles.filter((_, current) => current !== index)
    })
  },

  async saveFeedback() {
    const feedback = this.data.feedback.trim()
    if ((!feedback && !this.data.resolutionFiles.length)
        || this.data.submitting
        || this.data.uploadingResolutionImages) return
    this.setData({ submitting: true })
    try {
      await api.patch(`/mobile/staff/inspections/${this.data.id}`, {
        feedback,
        resolutionFileIds: this.data.resolutionFiles.map(file => Number(file.fileId))
      })
      this.setData({ feedback: '', feedbackLength: 0, resolutionFiles: [] })
      await this.load()
      wx.showToast({ title: '反馈已保存', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: message(error), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  async close() {
    if (this.data.submitting || this.data.uploadingResolutionImages) return
    this.setData({ submitting: true })
    try {
      await api.post(`/mobile/staff/inspections/${this.data.id}/close`, {
        feedback: this.data.feedback.trim(),
        resolutionFileIds: this.data.resolutionFiles.map(file => Number(file.fileId))
      })
      this.setData({ feedback: '', feedbackLength: 0, resolutionFiles: [] })
      await this.load()
      wx.showToast({ title: '巡检已关闭', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: message(error), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
