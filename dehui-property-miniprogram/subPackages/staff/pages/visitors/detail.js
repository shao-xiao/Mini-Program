const api = require('../../../../utils/request')
const {
  requireStaffFeature,
  has,
  message,
  uploadInternalImages,
  previewProtected
} = require('../../../../utils/staff')
const { loadModuleFieldSchema, SURFACES } = require('../../../../utils/moduleFieldSchema')

const STATUS_TEXT = Object.freeze({
  PENDING: '待审核',
  REGISTERED: '待核验',
  ENTERED: '已到访',
  LEFT: '已离场',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  EXPIRED: '已过期'
})

function confirmAction(content, confirmText = '确认') {
  return new Promise(resolve => {
    wx.showModal({
      title: '操作确认',
      content,
      confirmText,
      confirmColor: '#e4312b',
      success: result => resolve(Boolean(result.confirm)),
      fail: () => resolve(false)
    })
  })
}

Page({
  data: {
    id: 0,
    detail: null,
    fieldSchema: { fields: [] },
    canVerify: false,
    reviewRemark: '',
    submitting: false,
    error: ''
  },
  async onLoad(options) {
    this.setData({ id: Number(options.id || 0) })
    try {
      const profile = await requireStaffFeature('staffVisitor')
      this.setData({ canVerify: has('visitor.staff.verify', profile) })
      this.setData({ fieldSchema: await loadModuleFieldSchema('visitors', SURFACES.detail) })
      await this.load()
    } catch (error) {
      this.setData({ error: message(error) })
    }
  },
  async load() {
    const detail = await api.get(`/mobile/staff/visitors/${this.data.id}`, null, { silent: true })
    let qrLocalPath = ''
    if (detail.qrCodeUrl) {
      try {
        qrLocalPath = await api.download(
          detail.qrCodeUrl,
          detail.qrCodeFileName || `visitor-qr-${detail.id}.png`,
          { silent: true }
        )
      } catch (error) {
        qrLocalPath = ''
      }
    }
    this.setData({
      detail: {
        ...detail,
        qrLocalPath,
        statusText: STATUS_TEXT[String(detail.status || '')] || detail.status || '未知'
      },
      reviewRemark: detail.status === 'PENDING' ? this.data.reviewRemark : '',
      error: ''
    })
  },
  onRemarkInput(event) {
    this.setData({ reviewRemark: String(event.detail.value || '') })
  },
  async approve() {
    if (this.data.submitting || !this.data.canVerify) return
    if (!await confirmAction('确认审核通过该访客预约？', '通过')) return
    await this.submitReview('approve')
  },
  async reject() {
    if (this.data.submitting || !this.data.canVerify) return
    const reviewRemark = this.data.reviewRemark.trim()
    if (!reviewRemark) {
      wx.showToast({ title: '请填写驳回原因', icon: 'none' })
      return
    }
    if (!await confirmAction('确认驳回该访客预约？', '驳回')) return
    await this.submitReview('reject')
  },
  async submitReview(action) {
    this.setData({ submitting: true })
    try {
      await api.post(`/mobile/staff/visitors/${this.data.id}/${action}`, {
        reviewRemark: this.data.reviewRemark.trim()
      })
      wx.showToast({ title: action === 'approve' ? '审核已通过' : '预约已驳回', icon: 'success' })
      await this.load()
    } catch (error) {
      wx.showToast({ title: message(error), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
  async uploadQr() {
    if (this.data.submitting || !this.data.canVerify) return
    this.setData({ submitting: true })
    try {
      const files = await uploadInternalImages(1)
      if (!files.length) return
      await api.post(`/mobile/staff/visitors/${this.data.id}/qr-code`, {
        fileId: Number(files[0].fileId)
      })
      wx.showToast({ title: '二维码已保存', icon: 'success' })
      await this.load()
    } catch (error) {
      wx.showToast({ title: message(error), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
  async previewQr() {
    try {
      if (this.data.detail.qrLocalPath) {
        wx.previewImage({
          urls: [this.data.detail.qrLocalPath],
          current: this.data.detail.qrLocalPath
        })
        return
      }
      await previewProtected([this.data.detail.qrCodeUrl], this.data.detail.qrCodeUrl)
    } catch (error) {
      wx.showToast({ title: message(error, '二维码加载失败'), icon: 'none' })
    }
  },
  async action(event) {
    if (this.data.submitting) return
    const action = event.currentTarget.dataset.action
    const prompt = action === 'enter' ? '确认该访客已到访？' : '确认登记该访客离场？'
    if (!await confirmAction(prompt)) return
    this.setData({ submitting: true })
    try {
      await api.post(`/mobile/staff/visitors/${this.data.id}/${action}`, {})
      wx.showToast({ title: action === 'enter' ? '已核验到访' : '已登记离场', icon: 'success' })
      await this.load()
    } catch (error) {
      wx.showToast({ title: message(error), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
