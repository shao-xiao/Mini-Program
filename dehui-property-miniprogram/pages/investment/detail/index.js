const api = require('../../../utils/request')
const { authorizeWechatPhone, ensureWechatSession, refreshCurrentProfile } = require('../../../utils/auth')
const { canAccessFeature, getFeatureAccessState } = require('../../../utils/capabilities')
const { showInvestmentPhoneConsult } = require('../../../utils/investmentPhone')
const { getBaseURL } = require('../../../config/env')

const CONTACT_NAME_REG = /^[\u4e00-\u9fa5A-Za-z·\s]{2,20}$/
const COMPANY_NAME_REG = /^[\u4e00-\u9fa5A-Za-z0-9（）()&_\-\s·.,，]{2,50}$/
const SAFE_TEXT_REG = /^[\u4e00-\u9fa5A-Za-z0-9（）()&_\-\s·.,，、/／:：;；]*$/
const PUBLIC_PROFILE = { userType: 'PUBLIC' }

function accessMessage(featureKey, profile) {
  const state = getFeatureAccessState(featureKey, profile)
  return state && state.loginRequired
    ? '请先建立登录会话后重试。'
    : '当前账号暂未获得该招商服务能力。'
}

function initialLeadForm() {
  return {
    name: '',
    phone: '',
    companyName: '',
    intendedRoom: '',
    desiredArea: '',
    intendedUse: '',
    preferredVisitDate: '',
    preferredVisitClock: '',
    remark: '',
    listingId: null
  }
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function today() {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

function buildPreferredVisitTime(dateValue, timeValue) {
  return dateValue && timeValue ? `${dateValue} ${timeValue}` : ''
}

function profileContactName(profile) {
  return String(profile?.realName || profile?.displayName || profile?.name || profile?.nickname || '').trim()
}

function apiOrigin() {
  return getBaseURL().replace(/\/api\/?$/, '')
}

function normalizeImageUrl(value) {
  const url = String(value || '').trim()
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  if (url.startsWith('/api/')) return `${apiOrigin()}${url}`
  if (url.startsWith('/')) return `${apiOrigin()}${url}`
  return `${apiOrigin()}/${url}`
}

function typeText(value) {
  const map = { OFFICE: '办公', WAREHOUSE: '仓库', STORAGE: '仓库', PUBLIC_AREA: '公共区域', SHOP: '商业', OTHER: '其他' }
  return map[value] || value || '未分类'
}

function statusText(value) {
  const map = { VACANT: '空闲', AVAILABLE: '可租', RENTED: '已出租', RESERVED: '预约/不可用' }
  return map[value] || value || '待确认'
}

function formatArea(value) {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue) || numberValue <= 0) return '面积待确认'
  return `${Number.isInteger(numberValue) ? numberValue : numberValue.toFixed(1)}㎡`
}

function splitText(value) {
  if (Array.isArray(value)) return value.filter(Boolean)
  return String(value || '')
    .split(/[,，、\n]/)
    .map(item => item.trim())
    .filter(Boolean)
}

function normalizeDetail(data) {
  const floorText = data.floorName || data.floor || '楼层待确认'
  const roomNumber = data.roomNumber || data.roomNo || data.roomName || '房源待确认'
  const images = Array.isArray(data.images) ? data.images.map(item => normalizeImageUrl(item.imageUrl)) : []
  const cover = normalizeImageUrl(data.coverImageUrl)
  return {
    ...data,
    listingId: data.listingId || data.id,
    title: data.title || `${roomNumber} 招商房源`,
    floorText,
    roomNumber,
    areaText: formatArea(data.area),
    roomTypeText: typeText(data.roomType),
    statusText: statusText(data.status),
    coverImageUrl: cover,
    imageUrls: images.length ? images : (cover ? [cover] : []),
    tags: splitText(data.tags),
    highlights: splitText(data.highlights),
    bookingLabel: [floorText, roomNumber, data.title || '招商房源'].filter(Boolean).join(' / ')
  }
}

function validateLeadForm(form) {
  const name = String(form.name || '').trim()
  const companyName = String(form.companyName || '').trim()
  const intendedRoom = String(form.intendedRoom || '').trim()
  const intendedUse = String(form.intendedUse || '').trim()
  const remark = String(form.remark || '').trim()
  const desiredArea = String(form.desiredArea || '').trim()

  if (!CONTACT_NAME_REG.test(name)) {
    return '请输入正确的联系人姓名'
  }
  if (!COMPANY_NAME_REG.test(companyName)) {
    return '请输入正确的公司名称'
  }
  if (desiredArea && !/^\d+(\.\d{1,2})?$/.test(desiredArea)) {
    return '请输入正确的意向面积，只能填写数字'
  }
  if (intendedRoom && (!SAFE_TEXT_REG.test(intendedRoom) || intendedRoom.length > 50)) {
    return '请输入正确的意向房源'
  }
  if (intendedUse && (!SAFE_TEXT_REG.test(intendedUse) || intendedUse.length > 50)) {
    return '请输入正确的用途'
  }
  if (remark.length > 200) {
    return '备注最多 200 个字符'
  }
  if (remark && !SAFE_TEXT_REG.test(remark)) {
    return '备注包含暂不支持的字符'
  }
  if (!form.preferredVisitDate || !form.preferredVisitClock) {
    return '请选择期望看房日期和时间'
  }
  return ''
}

Page({
  data: {
    id: null,
    loading: false,
    pageState: 'loading',
    errorMessage: '',
    submitting: false,
    profile: PUBLIC_PROFILE,
    overview: {},
    canBookViewing: false,
    detail: null,
    today: today(),
    leadPopupVisible: false,
    leadForm: initialLeadForm()
  },

  onLoad(options) {
    this._active = true
    this._loadSequence = 0
    this.setData({ id: options.id })
    this.loadDetail()
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

  async ensureSession() {
    if (wx.getStorageSync('token')) return true
    try {
      await ensureWechatSession({ silent: true })
      return Boolean(wx.getStorageSync('token'))
    } catch (error) {
      return false
    }
  },

  async loadDetail() {
    const requestId = Number(this._loadSequence || 0) + 1
    this._loadSequence = requestId
    if (!this.data.id) {
      this.safeSetData({ pageState: 'empty', detail: null })
      return
    }
    this.safeSetData({
      loading: true,
      pageState: 'loading',
      errorMessage: '',
      detail: null,
      canBookViewing: false
    })
    try {
      const profile = await refreshCurrentProfile()
      if (!this.isCurrentRequest(requestId)) return
      if (!canAccessFeature('investment', profile)) {
        this.safeSetData({
          profile,
          pageState: 'unauthorized',
          errorMessage: accessMessage('investment', profile),
          detail: null
        })
        return
      }
      const [detailResult, overviewResult] = await Promise.allSettled([
        api.get(`/mobile/investment/listings/${this.data.id}`),
        api.get('/mobile/investment/showcase')
      ])
      if (!this.isCurrentRequest(requestId)) return
      if (detailResult.status === 'rejected') {
        throw detailResult.reason
      }
      const data = detailResult.value
      this.safeSetData({
        profile,
        overview: overviewResult.status === 'fulfilled' ? (overviewResult.value || {}) : {},
        canBookViewing: canAccessFeature('investmentBooking', profile) && Boolean(data?.availableForAppointment),
        detail: data ? normalizeDetail(data) : null,
        pageState: data ? 'success' : 'empty'
      })
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      const status = Number(error && (error.code || error.statusCode))
      this.safeSetData({
        detail: null,
        pageState: status === 404 ? 'empty' : (status === 401 ? 'login-required' : (status === 403 ? 'unauthorized' : 'error')),
        errorMessage: error && error.message ? error.message : '房源详情暂时无法加载'
      })
    } finally {
      if (this.isCurrentRequest(requestId)) {
        this.safeSetData({ loading: false })
      }
    }
  },

  retryDetail() {
    this.loadDetail()
  },

  callConsultant() {
    showInvestmentPhoneConsult(this, this.data.overview, wx)
  },

  openLeadPopup() {
    if (!this.data.canBookViewing || !canAccessFeature('investmentBooking', this.data.profile)) {
      wx.showModal({
        title: '预约看房暂未开放',
        content: accessMessage('investmentBooking', this.data.profile),
        showCancel: false
      })
      return
    }
    const detail = this.data.detail
    this.safeSetData({
      leadPopupVisible: true,
      leadForm: detail ? {
        ...initialLeadForm(),
        name: profileContactName(this.data.profile),
        phone: this.data.profile?.maskedPhone || '',
        intendedRoom: detail.bookingLabel,
        desiredArea: detail.area ? String(detail.area) : '',
        intendedUse: detail.roomTypeText,
        listingId: detail.listingId
      } : this.data.leadForm
    })
  },

  closeLeadPopup() {
    this.safeSetData({ leadPopupVisible: false })
  },

  noop() {},

  onLeadInput(event) {
    const field = event.currentTarget.dataset.field
    this.safeSetData({ [`leadForm.${field}`]: event.detail.value })
  },

  onDateChange(event) {
    this.safeSetData({ 'leadForm.preferredVisitDate': event.detail.value })
  },

  onTimeChange(event) {
    this.safeSetData({ 'leadForm.preferredVisitClock': event.detail.value })
  },

  async authorizePhone(event) {
    const phoneCode = event?.detail?.code
    if (!phoneCode) {
      wx.showToast({ title: '需要手机号授权后才能预约', icon: 'none' })
      return
    }
    try {
      const me = await authorizeWechatPhone(phoneCode, { silent: false })
      const profile = me || {}
      const canBookViewing = canAccessFeature('investmentBooking', profile)
        && Boolean(this.data.detail?.availableForAppointment)
      this.safeSetData({
        profile,
        canBookViewing,
        leadPopupVisible: canBookViewing && this.data.leadPopupVisible,
        'leadForm.phone': profile.maskedPhone || ''
      })
      if (!canBookViewing) {
        wx.showModal({
          title: '预约看房暂未开放',
          content: accessMessage('investmentBooking', profile),
          showCancel: false
        })
        return
      }
      wx.showToast({ title: '手机号已验证', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error?.message || '手机号授权失败', icon: 'none' })
    }
  },

  async submitLead() {
    if (!this.data.canBookViewing || !canAccessFeature('investmentBooking', this.data.profile)) {
      wx.showModal({
        title: '预约看房暂未开放',
        content: accessMessage('investmentBooking', this.data.profile),
        showCancel: false
      })
      return
    }
    const form = this.data.leadForm
    if (!this.data.profile?.maskedPhone) {
      wx.showToast({ title: '请先完成手机号验证', icon: 'none' })
      return
    }
    const validation = validateLeadForm(form)
    if (validation) {
      wx.showToast({ title: validation, icon: 'none' })
      return
    }
    const hasSession = await this.ensureSession()
    if (!this._active) return
    if (!hasSession) {
      wx.showToast({ title: '登录未完成', icon: 'none' })
      return
    }
    this.safeSetData({ submitting: true })
    try {
      await api.post('/mobile/investment/appointments', {
        name: String(form.name || '').trim(),
        companyName: String(form.companyName || '').trim(),
        listingId: form.listingId,
        desiredArea: form.desiredArea ? Number(form.desiredArea) : null,
        intendedUse: String(form.intendedUse || '').trim(),
        preferredVisitTime: buildPreferredVisitTime(form.preferredVisitDate, form.preferredVisitClock),
        remark: String(form.remark || '').trim()
      })
      if (!this._active) return
      wx.showToast({ title: '预约已提交', icon: 'success' })
      this.safeSetData({ leadPopupVisible: false, leadForm: initialLeadForm() })
    } catch (error) {
      if (!this._active) return
      wx.showToast({ title: error?.message || '提交失败', icon: 'none' })
    } finally {
      this.safeSetData({ submitting: false })
    }
  }
})
