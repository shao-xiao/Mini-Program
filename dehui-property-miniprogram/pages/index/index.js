const api = require('../../utils/request')
const {
  getUserType,
  isTenantProfile,
  getVisibleHomeFeatures,
  getFeatureAccessState,
  canAccessFeature
} = require('../../utils/capabilities')
const { ensureWechatSession, refreshCurrentProfile } = require('../../utils/auth')

const PUBLIC_PROFILE = {
  userType: 'PUBLIC'
}

const ANNOUNCEMENT_TYPE_TEXT = {
  NOTICE: '通知',
  ACTIVITY: '园区活动',
  PROPERTY: '物业通知',
  REMINDER: '运营提醒'
}

const FEATURE_ALIASES = {
  announcements: 'announcement',
  investmentRooms: 'investment',
  investmentBookingCreate: 'investmentBooking',
  viewingBooking: 'investmentBooking',
  bills: 'bill',
  workorders: 'workorder',
  meetings: 'meeting',
  energySummary: 'energy',
  visitors: 'visitor',
  login: 'tenantLogin'
}

const FEATURE_DETAILS = {
  announcement: {
    key: 'announcement',
    eyebrow: '园区',
    title: '公告通知',
    description: '查看园区最新通知与服务动态',
    url: '/pages/announcements/index'
  },
  investment: {
    key: 'investment',
    eyebrow: '招商',
    title: '招商中心',
    description: '查看可租房源并预约看房',
    url: '/pages/investment/index'
  },
  investmentBooking: {
    key: 'investmentBooking',
    eyebrow: '预约',
    title: '预约看房',
    description: '提交到访意向，招商顾问将与您联系',
    url: '/pages/investment/index'
  },
  tenantLogin: {
    key: 'tenantLogin',
    eyebrow: '租户',
    title: '租户登录',
    description: '使用手机号快捷登录进入租户服务',
    action: 'tenant-login'
  },
  bill: {
    key: 'bill',
    eyebrow: '财务',
    title: '我的账单',
    description: '查看租金、物业、能耗与停车账单',
    url: '/pages/bills/index'
  },
  workorder: {
    key: 'workorder',
    eyebrow: '服务',
    title: '我要报修',
    description: '提交报修并查看处理进度',
    url: '/pages/workorders/index'
  },
  meeting: {
    key: 'meeting',
    eyebrow: '空间',
    title: '会议预约',
    description: '查询可用会议室并发起预约',
    url: '/pages/meeting/index'
  },
  meetingPublic: {
    key: 'meetingPublic',
    eyebrow: '空间',
    title: '会议预约',
    description: '查看会议室，手机号快捷登录后预约',
    url: '/pages/meeting/index?mode=public'
  },
  energy: {
    key: 'energy',
    eyebrow: '能耗',
    title: '企业能耗',
    description: '查看真实抄表用量、历史趋势和费用依据',
    url: '/pages/energy/index'
  },
  parkingCompany: {
    key: 'parkingCompany',
    eyebrow: '停车',
    title: '企业车位',
    description: '查看企业有效车位及车辆、人员绑定',
    url: '/pages/parking/index?scope=company'
  },
  visitor: {
    key: 'visitor',
    eyebrow: '来访',
    title: '访客预约',
    description: '提交访客预约并查看审核与到访状态',
    url: '/pages/visitors/index'
  },
  visitorGuide: {
    key: 'visitorGuide',
    eyebrow: '来访',
    title: '访客预约',
    description: '无需登录即可提交访客预约',
    url: '/pages/visitors/index?mode=public'
  },
  staffWorkbench: {
    key: 'staffWorkbench',
    eyebrow: '物业',
    title: '移动工作台',
    description: '进入当前后台权限允许的物业移动功能',
    url: '/subPackages/staff/pages/workbench/index'
  }
}

function formatDateTime(value) {
  if (!value) return ''
  const normalized = String(value).replace('T', ' ')
  const [datePart, time = ''] = normalized.split(' ')
  const [year, month, day] = datePart.split('-')
  if (!year || !month || !day) {
    return normalized.slice(0, 16)
  }
  return `${year}-${month}-${day} ${time.slice(0, 5)}`
}

function getBannerSummary(item) {
  if (!item) return ''
  const source = item.summary || item.content || item.subtitle || ''
  return String(source).replace(/\s+/g, ' ').trim()
}

function getAnnouncementTypeText(item) {
  const type = String(item && (item.noticeType || item.type) || '').trim().toUpperCase()
  return ANNOUNCEMENT_TYPE_TEXT[type] || '园区公告'
}

function buildBanners(announcements) {
  const raw = Array.isArray(announcements)
    ? announcements
    : announcements && Array.isArray(announcements.records)
      ? announcements.records
      : []

  return raw.filter((item) => item && item.id).map((item) => ({
    id: item.id,
    title: item.title || '园区公告',
    typeText: getAnnouncementTypeText(item),
    subtitle: getBannerSummary(item),
    time: formatDateTime(item.publishTime || item.createdTime || item.time),
    url: `/pages/announcements/detail?id=${item.id}`
  }))
}

function extractProfile(value) {
  if (!value) return null
  const display = value.profile || value.userInfo || {}
  return {
    ...value,
    ...display,
    profile: display,
    tenantInfo: value.tenantInfo || display.tenantInfo || null,
    staffInfo: value.staffInfo || display.staffInfo || null,
    capabilities: Array.isArray(value.capabilities)
      ? value.capabilities
      : (Array.isArray(display.capabilities) ? display.capabilities : [])
  }
}

function featureKeyOf(feature) {
  const rawKey = typeof feature === 'string'
    ? feature
    : feature && (feature.key || feature.featureKey || feature.id)
  return FEATURE_ALIASES[rawKey] || rawKey
}

function buildFeatureCards(profile) {
  const visible = getVisibleHomeFeatures(profile) || []
  const cards = visible.map((feature) => {
    const key = featureKeyOf(feature)
    const details = FEATURE_DETAILS[key]
    if (!details) return null
    return {
      ...(typeof feature === 'object' ? feature : {}),
      ...details,
      key
    }
  }).filter(Boolean)

  if (getUserType(profile) === 'PUBLIC') {
    cards.push(FEATURE_DETAILS.tenantLogin)
  }
  return cards
}

function accessAllowed(state) {
  if (typeof state === 'boolean') return state
  if (!state) return true
  if (typeof state === 'string') return state === 'allowed' || state === 'granted'
  if (typeof state.allowed === 'boolean') return state.allowed
  return state.state === 'allowed' || state.status === 'allowed' || state.access === 'allowed'
}

Page({
  data: {
    announcementState: 'loading',
    announcementMessage: '',
    identityState: 'loading',
    identityMessage: '',
    banners: [],
    currentBannerIndex: 0,
    announcementSwiperHeight: 0,
    profile: PUBLIC_PROFILE,
    userType: 'PUBLIC',
    isTenant: false,
    isInternal: false,
    hasStaffWorkbench: false,
    canViewAnnouncements: true,
    identityLabel: '公开访问',
    modules: buildFeatureCards(PUBLIC_PROFILE)
  },

  onLoad() {
    this._active = true
    this._pageSequence = 0
    this._homeSequence = 0
    this._identitySequence = 0
    this._announcementMeasureSequence = 0
  },

  onShow() {
    this._active = true
    this.refreshPage()
  },

  onUnload() {
    this._active = false
    this._pageSequence = Number(this._pageSequence || 0) + 1
    this._homeSequence = Number(this._homeSequence || 0) + 1
    this._identitySequence = Number(this._identitySequence || 0) + 1
    this._announcementMeasureSequence = Number(this._announcementMeasureSequence || 0) + 1
  },

  safeSetData(payload, callback) {
    if (!this._active) return
    this.setData(payload, callback)
  },

  async refreshPage() {
    const requestId = Number(this._pageSequence || 0) + 1
    this._pageSequence = requestId
    const profile = await this.loadIdentity()
    if (!this._active || requestId !== this._pageSequence) return
    this.loadHome(profile || PUBLIC_PROFILE)
  },

  async loadHome(profile = this.data.profile || PUBLIC_PROFILE) {
    const requestId = Number(this._homeSequence || 0) + 1
    this._homeSequence = requestId
    this.safeSetData({
      announcementState: 'loading',
      announcementMessage: ''
    })

    const canViewAnnouncements = canAccessFeature('announcement', profile)
    const [announcementResult] = await Promise.allSettled([
      canViewAnnouncements
        ? api.request({ url: '/mobile/announcements/carousel', method: 'GET', silent: true })
        : Promise.resolve([])
    ])
    if (!this._active || requestId !== this._homeSequence) return

    const banners = announcementResult.status === 'fulfilled'
      ? buildBanners(announcementResult.value)
      : []

    this.safeSetData({
      banners,
      currentBannerIndex: 0,
      announcementSwiperHeight: 0,
      announcementState: !canViewAnnouncements
        ? 'unauthorized'
        : announcementResult.status === 'rejected'
        ? 'error'
        : banners.length
          ? 'success'
          : 'empty',
      announcementMessage: !canViewAnnouncements
        ? '当前账号暂未获得公告查看能力。'
        : announcementResult.status === 'rejected'
        ? '重点公告暂时无法加载，请稍后重试。'
        : ''
    }, () => {
      if (banners.length) {
        this.syncAnnouncementSwiperHeight(0)
      }
    })
  },

  retryHome() {
    this.loadHome(this.data.profile || PUBLIC_PROFILE)
  },

  syncAnnouncementSwiperHeight(index = this.data.currentBannerIndex) {
    if (!this._active || !this.data.banners.length) return
    const measureId = Number(this._announcementMeasureSequence || 0) + 1
    this._announcementMeasureSequence = measureId

    const measure = () => {
      if (!this._active || measureId !== this._announcementMeasureSequence) return
      this.createSelectorQuery()
        .selectAll('.banner-item')
        .boundingClientRect((rects) => {
          if (
            !this._active ||
            measureId !== this._announcementMeasureSequence ||
            !Array.isArray(rects) ||
            !rects.length
          ) return
          const safeIndex = Math.min(Math.max(Number(index) || 0, 0), rects.length - 1)
          if (safeIndex !== Number(this.data.currentBannerIndex || 0)) return
          const height = Math.ceil(Number(rects[safeIndex] && rects[safeIndex].height) || 0)
          if (!height || Math.abs(height - Number(this.data.announcementSwiperHeight || 0)) < 1) return
          this.safeSetData({ announcementSwiperHeight: height })
        })
        .exec()
    }

    if (typeof wx.nextTick === 'function') {
      wx.nextTick(measure)
    } else {
      setTimeout(measure, 0)
    }
  },

  onBannerChange(event) {
    const current = Number(event && event.detail && event.detail.current) || 0
    this.safeSetData({ currentBannerIndex: current }, () => {
      this.syncAnnouncementSwiperHeight(current)
    })
  },

  onResize() {
    this.syncAnnouncementSwiperHeight()
  },

  async loadIdentity() {
    const requestId = Number(this._identitySequence || 0) + 1
    this._identitySequence = requestId
    this.safeSetData({ identityState: 'loading', identityMessage: '' })
    let profile = PUBLIC_PROFILE
    try {
      const session = await ensureWechatSession()
      profile = extractProfile(await refreshCurrentProfile()) || extractProfile(session) || PUBLIC_PROFILE
    } catch (error) {
      if (!this._active || requestId !== this._identitySequence) return null
      const code = Number(error && (error.code || error.statusCode))
      this.safeSetData({
        identityState: code === 401 ? 'login-required' : (code === 403 ? 'unauthorized' : 'error'),
        identityMessage: error && error.message ? error.message : '当前身份暂时无法确认，请稍后重试。',
        profile: PUBLIC_PROFILE,
        userType: 'PUBLIC',
        isTenant: false,
        isInternal: false,
        hasStaffWorkbench: false,
        canViewAnnouncements: true,
        identityLabel: '身份未确认',
        modules: buildFeatureCards(PUBLIC_PROFILE)
      })
      return null
    }

    if (!this._active || requestId !== this._identitySequence) return null
    const userType = getUserType(profile)
    this.safeSetData({
      identityState: 'success',
      identityMessage: '',
      profile,
      userType,
      isTenant: isTenantProfile(profile),
      isInternal: userType === 'STAFF',
      hasStaffWorkbench: userType === 'STAFF' && canAccessFeature('staffWorkbench', profile),
      canViewAnnouncements: canAccessFeature('announcement', profile),
      identityLabel: userType === 'TENANT' ? '租户' : (userType === 'STAFF' ? '物业员工' : '公开访问'),
      modules: buildFeatureCards(profile)
    })
    return profile
  },

  openModule(event) {
    const key = event.currentTarget.dataset.key
    const item = this.data.modules.find((module) => module.key === key)
    if (!item) return

    if (item.action === 'tenant-login') {
      wx.navigateTo({ url: '/pages/me/index' })
      return
    }

    const accessState = getFeatureAccessState(item.key, this.data.profile)
    if (!accessAllowed(accessState)) {
      wx.showModal({
        title: '请先登录租户身份',
        content: '该服务仅向已匹配的租户联系人开放。',
        cancelText: '稍后再说',
        confirmText: '去登录',
        confirmColor: '#D71920',
        success: (result) => {
          if (result.confirm) {
            wx.navigateTo({ url: '/pages/me/index' })
          }
        }
      })
      return
    }

    if (item.url) {
      wx.navigateTo({ url: item.url })
    }
  },

  openBanner(event) {
    const index = Number(event.currentTarget.dataset.index)
    const banner = this.data.banners[index]
    if (banner && banner.url) {
      wx.navigateTo({ url: banner.url })
    }
  },

  openAnnouncements() {
    wx.navigateTo({ url: '/pages/announcements/index' })
  }
})
