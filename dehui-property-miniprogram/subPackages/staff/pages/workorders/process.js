const api = require('../../../../utils/request')
const {
  requireStaffFeature,
  has,
  loadSchema,
  uploadInternalImages,
  customFileValue,
  imageFieldLimit,
  previewProtected,
  message
} = require('../../../../utils/staff')

Page({
  data: { id: 0, content: '', files: [], customFields: {}, customFiles: {}, schemaFields: [], fieldSchema: { fields: [] }, canComplete: false, complete: false, submitting: false },
  async onLoad(options) {
    this.setData({ id: Number(options.id || 0) })
    try {
      const profile = await requireStaffFeature('staffWorkorder')
      const schema = await loadSchema('workorders')
      const schemaFields = ((schema && schema.fields) || []).filter(field => field.source === 'CUSTOM' && !field.readonly)
      this.setData({
        canComplete: has('workorder.staff.complete', profile),
        schemaFields,
        fieldSchema: { ...(schema || {}), fields: schemaFields }
      })
    } catch (error) {
      wx.showToast({ title: message(error), icon: 'none' })
    }
  },
  input(event) { this.setData({ content: event.detail.value }) },
  customInput(event) { this.setData({ [`customFields.${event.currentTarget.dataset.key}`]: event.detail.value }) },
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
  toggleComplete(event) { this.setData({ complete: event.detail.value.length > 0 }) },
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
  async submit() {
    if (this.data.submitting || !this.data.content.trim()) return
    const schemaForm = this.selectComponent('#schemaForm')
    const validation = schemaForm && schemaForm.validate()
    if (validation && !validation.valid) return wx.showToast({ title: '请完善处理字段', icon: 'none' })
    this.setData({ submitting: true })
    try {
      await api.post(`/mobile/staff/workorders/${this.data.id}/records`, {
        content: this.data.content,
        attachmentFileIds: this.data.files.map(file => file.fileId),
        customFields: schemaForm ? schemaForm.getPayload().customFields : this.data.customFields
      })
      if (this.data.complete) {
        await api.post(`/mobile/workorders/${this.data.id}/complete`, { result: this.data.content })
      }
      wx.showToast({ title: this.data.complete ? '工单已完成' : '处理记录已保存', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 300)
    } catch (error) {
      wx.showToast({ title: message(error, '保存失败'), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
  onSchemaChange(event) {
    this.setData({ customFields: (event.detail && event.detail.payload && event.detail.payload.customFields) || {} })
  }
})
