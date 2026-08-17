const api = require('../../utils/request')
const { authorizeWechatPhone, ensureWechatSession, refreshCurrentProfile } = require('../../utils/auth')
const { canAccessFeature, getFeatureAccessState } = require('../../utils/capabilities')
const { showInvestmentPhoneConsult } = require('../../utils/investmentPhone')
const { getBaseURL } = require('../../config/env')

const CONTACT_NAME_REG = /^[\u4e00-\u9fa5A-Za-z·\s]{2,20}$/
const COMPANY_NAME_REG = /^[\u4e00-\u9fa5A-Za-z0-9（）()&_\-\s·.,，]{2,50}$/
const SAFE_TEXT_REG = /^[\u4e00-\u9fa5A-Za-z0-9（）()&_\-\s·.,，、/／:：;；]*$/
const PUBLIC_PROFILE = { userType: 'PUBLIC' }
const EMPTY_ROOM_STATS = Object.freeze({ count: 0, area: '0㎡', office: 0, recommended: 0 })

function accessMessage(featureKey, profile) {
  const state = getFeatureAccessState(featureKey, profile)
  return state && state.loginRequired
    ? '请先建立登录会话后重试。'
    : '当前账号暂未获得该招商服务能力。'
}

const TYPE_FILTERS = [
  { label: '全部', value: 'all' },
  { label: '办公', value: 'OFFICE' },
  { label: '仓库', value: 'WAREHOUSE' },
  { label: '公共区域', value: 'PUBLIC_AREA' },
  { label: '其他', value: 'OTHER' }
]

const AREA_FILTERS = [
  { label: '全部', value: 'all' },
  { label: '50㎡以下', value: 'lt50' },
  { label: '50-100㎡', value: '50-100' },
  { label: '100㎡以上', value: 'gt100' }
]

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

function leadStatusText(value) {
  const map = {
    NEW: '待跟进',
    FOLLOWING: '跟进中',
    VIEWED: '已看房',
    NEGOTIATING: '洽谈中',
    INVALID: '无效',
    CONVERTED_TENANT: '已转租户',
    CONVERTED_CONTRACT: '已转合同',
    VIEWING_SCHEDULED: '已预约看房',
    CONVERTED: '已转化'
  }
  return map[value] || value || '待跟进'
}

function formatArea(value) {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue) || numberValue <= 0) return '面积待确认'
  return `${Number.isInteger(numberValue) ? numberValue : numberValue.toFixed(1)}㎡`
}

function splitTags(value) {
  if (Array.isArray(value)) return value.filter(Boolean).slice(0, 4)
  return String(value || '')
    .split(/[,，、\n]/)
    .map(item => item.trim())
    .filter(Boolean)
    .slice(0, 4)
}

const SHOWCASE_BANNER_SECTIONS = [
  { key: 'introduction', label: '园区介绍' },
  { key: 'highlight', label: '园区优势' },
  { key: 'policy', label: '招商政策' }
]

function compactText(value, maxLength = 72) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text
}

function buildInvestmentBanners(overview) {
  const source = overview && typeof overview === 'object' ? overview : {}
  const banners = []

  SHOWCASE_BANNER_SECTIONS.forEach((section) => {
    const records = Array.isArray(source[section.key]) ? source[section.key] : []
    records.forEach((item, index) => {
      if (!item || (!item.title && !item.content && !item.subtitle)) return
      banners.push({
        key: `${section.key}-${item.id || index}`,
        sectionKey: section.key,
        eyebrow: item.sectionName || section.label,
        title: item.title || section.label,
        subtitle: compactText(item.subtitle, 30),
        summary: compactText(item.content || item.subtitle),
        imageUrl: normalizeImageUrl(item.imageUrl),
        sortOrder: Number(item.sortOrder ?? index)
      })
    })
  })

  return banners.sort((left, right) => left.sortOrder - right.sortOrder)
}

function normalizeRoom(item) {
  const floorText = item.floorName || item.floor || '楼层待确认'
  const roomNumber = item.roomNumber || item.roomNo || item.roomName || '房源待确认'
  const areaValue = Number(item.area || 0)
  const roomType = item.roomType || item.type || 'OTHER'
  const title = item.title || `${roomNumber} 招商房源`
  return {
    ...item,
    id: item.listingId || item.id,
    listingId: item.listingId || item.id,
    roomId: item.roomId,
    title,
    subtitle: item.subtitle || '',
    floorText,
    floorFilterValue: floorText,
    roomNumber,
    areaValue,
    areaText: formatArea(areaValue),
    roomType,
    roomTypeText: typeText(roomType),
    statusText: statusText(item.status),
    coverImageUrl: normalizeImageUrl(item.coverImageUrl),
    tags: splitTags(item.tags || item.highlights),
    bookingLabel: [floorText, roomNumber, title].filter(Boolean).join(' / ')
  }
}

function floorFiltersFromRooms(rooms) {
  const floors = Array.from(new Set(rooms.map(item => item.floorFilterValue).filter(Boolean))).sort(compareFloorLabel)
  return [{ label: '全部', value: 'all' }].concat(floors.map(item => ({ label: item, value: item })))
}

function floorSortValue(value) {
  const text = String(value || '').trim().toUpperCase()
  const basementMatch = text.match(/(?:^|[^A-Z])B\s*(\d+)/)
  if (basementMatch) return -Number(basementMatch[1])
  const floorMatch = text.match(/(-?\d+(?:\.\d+)?)\s*F/)
  if (floorMatch) return Number(floorMatch[1])
  const numberMatch = text.match(/-?\d+(?:\.\d+)?/)
  return numberMatch ? Number(numberMatch[0]) : Number.MAX_SAFE_INTEGER
}

function compareFloorLabel(a, b) {
  const left = floorSortValue(a)
  const right = floorSortValue(b)
  if (left !== right) return left - right
  return String(a || '').localeCompare(String(b || ''), 'zh-Hans-CN')
}

function matchArea(areaValue, filter) {
  if (filter === 'lt50') return areaValue > 0 && areaValue < 50
  if (filter === '50-100') return areaValue >= 50 && areaValue <= 100
  if (filter === 'gt100') return areaValue > 100
  return true
}

function formatDateTime(value) {
  if (!value) return '未填写'
  return String(value).replace('T', ' ').slice(0, 16)
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
  if (!buildPreferredVisitTime(form.preferredVisitDate, form.preferredVisitClock)) {
    return '请选择期望看房时间'
  }
  return ''
}

function profileContactName(profile) {
  return String(profile?.contactName || profile?.name || profile?.nickname || '').trim()
}

Page({
  data: {
    loading: false,
    loadingRooms: false,
    loadingMyLeads: false,
    submitting: false,
    accessState: 'loading',
    accessMessage: '',
    profile: PUBLIC_PROFILE,
    canViewInvestment: false,
    canBookViewing: false,
    overviewError: '',
    investmentBanners: [],
    roomsError: '',
    leadsError: '',
    activeSection: 'rooms',
    today: today(),
    overview: {},
    rooms: [],
    filteredRooms: [],
    floorFilters: [{ label: '全部', value: 'all' }],
    typeFilters: TYPE_FILTERS,
    areaFilters: AREA_FILTERS,
    filters: { floor: 'all', type: 'all', area: 'all' },
    roomStats: EMPTY_ROOM_STATS,
    leadPopupVisible: false,
    leadForm: initialLeadForm(),
    myLeadRecords: []
  },

  onLoad() {
    this._active = true
    this._loadSequence = 0
  },

  onShow() {
    this._active = true
    this.loadPage()
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

  async ensureSession() {
    if (wx.getStorageSync('token')) return true
    try {
      await ensureWechatSession({ silent: true })
      return Boolean(wx.getStorageSync('token'))
    } catch (error) {
      return false
    }
  },

  async loadPage() {
    const requestId = Number(this._loadSequence || 0) + 1
    let overviewPromise = null
    this._loadSequence = requestId
    this.safeSetData({
      loading: true,
      accessState: 'loading',
      accessMessage: '',
      canViewInvestment: false,
      canBookViewing: false
    })
    try {
      const profile = await refreshCurrentProfile()
      if (!this.isCurrentRequest(requestId)) return
      if (!canAccessFeature('investment', profile)) {
        this.safeSetData({
          loading: false,
          accessState: 'unauthorized',
          accessMessage: accessMessage('investment', profile),
          profile,
          overview: {},
          investmentBanners: [],
          rooms: [],
          filteredRooms: [],
          myLeadRecords: [],
          roomStats: EMPTY_ROOM_STATS
        })
        return
      }
      const canBookViewing = canAccessFeature('investmentBooking', profile)
      this.safeSetData({
        accessState: 'ready',
        profile,
        canViewInvestment: true,
        canBookViewing,
        activeSection: canBookViewing ? this.data.activeSection : 'rooms'
      })
      overviewPromise = this.loadOverview(requestId)
      this._overviewPromise = overviewPromise
      await Promise.allSettled([
        overviewPromise,
        this.loadRooms(requestId),
        this.loadMyLeads(requestId, profile)
      ])
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      const code = Number(error && (error.code || error.statusCode))
      this.safeSetData({
        accessState: code === 401 ? 'login-required' : (code === 403 ? 'unauthorized' : 'error'),
        accessMessage: error && error.message ? error.message : '招商服务暂时无法加载，请稍后重试。',
        profile: PUBLIC_PROFILE,
        canViewInvestment: false,
        canBookViewing: false,
        overview: {},
        investmentBanners: [],
        rooms: [],
        filteredRooms: [],
        myLeadRecords: [],
        roomStats: EMPTY_ROOM_STATS
      })
    } finally {
      if (this._overviewPromise === overviewPromise) {
        this._overviewPromise = null
      }
      if (this.isCurrentRequest(requestId)) {
        this.safeSetData({ loading: false })
      }
    }
  },

  async loadOverview(requestId = this._loadSequence) {
    this.safeSetData({ overview: {}, investmentBanners: [], overviewError: '' })
    try {
      const overview = await api.get('/mobile/investment/showcase')
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({
        overview: overview || {},
        investmentBanners: buildInvestmentBanners(overview)
      })
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({
        overview: {},
        investmentBanners: [],
        overviewError: error && error.message ? error.message : '园区介绍暂时无法加载'
      })
    }
  },

  async loadRooms(requestId = this._loadSequence) {
    this.safeSetData({
      loadingRooms: true,
      roomsError: '',
      rooms: [],
      filteredRooms: [],
      roomStats: EMPTY_ROOM_STATS
    })
    try {
      const records = await api.get('/mobile/investment/listings')
      const rooms = (Array.isArray(records) ? records : []).map(normalizeRoom)
      const totalArea = rooms.reduce((sum, item) => sum + (item.areaValue || 0), 0)
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({
        rooms,
        floorFilters: floorFiltersFromRooms(rooms),
        roomStats: {
          count: rooms.length,
          area: formatArea(totalArea),
          office: rooms.filter(item => item.roomType === 'OFFICE').length,
          recommended: rooms.filter(item => Number(item.recommended) === 1 || item.recommended === true).length
        }
      })
      this.applyFilters()
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({
        rooms: [],
        filteredRooms: [],
        floorFilters: [{ label: '全部', value: 'all' }],
        roomStats: EMPTY_ROOM_STATS,
        roomsError: error && error.message ? error.message : '招商房源暂时无法加载'
      })
    } finally {
      if (this.isCurrentRequest(requestId)) {
        this.safeSetData({ loadingRooms: false })
      }
    }
  },

  applyFilters() {
    const { filters, rooms } = this.data
    const filteredRooms = rooms.filter(item => {
      const floorMatched = filters.floor === 'all' || item.floorFilterValue === filters.floor
      const typeMatched = filters.type === 'all' || item.roomType === filters.type
      const areaMatched = matchArea(item.areaValue, filters.area)
      return floorMatched && typeMatched && areaMatched
    })
    this.safeSetData({ filteredRooms })
  },

  changeFilter(event) {
    const { field, value } = event.currentTarget.dataset
    if (!field) return
    this.safeSetData({ [`filters.${field}`]: value || 'all' })
    this.applyFilters()
  },

  switchSection(event) {
    this.safeSetData({ activeSection: event.currentTarget.dataset.target || 'rooms' })
  },

  openInvestmentRooms() {
    this.safeSetData({ activeSection: 'rooms' }, () => {
      wx.pageScrollTo({
        selector: '#investment-room-section',
        duration: 350
      })
    })
  },

  viewDetail(event) {
    const id = event.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: `/pages/investment/detail/index?id=${id}` })
  },

  openLeadPopup(event) {
    if (!this.data.canBookViewing || !canAccessFeature('investmentBooking', this.data.profile)) {
      wx.showModal({
        title: '预约看房暂未开放',
        content: accessMessage('investmentBooking', this.data.profile),
        showCancel: false
      })
      return
    }
    const id = event?.currentTarget?.dataset?.id
    const room = id ? this.data.rooms.find(item => String(item.listingId) === String(id)) : null
    this.safeSetData({
      leadPopupVisible: true,
      leadForm: room ? {
        ...initialLeadForm(),
        name: profileContactName(this.data.profile),
        phone: this.data.profile?.maskedPhone || '',
        intendedRoom: room.bookingLabel,
        desiredArea: room.areaValue ? String(room.areaValue) : '',
        intendedUse: room.roomTypeText,
        listingId: room.listingId
      } : this.data.leadForm
    })
  },

  closeLeadPopup() {
    this.safeSetData({ leadPopupVisible: false })
  },

  noop() {},

  async callConsultant() {
    const overviewPromise = this._overviewPromise
    if (overviewPromise) {
      try {
        await overviewPromise
      } catch (error) {
        // loadOverview owns the visible error state; phone consultation uses its fallback.
      }
    }
    if (!this._active) return
    showInvestmentPhoneConsult(this, this.data.overview, wx)
  },

  async loadMyLeads(requestId = this._loadSequence, profile = this.data.profile) {
    if (!canAccessFeature('investmentBooking', profile)) {
      this.safeSetData({ loadingMyLeads: false, myLeadRecords: [], leadsError: '' })
      return
    }
    this.safeSetData({ loadingMyLeads: true, leadsError: '', myLeadRecords: [] })
    const hasSession = await this.ensureSession()
    if (!hasSession) {
      if (this.isCurrentRequest(requestId)) {
        this.safeSetData({
          loadingMyLeads: false,
          myLeadRecords: [],
          leadsError: '登录会话建立失败，请检查网络后重试'
        })
      }
      return
    }
    try {
      const records = await api.get('/mobile/investment/appointments/mine')
      const myLeadRecords = (Array.isArray(records) ? records : []).map(item => ({
        ...item,
        statusText: leadStatusText(item.status),
        preferredVisitTimeText: formatDateTime(item.preferredVisitTime),
        createdAtText: formatDateTime(item.createdAt)
      }))
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({ myLeadRecords })
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({
        myLeadRecords: [],
        leadsError: error && error.message ? error.message : '预约记录暂时无法加载'
      })
    } finally {
      if (this.isCurrentRequest(requestId)) {
        this.safeSetData({ loadingMyLeads: false })
      }
    }
  },

  retryRooms() {
    this.loadRooms()
  },

  retryMyLeads() {
    this.loadMyLeads(this._loadSequence, this.data.profile)
  },

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
        name: form.name,
        companyName: form.companyName,
        listingId: form.listingId,
        desiredArea: form.desiredArea ? Number(form.desiredArea) : null,
        intendedUse: form.intendedUse,
        remark: form.remark,
        preferredVisitTime: buildPreferredVisitTime(form.preferredVisitDate, form.preferredVisitClock)
      })
      if (!this._active) return
      wx.showToast({ title: '预约已提交', icon: 'success' })
      this.safeSetData({ leadPopupVisible: false, leadForm: initialLeadForm() })
      this.loadMyLeads(this._loadSequence, this.data.profile)
    } catch (error) {
      if (!this._active) return
      wx.showToast({ title: error?.message || '提交失败', icon: 'none' })
    } finally {
      this.safeSetData({ submitting: false })
    }
  }
})
