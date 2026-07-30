const api = require('../../utils/request')
const { ensurePrivacyAuthorization } = require('../../utils/privacy')
const { refreshCurrentProfile } = require('../../utils/auth')
const { canAccessFeature, getFeatureAccessState } = require('../../utils/capabilities')
const { loadModuleFieldSchema, fieldOptions, fieldValidation } = require('../../utils/moduleFieldSchema')

const FEATURE_KEY = 'workorder'
const MAX_IMAGE_COUNT = 6
const HISTORY_PREVIEW_COUNT = 3

function pad(value) {
  return String(value).padStart(2, '0')
}

function currentDateTimeValue(now = new Date()) {
  return [
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`,
    `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  ].join(' ')
}

function toDateTimeText(value) {
  if (!value) return '-'
  const [date, time = ''] = String(value).replace('T', ' ').split(' ')
  const [year, month, day] = date.split('-')
  const [hour = '00', minute = '00', second = '00'] = time.split(':')
  return `${year}年${month}月${day}日 ${hour}时${minute}分${second.slice(0, 2)}秒`
}

function initialForm() {
  return {
    problemCategory: 'WATER',
    reporterName: '',
    companyName: '',
    reportedAt: currentDateTimeValue(),
    description: '',
    remark: '',
    customFields: {}
  }
}

function applyProfileDefaults(profile, form = {}) {
  const displayProfile = profile && profile.profile || profile || {}
  const tenantInfo = profile && profile.tenantInfo || displayProfile.tenantInfo || {}
  return {
    reporterName: form.reporterName || displayProfile.name || displayProfile.contactName || displayProfile.nickname || '',
    companyName: form.companyName || tenantInfo.name || displayProfile.boundTenantName || '',
    reportedAt: form.reportedAt || currentDateTimeValue()
  }
}

function customOnlySchema(schema) {
  return {
    ...(schema || {}),
    fields: ((schema && schema.fields) || []).filter(field => field && field.source === 'CUSTOM')
  }
}

function categoryText(options, value) {
  const matched = options.find(option => option.value === value)
  return matched && matched.label ? matched.label : '其他'
}

function compressImage(filePath) {
  if (!wx.compressImage) return Promise.resolve(filePath)
  return new Promise(resolve => {
    wx.compressImage({
      src: filePath,
      quality: 75,
      success: res => resolve(res.tempFilePath || filePath),
      fail: () => resolve(filePath)
    })
  })
}

function requestFailureState(error) {
  const code = Number(error && (error.code || error.statusCode || error.status))
  if (code === 401) {
    return {
      pageState: 'login-required',
      errorMessage: '登录状态已失效，请重新登录后使用报修服务。'
    }
  }
  if (code === 403) {
    return {
      pageState: 'unauthorized',
      errorMessage: '当前账号没有租户报修资格，请联系物业确认租户联系人状态。'
    }
  }
  return {
    pageState: 'error',
    errorMessage: error && error.message ? error.message : '报修服务加载失败，请稍后重试。'
  }
}

function deniedState(profile) {
  const result = getFeatureAccessState(FEATURE_KEY, profile)
  const state = typeof result === 'string' ? result : result && (result.state || result.reason)
  if (state === 'login-required' || (result && result.loginRequired)) {
    return {
      pageState: 'login-required',
      errorMessage: '请先登录租户账号，再提交或查看报修。'
    }
  }
  return {
    pageState: 'unauthorized',
    errorMessage: '报修服务仅向已匹配的有效租户联系人开放，当前账号暂无使用资格。'
  }
}

Page({
  data: {
    pageState: 'loading',
    loading: true,
    submitting: false,
    uploadingImages: false,
    customUploading: false,
    errorMessage: '',
    identityText: '',
    form: initialForm(),
    reportedAtText: '',
    selectedImages: [],
    maxImageCount: MAX_IMAGE_COUNT,
    imageTip: `最多上传${MAX_IMAGE_COUNT}张，上传完成后再提交`,
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
    fieldSchema: { fields: [] },
    customFieldSchema: { fields: [] },
    workOrders: [],
    visibleWorkOrders: [],
    historyExpanded: false,
    historyPreviewCount: HISTORY_PREVIEW_COUNT,
    page: 1,
    pageSize: 20,
    total: 0,
    noMore: false,
    loadingMore: false
  },

  onLoad() {
    this._active = true
    this._loadSequence = 0
  },

  onShow() {
    this._active = true
    this.loadWorkOrders()
  },

  onUnload() {
    this._active = false
    this._loadSequence = Number(this._loadSequence || 0) + 1
  },

  safeSetData(payload, callback) {
    if (!this._active) return
    this.setData(payload, callback)
  },

  isCurrentRequest(requestId) {
    return this._active && requestId === this._loadSequence
  },

  async loadWorkOrders(append = false) {
    if (append && (this.data.loadingMore || this.data.noMore)) return
    const requestId = Number(this._loadSequence || 0) + 1
    this._loadSequence = requestId
    this.safeSetData(append ? { loadingMore: true } : {
      pageState: 'loading',
      loading: true,
      errorMessage: '',
      identityText: '',
      workOrders: [],
      visibleWorkOrders: [],
      evaluationVisible: false
    })

    try {
      const profile = await refreshCurrentProfile()
      if (!this.isCurrentRequest(requestId)) return
      if (!canAccessFeature(FEATURE_KEY, profile)) {
        this.safeSetData({
          ...deniedState(profile),
          loading: false
        })
        return
      }

      const page = append ? this.data.page + 1 : 1
      const [data, fieldSchema] = await Promise.all([
        api.get('/mobile/workorders', { page, pageSize: this.data.pageSize }),
        append ? Promise.resolve(this.data.fieldSchema) : loadModuleFieldSchema('workorders')
      ])
      if (!this.isCurrentRequest(requestId)) return
      const effectiveProfile = data.profile || profile || {}
      const categories = fieldOptions(fieldSchema, 'problemCategory', this.data.categories)
      const incoming = (data.records || data.workOrders || []).map(item => {
        const hasRating = item.rating !== null && item.rating !== undefined
        return {
          ...item,
          reportedAtText: toDateTimeText(item.reportedAt || item.createdTime),
          createdTimeText: toDateTimeText(item.createdTime),
          categoryText: categoryText(categories, item.problemCategory || item.category),
          statusText: this.toStatusText(item.status),
          statusClass: this.toStatusClass(item.status),
          cancellable: item.status === 'CREATED' || item.status === 'SUBMITTED',
          evaluable: (item.status === 'COMPLETED' || item.status === 'CLOSED') && !hasRating,
          ratingStars: this.ratingStars(item.rating),
          timeline: this.buildTimeline(item)
        }
      })
      const workOrders = append ? this.data.workOrders.concat(incoming) : incoming
      const form = {
        ...this.data.form,
        ...applyProfileDefaults(effectiveProfile, this.data.form)
      }
      const visibleWorkOrders = this.data.historyExpanded
        ? workOrders
        : workOrders.slice(0, HISTORY_PREVIEW_COUNT)
      this.safeSetData({
        pageState: workOrders.length ? 'success' : 'empty',
        loading: false,
        loadingMore: false,
        page: Number(data.page || page),
        total: Number(data.total || 0),
        noMore: workOrders.length >= Number(data.total || 0),
        identityText: this.toIdentityText(effectiveProfile),
        form,
        reportedAtText: toDateTimeText(form.reportedAt),
        fieldSchema,
        customFieldSchema: customOnlySchema(fieldSchema),
        categories,
        maxImageCount: Number(fieldValidation(fieldSchema, 'images').maxFiles) || MAX_IMAGE_COUNT,
        workOrders,
        visibleWorkOrders
      })
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({
        ...requestFailureState(error),
        loading: false,
        loadingMore: false,
        identityText: '',
        workOrders: [],
        visibleWorkOrders: []
      })
    }
  },

  retryLoad() {
    this.loadWorkOrders()
  },

  onPullDownRefresh() {
    this.loadWorkOrders().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.historyExpanded) this.loadWorkOrders(true)
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/business-detail/index?type=workorder&id=${event.currentTarget.dataset.id}` })
  },

  toggleHistory() {
    if (!Number(this.data.total || 0)) return
    const historyExpanded = !this.data.historyExpanded
    this.safeSetData({
      historyExpanded,
      visibleWorkOrders: historyExpanded
        ? this.data.workOrders
        : this.data.workOrders.slice(0, HISTORY_PREVIEW_COUNT)
    })
  },

  handleAccessError(error) {
    const state = requestFailureState(error)
    if (state.pageState === 'error') return false
    this.safeSetData({
      ...state,
      loading: false,
      identityText: '',
      workOrders: [],
      evaluationVisible: false
    })
    return true
  },

  showActionError(error, fallbackMessage) {
    if (this.handleAccessError(error) || !this._active) return
    wx.showToast({
      title: error && error.message ? error.message : fallbackMessage,
      icon: 'none'
    })
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field
    this.safeSetData({ [`form.${field}`]: event.detail.value })
  },

  selectCategory(event) {
    this.safeSetData({ 'form.problemCategory': event.currentTarget.dataset.value })
  },

  async chooseImages() {
    if (this.data.uploadingImages) return
    const maximum = Number(this.data.maxImageCount) || MAX_IMAGE_COUNT
    const remaining = maximum - this.data.selectedImages.length
    if (remaining <= 0) {
      wx.showToast({ title: `最多上传${MAX_IMAGE_COUNT}张照片`, icon: 'none' })
      return
    }

    const authorized = await ensurePrivacyAuthorization('workorderImage')
    if (!this._active) return
    if (!authorized) {
      wx.showToast({ title: '同意隐私说明后可添加照片', icon: 'none' })
      return
    }

    wx.chooseImage({
      count: remaining,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async res => {
        this.safeSetData({ uploadingImages: true })
        wx.showLoading({ title: '上传照片中', mask: true })
        try {
          const uploadedImages = []
          for (const filePath of res.tempFilePaths) {
            const compressedPath = await compressImage(filePath)
            const uploaded = await api.upload('/mobile/files', compressedPath, 'file', {
              formData: { fieldType: 'IMAGE' },
              silent: true
            })
            const fileId = Number(uploaded && (uploaded.fileId || uploaded.id))
            if (!fileId) throw new Error('照片上传结果无效')
            uploadedImages.push({
              fileId,
              url: compressedPath,
              name: uploaded.name || `现场照片${this.data.selectedImages.length + uploadedImages.length + 1}`,
              sortOrder: this.data.selectedImages.length + uploadedImages.length
            })
          }
          if (!this._active) return
          this.safeSetData({
            selectedImages: this.data.selectedImages.concat(uploadedImages).slice(0, maximum)
          })
        } catch (error) {
          if (!this.handleAccessError(error)) {
            wx.showToast({ title: error && error.message || '照片上传失败，请重试', icon: 'none' })
          }
        } finally {
          wx.hideLoading()
          this.safeSetData({ uploadingImages: false })
        }
      }
    })
  },

  removeSelectedImage(event) {
    const index = Number(event.currentTarget.dataset.index)
    const selectedImages = this.data.selectedImages.filter((_, itemIndex) => itemIndex !== index)
    this.safeSetData({ selectedImages })
  },

  previewSelectedImage(event) {
    const urls = this.data.selectedImages.map(item => item.url).filter(Boolean)
    wx.previewImage({
      current: event.currentTarget.dataset.url,
      urls
    })
  },

  async submitWorkOrder() {
    if (this.data.uploadingImages || this.data.customUploading) {
      wx.showToast({ title: '照片或附件仍在上传，请稍候', icon: 'none' })
      return
    }
    const schemaForm = this.selectComponent('#schemaForm')
    const validation = schemaForm && schemaForm.validate()
    if (validation && !validation.valid) {
      wx.showToast({ title: validation.uploading ? '文件上传中，请稍候' : '请完善报修信息', icon: 'none' })
      return
    }
    const form = this.data.form
    if (!form.reporterName.trim()) {
      wx.showToast({ title: '请填写报修人', icon: 'none' })
      return
    }
    if (!form.companyName.trim()) {
      wx.showToast({ title: '请填写公司名称', icon: 'none' })
      return
    }
    if (!form.problemCategory) {
      wx.showToast({ title: '请选择问题类型', icon: 'none' })
      return
    }
    if (!form.description.trim()) {
      wx.showToast({ title: '请描述问题', icon: 'none' })
      return
    }

    this.safeSetData({ submitting: true })
    try {
      const dynamicPayload = schemaForm ? schemaForm.getPayload() : { customFields: {} }
      const created = await api.post('/mobile/workorders', {
        reporterName: form.reporterName.trim(),
        companyName: form.companyName.trim(),
        reportedAt: form.reportedAt,
        problemCategory: form.problemCategory,
        description: form.description.trim(),
        remark: form.remark.trim(),
        images: {
          version: 1,
          files: this.data.selectedImages.map((item, sortOrder) => ({
            fileId: item.fileId,
            sortOrder
          }))
        },
        customFields: dynamicPayload.customFields || {}
      })
      if (!this._active) return
      wx.showModal({
        title: '报修已提交',
        content: created.orderNumber ? `工单号：${created.orderNumber}` : '可在“我的报修”查看处理进度。',
        showCancel: false
      })
      const nextForm = initialForm()
      this.safeSetData({
        form: nextForm,
        reportedAtText: toDateTimeText(nextForm.reportedAt),
        selectedImages: []
      })
      this.loadWorkOrders()
    } catch (error) {
      this.showActionError(error, '报修提交失败，请稍后重试')
    } finally {
      this.safeSetData({ submitting: false })
    }
  },

  onSchemaChange(event) {
    this.safeSetData({
      'form.customFields': {
        ...this.data.form.customFields,
        ...((event.detail && event.detail.values) || {})
      }
    })
  },

  onUploadState(event) {
    this.safeSetData({ customUploading: Boolean(event.detail && event.detail.uploading) })
  },

  cancelWorkOrder(event) {
    const id = Number(event.currentTarget.dataset.id)
    const workOrder = this.data.workOrders.find(item => Number(item.id) === id)
    wx.showModal({
      title: '撤回报修',
      content: workOrder ? `确定撤回“${workOrder.title}”吗？` : '确定撤回该报修吗？',
      confirmText: '撤回',
      confirmColor: '#D71920',
      success: async res => {
        if (!res.confirm) return
        try {
          await api.post(`/mobile/workorders/${id}/cancel`)
          if (!this._active) return
          wx.showToast({ title: '已撤回', icon: 'success' })
          this.loadWorkOrders()
        } catch (error) {
          this.showActionError(error, '报修撤回失败，请稍后重试')
        }
      }
    })
  },

  openEvaluation(event) {
    const id = Number(event.currentTarget.dataset.id)
    const workOrder = this.data.workOrders.find(item => Number(item.id) === id)
    this.safeSetData({
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
    this.safeSetData({ evaluationVisible: false })
  },

  noop() {},

  selectRating(event) {
    this.safeSetData({ 'evaluationForm.rating': Number(event.currentTarget.dataset.value) })
  },

  onEvaluationInput(event) {
    this.safeSetData({ 'evaluationForm.content': event.detail.value })
  },

  async submitEvaluation() {
    const form = this.data.evaluationForm
    if (!form.workOrderId) return
    try {
      await api.post(`/mobile/workorders/${form.workOrderId}/evaluation`, {
        rating: form.rating,
        content: form.content
      })
      if (!this._active) return
      wx.showToast({ title: '评价已提交', icon: 'success' })
      this.safeSetData({ evaluationVisible: false })
      this.loadWorkOrders()
    } catch (error) {
      this.showActionError(error, '评价提交失败，请稍后重试')
    }
  },

  goProfile() {
    wx.switchTab({ url: '/pages/mine/index' })
  },

  toIdentityText(profile) {
    if (!profile) return ''
    const displayProfile = profile.profile || profile
    const tenantInfo = profile.tenantInfo || displayProfile.tenantInfo || {}
    return tenantInfo.name || displayProfile.boundTenantName || ''
  },

  toStatusClass(status) {
    if (status === 'CANCELLED') return 'cancelled'
    if (status === 'COMPLETED' || status === 'CLOSED') return 'done'
    if (status === 'PROCESSING' || status === 'ASSIGNED') return 'processing'
    return 'created'
  },

  toStatusText(status) {
    if (status === 'CREATED' || status === 'SUBMITTED') return '已提交'
    if (status === 'ASSIGNED') return '已受理'
    if (status === 'PROCESSING') return '处理中'
    if (status === 'COMPLETED') return '已完成'
    if (status === 'CLOSED') return '已关闭'
    if (status === 'CANCELLED') return '已撤回'
    return '状态更新'
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
