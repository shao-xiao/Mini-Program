const api = require('../../../../utils/request')
const {
  requireStaffFeature,
  has,
  uploadInternalImages,
  previewProtected,
  message
} = require('../../../../utils/staff')
const { loadModuleFieldSchema, SURFACES } = require('../../../../utils/moduleFieldSchema')

Page({
  data: {
    id: 0,
    detail: null,
    fieldSchema: { fields: [] },
    canStart: false,
    canComplete: false,
    feedback: '',
    feedbackLength: 0,
    feedbackFiles: [],
    submitting: false,
    error: ''
  },

  async onLoad(options) {
    this.setData({ id: Number(options.id || 0) })
    try {
      const profile = await requireStaffFeature('staffWorkorder')
      this.setData({
        canStart: has('workorder.staff.start', profile),
        canComplete: has('workorder.staff.complete', profile)
      })
      this.setData({ fieldSchema: await loadModuleFieldSchema('workorders', SURFACES.detail) })
      await this.load()
    } catch (error) {
      this.setData({ error: message(error) })
    }
  },

  onShow() { if (this._loaded) this.load() },

  async load() {
    try {
      const detail = await api.get(`/mobile/workorders/${this.data.id}`, null, { silent: true })
      detail.records = (detail.records || []).map(item => ({
        ...item,
        actionLabel: ({
          CREATE: '创建工单',
          ASSIGN: '派单',
          START: '开始处理',
          PROGRESS: '处理反馈',
          COMPLETE: '完成工单',
          CLOSE: '关闭工单'
        })[item.action] || item.action
      }))
      this.setData({ detail, error: '' })
      this._loaded = true
    } catch (error) {
      this.setData({ error: message(error) })
    }
  },

  async start() {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    try {
      await api.post(`/mobile/workorders/${this.data.id}/start`, {})
      await this.load()
      wx.showToast({ title: '已开始处理', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: message(error), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  inputFeedback(event) {
    const feedback = event.detail.value
    this.setData({ feedback, feedbackLength: String(feedback || '').length })
  },

  async chooseFeedbackImages() {
    if (this.data.submitting || this.data.feedbackFiles.length >= 6) return
    this.setData({ submitting: true })
    try {
      const files = await uploadInternalImages(6 - this.data.feedbackFiles.length)
      this.setData({ feedbackFiles: this.data.feedbackFiles.concat(files) })
    } catch (error) {
      wx.showToast({ title: message(error, '图片上传失败'), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  removeFeedbackImage(event) {
    const index = Number(event.currentTarget.dataset.index)
    this.setData({
      feedbackFiles: this.data.feedbackFiles.filter((_, current) => current !== index)
    })
  },

  previewFeedbackImage(event) {
    return previewProtected(this.data.feedbackFiles, event.currentTarget.dataset.url)
  },

  previewRecordImage(event) {
    const record = (this.data.detail.records || []).find(item => Number(item.id) === Number(event.currentTarget.dataset.recordId))
    return previewProtected((record && record.attachments) || [], event.currentTarget.dataset.url)
  },

  async saveFeedback() {
    const content = this.data.feedback.trim()
    if (!content || this.data.submitting) return
    this.setData({ submitting: true })
    try {
      await api.post(`/mobile/staff/workorders/${this.data.id}/records`, {
        content,
        attachmentFileIds: this.data.feedbackFiles.map(file => file.fileId)
      })
      this.setData({ feedback: '', feedbackLength: 0, feedbackFiles: [] })
      await this.load()
      wx.showToast({ title: '处理反馈已保存', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: message(error, '反馈保存失败'), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  process() { wx.navigateTo({ url: `/subPackages/staff/pages/workorders/process?id=${this.data.id}` }) },

  preview(event) {
    previewProtected(this.data.detail.attachments || [], event.currentTarget.dataset.url)
  }
})
