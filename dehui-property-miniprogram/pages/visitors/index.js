const api = require('../../utils/request')
const { refreshCurrentProfile } = require('../../utils/auth')
const { canAccessFeature, getFeatureAccessState } = require('../../utils/capabilities')
const { loadModuleFieldSchema, requiredError } = require('../../utils/moduleFieldSchema')
const { ensurePrivacyAuthorization } = require('../../utils/privacy')

const FEATURE_KEY = 'visitor'

function pad(value) {
  return String(value).padStart(2, '0')
}

function toDateValue(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function toTimeValue(date) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function buildDefaultVisitDate(date = new Date()) {
  const next = new Date(date.getTime() + 60 * 60 * 1000)
  return {
    visitDate: toDateValue(next),
    visitClock: toTimeValue(next)
  }
}

function toApiDateTime(dateValue, timeValue) {
  return dateValue && timeValue ? `${dateValue}T${timeValue}:00` : ''
}

function toDateTimeText(value) {
  if (!value) return '-'
  const [date, time = ''] = String(value).replace('T', ' ').split(' ')
  const [year, month, day] = date.split('-')
  return `${year}年${month}月${day}日 ${time.slice(0, 5)}`
}

function initialForm() {
  const { visitDate, visitClock } = buildDefaultVisitDate()
  return {
    visitorName: '',
    visitorPhone: '',
    visitCompany: '',
    companyName: '',
    visitReason: '',
    remark: '',
    visitDate,
    visitClock,
    visitTime: visitClock,
    carPlateNo: '',
    plateNumber: ''
  }
}

function createSubmissionToken() {
  const random = Math.random().toString(36).slice(2)
  const extra = Math.random().toString(36).slice(2)
  return `visitor_${Date.now().toString(36)}_${random}${extra}`.slice(0, 96)
}

function requestFailureState(error) {
  const code = Number(error && (error.code || error.statusCode || error.status))
  if (code === 401) {
    return {
      pageState: 'login-required',
      errorMessage: '登录状态已失效，请重新登录后创建访客邀请。'
    }
  }
  if (code === 403) {
    return {
      pageState: 'unauthorized',
      errorMessage: '当前账号没有租户访客邀请资格，请联系物业确认租户联系人状态。'
    }
  }
  return {
    pageState: 'error',
    errorMessage: error && error.message ? error.message : '访客邀请加载失败，请稍后重试。'
  }
}

function deniedState(profile) {
  const result = getFeatureAccessState(FEATURE_KEY, profile)
  const state = typeof result === 'string' ? result : result && (result.state || result.reason)
  if (state === 'login-required' || (result && result.loginRequired)) {
    return {
      pageState: 'login-required',
      errorMessage: '请先登录租户账号，再创建或查看访客邀请。'
    }
  }
  return {
    pageState: 'unauthorized',
    errorMessage: '访客邀请仅向已匹配的有效租户联系人开放，当前账号暂无使用资格。'
  }
}

Page({
  data: {
    pageState: 'loading',
    loading: true,
    submitting: false,
    errorMessage: '',
    isPublicMode: false,
    submissionToken: '',
    identityText: '',
    today: toDateValue(new Date()),
    form: initialForm(),
    fieldSchema: { fields: [] },
    visitors: [],
    page: 1,
    pageSize: 20,
    total: 0,
    noMore: false,
    loadingMore: false,
    qrPopupVisible: false,
    qrLoading: false,
    qrImagePath: '',
    qrVisitor: null
  },

  onLoad(options = {}) {
    this._active = true
    this._loadSequence = 0
    const isPublicMode = options.mode === 'public' || options.mode === 'guide'
    this.setData({
      isPublicMode,
      pageState: isPublicMode ? 'success' : 'loading',
      loading: !isPublicMode,
      ...(isPublicMode
        ? {
            submissionToken: createSubmissionToken(),
            form: initialForm(),
            today: toDateValue(new Date())
          }
        : {})
    })
    if (isPublicMode) wx.setNavigationBarTitle({ title: '访客预约记录' })
  },

  onShow() {
    this._active = true
    if (this.data.isPublicMode) return
    this.loadVisitors()
  },

  onUnload() {
    this._active = false
    this._loadSequence = Number(this._loadSequence || 0) + 1
  },

  safeSetData(payload) {
    if (!this._active) return
    this.setData(payload)
  },

  isCurrentRequest(requestId) {
    return this._active && requestId === this._loadSequence
  },

  async loadVisitors(append = false) {
    if (append && (this.data.loadingMore || this.data.noMore)) return
    const requestId = Number(this._loadSequence || 0) + 1
    this._loadSequence = requestId
    this.safeSetData(append ? { loadingMore: true } : {
      pageState: 'loading',
      loading: true,
      errorMessage: '',
      identityText: '',
      visitors: []
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
        api.get('/mobile/visitors', { page, pageSize: this.data.pageSize }),
        append ? Promise.resolve(this.data.fieldSchema) : loadModuleFieldSchema('visitors')
      ])
      if (!this.isCurrentRequest(requestId)) return
      const incoming = (data.records || data.visitors || []).map(item => ({
        ...item,
        visitReason: item.visitReason || item.remark || '',
        visitTimeText: toDateTimeText(item.visitAt || `${item.visitDate || ''}T${item.visitTime || ''}`),
        leaveTimeText: toDateTimeText(item.leaveTime),
        cancellable: item.status === 'REGISTERED' || item.status === 'PENDING',
        statusText: this.toStatusText(item.status),
        statusClass: this.toStatusClass(item.status)
      }))
      const visitors = append ? this.data.visitors.concat(incoming) : incoming
      this.safeSetData({
        pageState: visitors.length ? 'success' : 'empty',
        loading: false,
        loadingMore: false,
        page: Number(data.page || page),
        total: Number(data.total || 0),
        noMore: visitors.length >= Number(data.total || 0),
        identityText: this.toIdentityText(data.profile || profile),
        fieldSchema,
        visitors
      })
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({
        ...requestFailureState(error),
        loading: false,
        loadingMore: false,
        identityText: '',
        visitors: []
      })
    }
  },

  retryLoad() {
    this.loadVisitors()
  },

  onPullDownRefresh() {
    if (this.data.isPublicMode) {
      wx.stopPullDownRefresh()
      return
    }
    this.loadVisitors().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.isPublicMode) return
    this.loadVisitors(true)
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/business-detail/index?type=visitor&id=${event.currentTarget.dataset.id}` })
  },

  handleAccessError(error) {
    const state = requestFailureState(error)
    if (state.pageState === 'error') return false
    this.safeSetData({
      ...state,
      loading: false,
      identityText: '',
      visitors: []
    })
    return true
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field
    this.safeSetData({ [`form.${field}`]: event.detail.value })
  },

  onVisitDateChange(event) {
    this.safeSetData({ 'form.visitDate': event.detail.value })
  },

  onVisitTimeChange(event) {
    this.safeSetData({
      'form.visitClock': event.detail.value,
      'form.visitTime': event.detail.value
    })
  },

  async submitVisitor() {
    if (this.data.submitting) return
    const schemaForm = this.data.isPublicMode ? null : this.selectComponent('#schemaForm')
    const validation = schemaForm && schemaForm.validate()
    if (validation && !validation.valid) {
      wx.showToast({ title: validation.uploading ? '文件上传中，请稍候' : '请完善访客信息', icon: 'none' })
      return
    }
    const form = this.data.form
    const schemaError = this.data.isPublicMode ? '' : requiredError(this.data.fieldSchema, {
      ...form,
      visitTime: toApiDateTime(form.visitDate, form.visitClock)
    })
    if (schemaError) {
      wx.showToast({ title: schemaError, icon: 'none' })
      return
    }
    if (!form.visitorName.trim()) {
      wx.showToast({ title: '请填写访客姓名', icon: 'none' })
      return
    }
    if (!form.visitorPhone.trim()) {
      wx.showToast({ title: '请填写访客手机号', icon: 'none' })
      return
    }
    if (this.data.isPublicMode && !/^1[3-9]\d{9}$/.test(form.visitorPhone.trim())) {
      wx.showToast({ title: '请填写正确的11位手机号', icon: 'none' })
      return
    }
    if (!form.visitReason.trim()) {
      wx.showToast({ title: '请填写来访事由', icon: 'none' })
      return
    }
    if (!form.visitDate || !form.visitClock) {
      wx.showToast({ title: '请选择来访日期和时间', icon: 'none' })
      return
    }
    if (this.data.isPublicMode &&
      new Date(toApiDateTime(form.visitDate, form.visitClock)).getTime() <= Date.now()) {
      wx.showToast({ title: '来访时间需晚于当前时间', icon: 'none' })
      return
    }
    if (this.data.isPublicMode) {
      const authorized = await ensurePrivacyAuthorization('publicVisitor')
      if (!authorized) {
        wx.showToast({ title: '需要同意隐私说明后才能提交', icon: 'none' })
        return
      }
    }

    this.safeSetData({ submitting: true })
    try {
      const dynamicPayload = schemaForm ? schemaForm.getPayload() : form
      const created = await api.post(
        this.data.isPublicMode ? '/mobile/public/visitors' : '/mobile/visitors',
        {
        visitorName: dynamicPayload.visitorName,
        visitorPhone: dynamicPayload.visitorPhone,
        visitDate: dynamicPayload.visitDate || form.visitDate,
        visitTime: dynamicPayload.visitTime || form.visitClock,
        carPlateNo: dynamicPayload.plateNumber || form.carPlateNo,
        visitCompany: dynamicPayload.companyName || form.visitCompany,
        visitReason: dynamicPayload.visitReason,
        remark: dynamicPayload.remark,
        ...(this.data.isPublicMode
          ? { submissionToken: this.data.submissionToken }
          : { customFields: dynamicPayload.customFields })
        }
      )
      if (!this._active) return
      const identifier = created.invitationCode || created.visitCode || created.code || created.id
      wx.showModal({
        title: this.data.isPublicMode ? '预约已提交' : '邀请已创建',
        content: this.data.isPublicMode
          ? (identifier ? `预约编号：${identifier}\n预约已提交，请等待物业审核。` : '预约已提交，请等待物业审核。')
          : (identifier
              ? `邀请记录：${identifier}\n审核通过后，物业会上传门禁二维码。`
              : '预约已提交，可在“我的访客邀请”查看审核状态。'),
        showCancel: false
      })
      this.safeSetData({
        form: initialForm(),
        ...(this.data.isPublicMode ? { submissionToken: createSubmissionToken() } : {})
      })
      if (!this.data.isPublicMode) this.loadVisitors()
    } catch (error) {
      this.handleAccessError(error)
    } finally {
      this.safeSetData({ submitting: false })
    }
  },

  onSchemaChange(event) {
    this.safeSetData({ form: { ...this.data.form, ...((event.detail && event.detail.values) || {}) } })
  },

  onUploadState() {},

  cancelVisitor(event) {
    const id = Number(event.currentTarget.dataset.id)
    const visitor = this.data.visitors.find(item => Number(item.id) === id)
    wx.showModal({
      title: '取消邀请',
      content: visitor ? `确定取消“${visitor.visitorName}”的访客邀请吗？` : '确定取消该邀请吗？',
      confirmText: '取消邀请',
      confirmColor: '#d93025',
      success: async res => {
        if (!res.confirm) return
        try {
          await api.post(`/mobile/visitors/${id}/cancel`)
          if (!this._active) return
          wx.showToast({ title: '邀请已取消', icon: 'success' })
          this.loadVisitors()
        } catch (error) {
          this.handleAccessError(error)
        }
      }
    })
  },

  goProfile() {
    wx.switchTab({ url: '/pages/mine/index' })
  },

  async showQrCode(event) {
    const id = Number(event.currentTarget.dataset.id)
    const visitor = this.data.visitors.find(item => Number(item.id) === id)
    if (!visitor || !visitor.qrCodeUrl) {
      wx.showToast({ title: '门禁二维码尚未上传', icon: 'none' })
      return
    }
    this.safeSetData({
      qrPopupVisible: true,
      qrLoading: true,
      qrImagePath: '',
      qrVisitor: visitor
    })
    try {
      const path = await api.download(visitor.qrCodeUrl, `visitor-qr-${id}.png`, { silent: true })
      if (!this._active || Number(this.data.qrVisitor?.id) !== id) return
      this.safeSetData({ qrLoading: false, qrImagePath: path })
    } catch (error) {
      if (!this._active) return
      this.safeSetData({ qrLoading: false })
      this.handleAccessError(error)
      wx.showToast({ title: error?.message || '二维码加载失败', icon: 'none' })
    }
  },

  closeQrCode() {
    this.safeSetData({
      qrPopupVisible: false,
      qrLoading: false,
      qrImagePath: '',
      qrVisitor: null
    })
  },

  stopPropagation() {},

  toIdentityText(profile) {
    if (!profile) return ''
    return `租户：${profile.boundTenantName || profile.nickname || ''}`
  },

  toStatusText(status) {
    if (status === 'PENDING') return '待审核'
    if (status === 'REGISTERED') return '审核通过'
    if (status === 'REJECTED') return '已驳回'
    if (status === 'ENTERED') return '已到访'
    if (status === 'LEFT') return '已离场'
    if (status === 'CANCELLED') return '已取消'
    if (status === 'EXPIRED') return '已过期'
    return status || '未知'
  },

  toStatusClass(status) {
    if (status === 'CANCELLED' || status === 'REJECTED') return 'cancelled'
    if (status === 'LEFT') return 'left'
    if (status === 'ENTERED') return 'entered'
    return 'registered'
  }
})
