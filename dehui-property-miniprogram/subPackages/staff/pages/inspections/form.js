const api = require('../../../../utils/request')
const { normalizeFileValue } = require('../../../../utils/moduleFieldSchema')
const {
  requireStaffFeature,
  loadSchema,
  uploadInternalImages,
  customFileValue,
  imageFieldLimit,
  previewProtected,
  formValue,
  message
} = require('../../../../utils/staff')

Page({
  data: {
    schemaFields: [],
    fieldSchema: { fields: [] },
    files: [],
    customFiles: {},
    submitting: false,
    form: {
      inspectionDate: today(),
      inspector: '',
      inspectionType: '',
      location: '',
      area: '',
      inspectionObject: '',
      target: '',
      inspectionResult: 'NORMAL',
      result: 'NORMAL',
      problemDescription: '',
      handlingMeasures: '',
      actionTaken: '',
      remark: ''
    },
    customFields: {}
  },

  async onLoad() {
    try {
      await requireStaffFeature('staffInspection')
      const [options, types, schema] = await Promise.all([
        api.get('/mobile/staff/inspection-options', null, { silent: true }),
        api.get('/mobile/staff/inspection-types', null, { silent: true }),
        loadSchema('inspections')
      ])
      const areaOptions = normalizeOptions(options && options.areas)
      const typeOptions = normalizeOptions(types)
      const fields = ((schema && schema.fields) || []).map(field => {
        if (field.fieldKey === 'area') return { ...field, dataType: 'SELECT', options: areaOptions }
        if (field.fieldKey === 'inspectionType') return { ...field, dataType: 'SELECT', options: typeOptions }
        return field
      })
      this.setData({
        fieldSchema: { ...(schema || {}), fields },
        schemaFields: fields.filter(field => field.source === 'CUSTOM' && !field.readonly)
      })
    } catch (error) {
      wx.showToast({ title: message(error), icon: 'none' })
    }
  },

  input(event) {
    this.setData({ [`form.${event.currentTarget.dataset.key}`]: formValue(event) })
  },

  result(event) {
    this.setData({ 'form.inspectionResult': event.detail.value })
  },

  customInput(event) {
    this.setData({ [`customFields.${event.currentTarget.dataset.key}`]: formValue(event) })
  },

  async chooseCustomImages(event) {
    if (this.data.submitting) return
    const key = event.currentTarget.dataset.key
    const field = this.data.schemaFields.find(item => item.fieldKey === key)
    const current = this.data.customFiles[key] || []
    const remaining = Math.max(0, imageFieldLimit(field) - current.length)
    if (!remaining) return
    this.setData({ submitting: true })
    try {
      const next = current.concat(await uploadInternalImages(remaining))
      this.setData({
        [`customFiles.${key}`]: next,
        [`customFields.${key}`]: customFileValue(next)
      })
    } catch (error) {
      wx.showToast({ title: message(error, '图片上传失败'), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  removeCustomImage(event) {
    const { key } = event.currentTarget.dataset
    const index = Number(event.currentTarget.dataset.index)
    const next = (this.data.customFiles[key] || []).filter((_, current) => current !== index)
    this.setData({
      [`customFiles.${key}`]: next,
      [`customFields.${key}`]: customFileValue(next)
    })
  },

  previewCustomImage(event) {
    const { key, url } = event.currentTarget.dataset
    return previewProtected((this.data.customFiles[key] || []).map(file => file.url), url)
  },

  async chooseImages() {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    try {
      const files = await uploadInternalImages(Math.max(0, 6 - this.data.files.length))
      this.setData({ files: this.data.files.concat(files) })
    } catch (error) {
      wx.showToast({ title: message(error, '图片上传失败'), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  removeImage(event) {
    const index = Number(event.currentTarget.dataset.index)
    this.setData({ files: this.data.files.filter((_, current) => current !== index) })
  },

  async submit() {
    if (this.data.submitting) return
    const schemaForm = this.selectComponent('#schemaForm')
    const validation = schemaForm && schemaForm.validate()
    if (validation && !validation.valid) {
      wx.showToast({ title: validation.uploading ? '文件上传中，请稍候' : '请完善巡检信息', icon: 'none' })
      return
    }
    const form = this.data.form
    if (!form.inspector || !form.inspectionType || !form.area || !form.target) {
      wx.showToast({ title: '请填写巡检人、巡检类型和巡检对象，并选择区域', icon: 'none' })
      return
    }
    const inspectionResult = form.result || form.inspectionResult
    if (inspectionResult === 'ABNORMAL' && !String(form.problemDescription || '').trim()) {
      wx.showToast({ title: '异常巡检必须填写问题描述', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      const payload = schemaForm ? schemaForm.getPayload() : form
      const attachmentFileIds = Array.from(new Set([
        ...this.data.files.map(file => Number(file.fileId)),
        ...normalizeFileValue(payload.images).files.map(file => Number(file.fileId))
      ].filter(fileId => fileId > 0)))
      await api.post('/mobile/staff/inspections', {
        ...payload,
        attachmentFileIds
      })
      wx.showToast({ title: '巡检已提交', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 300)
    } catch (error) {
      wx.showToast({ title: message(error, '提交失败'), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
  ,
  onSchemaChange(event) {
    this.setData({ form: { ...this.data.form, ...((event.detail && event.detail.values) || {}) } })
  }
})

function today() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function normalizeOptions(items) {
  return (Array.isArray(items) ? items : []).map(item => ({
    value: String(item && (item.value ?? item.code) || ''),
    label: String(item && (item.label ?? item.name ?? item.value ?? item.code) || '')
  })).filter(item => item.value && item.label)
}
