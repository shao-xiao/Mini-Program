const api = require('../../utils/request')
const { getBaseURL } = require('../../config/env')
const { formatDateTime } = require('../../utils/format')

function imageUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return `${getBaseURL()}${url}`
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

Page({
  data: {
    loading: false,
    submitting: false,
    errorMessage: '',
    identityText: '',
    form: initialForm(),
    selectedImages: [],
    maxImageCount: MAX_IMAGE_COUNT,
    imageTip: `最多可上传${MAX_IMAGE_COUNT}张，系统将自动压缩后上传`,
    evaluationVisible: false,
    evaluationForm: {
      workOrderId: null,
      title: '',
      rating: 5,
      content: ''
    },
    ratingOptions: [1, 2, 3, 4, 5],
    categories: [
      { label: '水类', value: 'WATER' },
      { label: '电力', value: 'ELECTRIC' },
      { label: '空调', value: 'AIR_CONDITIONER' },
      { label: '门窗', value: 'DOOR_WINDOW' },
      { label: '网络', value: 'NETWORK' },
      { label: '清洁', value: 'CLEANING' },
      { label: '其他', value: 'OTHER' }
    ],
    priorities: [
      { label: '普通', value: 'NORMAL' },
      { label: '高', value: 'HIGH' },
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
        createdTimeText: formatDateTime(item.createdTime),
        categoryText: this.toCategoryText(item.category),
        priorityText: this.toPriorityText(item.priority),
        statusText: this.toStatusText(item.status),
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
        errorMessage: error && error.message ? error.message : '请先登录后查看工单列表',
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
      wx.showToast({ title: `最多可添加${MAX_IMAGE_COUNT}张`, icon: 'none' })
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
        this.setData({ selectedImages: nextImages })
        if (res.tempFilePaths.length > remaining) {
          wx.showToast({ title: `已超出${MAX_IMAGE_COUNT}张上限`, icon: 'none' })
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
    wx.previewImage({ current, urls: this.data.selectedImages })
  },

  previewWorkOrderImage(event) {
    const current = event.currentTarget.dataset.url
    const urls = event.currentTarget.dataset.urls || []
    wx.previewImage({ current, urls })
  },

  async submitWorkOrder() {
    const form = this.data.form
    if (!form.title.trim()) {
      wx.showToast({ title: '请输入工单标题', icon: 'none' })
      return
    }
    if (!form.location.trim()) {
      wx.showToast({ title: '请输入报修位置', icon: 'none' })
      return
    }
    if (!form.description.trim()) {
      wx.showToast({ title: '请输入报修描述', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      const created = await api.post('/mobile/workorders', form)
      for (const imagePath of this.data.selectedImages) {
        await api.upload(`/mobile/workorders/${created.id}/images`, imagePath)
      }
      wx.showModal({
        title: '提交成功',
        content: `工单编号：${created.orderNumber}`,
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
      title: '取消工单',
      content: workOrder ? `确认取消 ${workOrder.title} 的工单？` : '确认取消该工单？',
      confirmText: '取消工单',
      confirmColor: '#d93025',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.post(`/mobile/workorders/${id}/cancel`)
          wx.showToast({ title: '已取消', icon: 'success' })
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
      await api.post(`/mobile/workorders/${form.workOrderId}/evaluation`, {
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
    wx.navigateTo({ url: '/pages/me/index' })
  },

  toIdentityText(profile) {
    if (!profile) return ''
    if (profile.userType === 'INTERNAL') return `内部人员：${profile.boundSysRealName || profile.boundSysUsername || profile.nickname || ''}`
    if (profile.userType === 'TENANT') return `租户：${profile.boundTenantName || profile.nickname || ''}`
    return `游客：${profile.nickname || ''}`
  },

  toStatusText(status) {
    if (status === 'CREATED') return '已创建'
    if (status === 'ASSIGNED') return '已分配'
    if (status === 'PROCESSING') return '处理中'
    if (status === 'COMPLETED') return '已完成'
    if (status === 'CLOSED') return '已关闭'
    if (status === 'CANCELLED') return '已取消'
    return status || '未知'
  },

  toCategoryText(category) {
    const map = {
      WATER: '水类',
      ELECTRIC: '电力',
      AIR_CONDITIONER: '空调',
      DOOR_WINDOW: '门窗',
      NETWORK: '网络',
      CLEANING: '清洁',
      OTHER: '其他'
    }
    return map[category] || (category || '未设置')
  },

  toPriorityText(priority) {
    const map = {
      NORMAL: '普通',
      HIGH: '高',
      URGENT: '紧急'
    }
    return map[priority] || (priority || '普通')
  },

  toStatusClass(status) {
    if (status === 'CANCELLED') return 'cancelled'
    if (status === 'COMPLETED' || status === 'CLOSED') return 'done'
    if (status === 'PROCESSING' || status === 'ASSIGNED') return 'processing'
    return 'created'
  },

  buildTimeline(item) {
    const steps = [
      { label: '提交工单', time: item.submittedTime || item.createdTime, active: true },
      { label: '已分配', time: item.assignedTime, active: Boolean(item.assignedTime) },
      { label: '处理中', time: item.processingTime, active: Boolean(item.processingTime) },
      { label: '完成', time: item.completedTime, active: Boolean(item.completedTime) },
      { label: '关闭', time: item.closedTime, active: Boolean(item.closedTime) }
    ]
    if (item.cancelledTime || item.status === 'CANCELLED') {
      return [
        steps[0],
        { label: '已取消', time: item.cancelledTime || item.updatedTime, active: true }
      ].map(step => ({
        ...step,
        timeText: formatDateTime(step.time)
      }))
    }
    return steps.map(step => ({
      ...step,
      timeText: step.time ? formatDateTime(step.time) : ''
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
