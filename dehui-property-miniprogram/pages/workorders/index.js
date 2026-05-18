const api = require('../../utils/request')
const { baseURL } = require('../../config/env')

function toDateTimeText(value) {
  if (!value) return '-'
  const [date, time = ''] = value.replace('T', ' ').split(' ')
  const [year, month, day] = date.split('-')
  return `${year}年${month}月${day}日 ${time.slice(0, 5)}`
}

function initialForm() {
  return {
    title: '',
    location: '',
    category: 'WATER',
    priority: 'NORMAL',
    contactPhone: '',
    description: ''
  }
}

function imageUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return `${baseURL}${url}`
}

const MAX_IMAGE_COUNT = 6

function compressImage(filePath) {
  if (!wx.compressImage) {
    return Promise.resolve(filePath)
  }
  return new Promise((resolve) => {
    wx.compressImage({
      src: filePath,
      quality: 75,
      success: (res) => resolve(res.tempFilePath || filePath),
      fail: () => resolve(filePath)
    })
  })
}

Page({
  data: {
    loading: false,
    submitting: false,
    errorMessage: '',
    identityText: '',
    form: initialForm(),
    selectedImages: [],
    maxImageCount: MAX_IMAGE_COUNT,
    imageTip: `最多上传${MAX_IMAGE_COUNT}张，系统会压缩后提交`,
    evaluationVisible: false,
    evaluationForm: {
      workOrderId: null,
      title: '',
      rating: 5,
      content: ''
    },
    ratingOptions: [1, 2, 3, 4, 5],
    categories: [
      { label: '水路', value: 'WATER' },
      { label: '电路', value: 'ELECTRIC' },
      { label: '空调', value: 'AIR_CONDITIONER' },
      { label: '门窗', value: 'DOOR_WINDOW' },
      { label: '网络', value: 'NETWORK' },
      { label: '保洁', value: 'CLEANING' },
      { label: '其他', value: 'OTHER' }
    ],
    priorities: [
      { label: '普通', value: 'NORMAL' },
      { label: '较高', value: 'HIGH' },
      { label: '紧急', value: 'URGENT' }
    ],
    workOrders: []
  },

  onShow() {
    this.loadWorkOrders()
  },

  async loadWorkOrders() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const data = await api.get('/mobile/workorders')
      const workOrders = (data.workOrders || []).map(item => ({
        ...item,
        createdTimeText: toDateTimeText(item.createdTime),
        statusClass: this.toStatusClass(item.status),
        cancellable: item.status === 'CREATED',
        evaluable: (item.status === 'COMPLETED' || item.status === 'CLOSED') && !item.rating,
        ratingStars: this.ratingStars(item.rating),
        timeline: this.buildTimeline(item),
        imageUrls: (item.imageUrls || []).map(imageUrl)
      }))
      this.setData({
        identityText: this.toIdentityText(data.profile),
        workOrders
      })
    } catch (error) {
      this.setData({
        errorMessage: error && error.message ? error.message : '请先登录后提交报修',
        workOrders: []
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`form.${field}`]: event.detail.value
    })
  },

  selectCategory(event) {
    this.setData({
      'form.category': event.currentTarget.dataset.value
    })
  },

  selectPriority(event) {
    this.setData({
      'form.priority': event.currentTarget.dataset.value
    })
  },

  chooseImages() {
    const remaining = MAX_IMAGE_COUNT - this.data.selectedImages.length
    if (remaining <= 0) {
      wx.showToast({ title: `最多上传${MAX_IMAGE_COUNT}张照片`, icon: 'none' })
      return
    }
    wx.chooseImage({
      count: remaining,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async (res) => {
        wx.showLoading({ title: '压缩图片中', mask: true })
        const compressedImages = await Promise.all(res.tempFilePaths.map(compressImage))
        wx.hideLoading()
        const nextImages = this.data.selectedImages.concat(compressedImages).slice(0, MAX_IMAGE_COUNT)
        this.setData({
          selectedImages: nextImages
        })
        if (res.tempFilePaths.length > remaining) {
          wx.showToast({ title: `最多保留${MAX_IMAGE_COUNT}张照片`, icon: 'none' })
        }
      }
    })
  },

  removeSelectedImage(event) {
    const index = event.currentTarget.dataset.index
    const selectedImages = this.data.selectedImages.filter((_, itemIndex) => itemIndex !== index)
    this.setData({ selectedImages })
  },

  previewSelectedImage(event) {
    const current = event.currentTarget.dataset.url
    wx.previewImage({
      current,
      urls: this.data.selectedImages
    })
  },

  previewWorkOrderImage(event) {
    const current = event.currentTarget.dataset.url
    const urls = event.currentTarget.dataset.urls || []
    wx.previewImage({ current, urls })
  },

  async submitWorkOrder() {
    const form = this.data.form
    if (!form.title.trim()) {
      wx.showToast({ title: '请填写报修标题', icon: 'none' })
      return
    }
    if (!form.location.trim()) {
      wx.showToast({ title: '请填写报修位置', icon: 'none' })
      return
    }
    if (!form.description.trim()) {
      wx.showToast({ title: '请描述问题', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      const created = await api.post('/mobile/workorders', form)
      for (const imagePath of this.data.selectedImages) {
        await api.upload(`/mobile/workorders/${created.id}/images`, imagePath)
      }
      wx.showModal({
        title: '报修已提交',
        content: `工单号：${created.orderNumber}`,
        showCancel: false
      })
      this.setData({ form: initialForm(), selectedImages: [] })
      this.loadWorkOrders()
    } finally {
      this.setData({ submitting: false })
    }
  },

  cancelWorkOrder(event) {
    const id = event.currentTarget.dataset.id
    const workOrder = this.data.workOrders.find(item => item.id === id)
    wx.showModal({
      title: '撤回报修',
      content: workOrder ? `确定撤回“${workOrder.title}”吗？` : '确定撤回该报修吗？',
      confirmText: '撤回',
      confirmColor: '#d93025',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.patch(`/mobile/workorders/${id}/cancel`)
          wx.showToast({ title: '已撤回', icon: 'success' })
          this.loadWorkOrders()
        } catch (error) {
          // request.js already shows the backend message.
        }
      }
    })
  },

  openEvaluation(event) {
    const id = event.currentTarget.dataset.id
    const workOrder = this.data.workOrders.find(item => item.id === id)
    this.setData({
      evaluationVisible: true,
      evaluationForm: {
        workOrderId: id,
        title: workOrder ? workOrder.title : '',
        rating: 5,
        content: ''
      }
    })
  },

  closeEvaluation() {
    this.setData({ evaluationVisible: false })
  },

  noop() {},

  selectRating(event) {
    this.setData({
      'evaluationForm.rating': Number(event.currentTarget.dataset.value)
    })
  },

  onEvaluationInput(event) {
    this.setData({
      'evaluationForm.content': event.detail.value
    })
  },

  async submitEvaluation() {
    const form = this.data.evaluationForm
    if (!form.workOrderId) return
    try {
      await api.patch(`/mobile/workorders/${form.workOrderId}/evaluation`, {
        rating: form.rating,
        content: form.content
      })
      wx.showToast({ title: '评价成功', icon: 'success' })
      this.setData({ evaluationVisible: false })
      this.loadWorkOrders()
    } catch (error) {
      // request.js already shows the backend message.
    }
  },

  goProfile() {
    wx.navigateTo({
      url: '/pages/me/index'
    })
  },

  toIdentityText(profile) {
    if (!profile) return ''
    if (profile.userType === 'INTERNAL') return `内部员工：${profile.boundSysRealName || profile.boundSysUsername || profile.nickname || ''}`
    if (profile.userType === 'TENANT') return `租户：${profile.boundTenantName || profile.nickname || ''}`
    return `访客：${profile.nickname || ''}`
  },

  toStatusClass(status) {
    if (status === 'CANCELLED') return 'cancelled'
    if (status === 'COMPLETED' || status === 'CLOSED') return 'done'
    if (status === 'PROCESSING' || status === 'ASSIGNED') return 'processing'
    return 'created'
  },

  buildTimeline(item) {
    const steps = [
      { label: '已提交', time: item.submittedTime || item.createdTime, active: true },
      { label: '已派单', time: item.assignedTime, active: Boolean(item.assignedTime) },
      { label: '处理中', time: item.processingTime, active: Boolean(item.processingTime) },
      { label: '已完成', time: item.completedTime, active: Boolean(item.completedTime) },
      { label: '已关闭', time: item.closedTime, active: Boolean(item.closedTime) }
    ]
    if (item.cancelledTime || item.status === 'CANCELLED') {
      return [
        steps[0],
        { label: '已撤回', time: item.cancelledTime || item.updatedTime, active: true }
      ].map(step => ({
        ...step,
        timeText: toDateTimeText(step.time)
      }))
    }
    return steps.map(step => ({
      ...step,
      timeText: step.time ? toDateTimeText(step.time) : ''
    }))
  },

  ratingStars(rating) {
    const score = Number(rating || 0)
    return [1, 2, 3, 4, 5].map(value => ({
      value,
      active: value <= score
    }))
  }
})
