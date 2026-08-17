const {
  getUserType,
  isTenantProfile,
  getVisibleMineFeatures,
  getFeatureAccessState
} = require('../../utils/capabilities')
const api = require('../../utils/request')
const { ensureWechatSession, refreshCurrentProfile } = require('../../utils/auth')

const PUBLIC_PROFILE = {
  userType: 'PUBLIC'
}

const FEATURE_ALIASES = {
  bills: 'bill',
  contracts: 'contract',
  workorders: 'workorder',
  meetings: 'meeting',
  energySummary: 'energy',
  parking: 'parkingMine',
  visitors: 'visitor'
}

const BUSINESS_DETAILS = {
  bill: {
    key: 'bill',
    label: '账',
    title: '我的账单',
    description: '查看租金、物业、能耗、停车及其他服务账单',
    url: '/pages/bills/index'
  },
  contract: {
    key: 'contract',
    label: '合',
    title: '我的合同',
    description: '查看当前租户电子合同与附件',
    url: '/pages/contracts/index'
  },
  workorder: {
    key: 'workorder',
    label: '修',
    title: '我的报修',
    description: '提交报修并查看受理和处理进度',
    url: '/pages/workorders/index'
  },
  meeting: {
    key: 'meeting',
    label: '会',
    title: '我的会议',
    description: '预约会议室并管理本人预约记录',
    url: '/pages/meeting/index?mode=records',
    target: '/pages/meeting/index?mode=records'
  },
  energy: {
    key: 'energy',
    label: '能',
    title: '企业能耗',
    description: '查看真实抄表用量、趋势及费用依据',
    url: '/pages/energy/index'
  },
  parkingCompany: {
    key: 'parkingCompany',
    label: '企',
    title: '企业车位',
    description: '查看企业有效车位及车辆绑定',
    url: '/pages/parking/index?scope=company'
  },
  parkingMine: {
    key: 'parkingMine',
    label: '车',
    title: '我的车位',
    description: '查看本人车位、车牌和有效期',
    url: '/pages/parking/index?scope=mine'
  },
  visitor: {
    key: 'visitor',
    label: '访',
    title: '我的访客预约',
    description: '提交访客预约并查看审核状态',
    url: '/pages/visitors/index'
  },
  staffWorkbench: {
    key: 'staffWorkbench',
    label: '物',
    title: '物业移动工作台',
    description: '进入后台权限允许的巡检、工单和园区管理功能',
    url: '/subPackages/staff/pages/workbench/index'
  }
}

function featureKeyOf(feature) {
  const rawKey = typeof feature === 'string'
    ? feature
    : feature && (feature.key || feature.featureKey || feature.id)
  return FEATURE_ALIASES[rawKey] || rawKey
}

function buildBusinessCards(profile) {
  const visible = getVisibleMineFeatures(profile) || []
  return visible.map((feature) => {
    const key = featureKeyOf(feature)
    const details = BUSINESS_DETAILS[key]
    if (!details) return null
    return {
      ...(typeof feature === 'object' ? feature : {}),
      ...details,
      key
    }
  }).filter(Boolean)
}

function normalizeProfile(payload) {
  if (!payload) return PUBLIC_PROFILE
  const display = payload.profile || payload.userInfo || {}
  const profile = {
    ...payload,
    ...display,
    profile: display
  }
  return {
    ...profile,
    loginStatus: payload.sessionState === 'AUTHENTICATED',
    tenantInfo: payload.tenantInfo || profile.tenantInfo || null
  }
}

function profilePresentation(profile) {
  const userType = getUserType(profile)
  const tenantInfo = profile.tenantInfo || {}
  if (userType === 'TENANT') {
    return {
      identityText: '租户用户',
      identityStatus: '租户身份已匹配',
      identityDescription: '仅展示与当前租户联系人关联的业务记录。',
      tenantName: profile.boundTenantName || tenantInfo.tenantName || tenantInfo.name || '',
      tenantLogoUrl: String(tenantInfo.logoUrl || '').trim(),
      contactName: profile.name || '',
      contactPhone: profile.maskedPhone || '',
      accountActionText: '账号管理',
      accountStatusText: '已绑定'
    }
  }
  if (userType === 'STAFF') {
    return {
      identityText: '物业员工',
      identityStatus: '员工身份已绑定',
      identityDescription: '移动能力来自后台角色权限，仅显示服务端返回的能力白名单。',
      tenantName: '',
      tenantLogoUrl: '',
      contactName: '',
      contactPhone: profile.maskedPhone || '',
      accountActionText: '查看账号',
      accountStatusText: '员工账号'
    }
  }
  return {
    identityText: '公开用户',
    identityStatus: '尚未登录租户服务',
    identityDescription: '公告和招商可直接查看；租户业务需完成手机号快捷登录以匹配身份。',
    tenantName: '',
    tenantLogoUrl: '',
    contactName: '',
    contactPhone: profile.maskedPhone || '',
    accountActionText: '手机号快捷登录',
    accountStatusText: '未绑定'
  }
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
    pageState: 'loading',
    errorMessage: '',
    profile: PUBLIC_PROFILE,
    userType: 'PUBLIC',
    isTenant: false,
    isStaff: false,
    identityText: '公开用户',
    identityStatus: '正在确认身份',
    identityDescription: '',
    tenantName: '',
    tenantLogoUrl: '',
    tenantLogoPath: '',
    contactName: '',
    contactPhone: '',
    accountActionText: '手机号快捷登录',
    accountStatusText: '确认中',
    businessCards: []
  },

  onLoad() {
    this._active = true
    this._refreshSequence = 0
  },

  onShow() {
    this._active = true
    this.refresh()
  },

  onHide() {
    this._active = false
    this._refreshSequence = Number(this._refreshSequence || 0) + 1
  },

  onUnload() {
    this._active = false
    this._refreshSequence = Number(this._refreshSequence || 0) + 1
  },

  safeSetData(payload) {
    if (!this._active) return
    this.setData(payload)
  },

  async refresh() {
    const requestId = Number(this._refreshSequence || 0) + 1
    this._refreshSequence = requestId
    this.safeSetData({
      pageState: 'loading',
      errorMessage: '',
      tenantLogoUrl: '',
      tenantLogoPath: ''
    })
    try {
      await ensureWechatSession()
      const payload = await refreshCurrentProfile()
      if (!this._active || requestId !== this._refreshSequence) return
      const profile = normalizeProfile(payload)
      const presentation = profilePresentation(profile)
      const isTenant = isTenantProfile(profile)
      this.safeSetData({
        pageState: 'success',
        profile,
        userType: getUserType(profile),
        isTenant,
        isStaff: getUserType(profile) === 'STAFF',
        businessCards: buildBusinessCards(profile),
        tenantLogoPath: '',
        ...presentation
      })
      this.loadTenantLogo(presentation.tenantLogoUrl, requestId)
    } catch (error) {
      if (!this._active || requestId !== this._refreshSequence) return
      const code = Number(error && (error.code || error.statusCode))
      const presentation = profilePresentation(PUBLIC_PROFILE)
      this.safeSetData({
        pageState: code === 401 ? 'login-required' : (code === 403 ? 'unauthorized' : 'error'),
        errorMessage: error && error.message ? error.message : '暂时无法确认当前身份，请稍后重试。',
        profile: PUBLIC_PROFILE,
        userType: 'PUBLIC',
        isTenant: false,
        isStaff: false,
        businessCards: [],
        tenantLogoPath: '',
        ...presentation
      })
    }
  },

  async loadTenantLogo(logoUrl, requestId) {
    const normalizedLogoUrl = String(logoUrl || '').trim()
    if (!normalizedLogoUrl) return
    try {
      const tenantLogoPath = await api.download(
        normalizedLogoUrl,
        'tenant-logo.png',
        { silent: true, temporary: true }
      )
      if (!this._active || requestId !== this._refreshSequence) return
      if (!this.data.isTenant || this.data.tenantLogoUrl !== normalizedLogoUrl) return
      this.safeSetData({ tenantLogoPath })
    } catch (error) {
      if (!this._active || requestId !== this._refreshSequence) return
      if (!this.data.isTenant || this.data.tenantLogoUrl !== normalizedLogoUrl) return
      this.safeSetData({ tenantLogoPath: '' })
    }
  },

  openAccount() {
    wx.navigateTo({ url: '/pages/me/index' })
  },

  openBusiness(event) {
    const key = event.currentTarget.dataset.key
    const item = this.data.businessCards.find((card) => card.key === key)
    if (!item) return

    const accessState = getFeatureAccessState(item.key, this.data.profile)
    if (!accessAllowed(accessState)) {
      const loginRequired = !!(accessState && accessState.loginRequired)
      wx.showModal({
        title: loginRequired ? '请先登录租户身份' : '当前不可访问',
        content: loginRequired
          ? '该服务仅向已匹配的租户联系人开放。'
          : '当前账号没有该租户服务资格，请联系物业确认。',
        showCancel: false,
        confirmColor: '#D71920'
      })
      return
    }

    wx.navigateTo({ url: item.url })
  },

  openServiceAgreement() {
    wx.navigateTo({ url: '/pages/agreements/service/index' })
  },

  openPrivacyPolicy() {
    if (wx.openPrivacyContract) {
      wx.openPrivacyContract({
        fail() {
          wx.showModal({
            title: '用户隐私保护指引',
            content: '请在小程序资料页或手机号授权弹窗中查看用户隐私保护指引。',
            showCancel: false
          })
        }
      })
      return
    }
    wx.showModal({
      title: '用户隐私保护指引',
      content: '当前客户端版本暂不支持直接打开隐私保护指引，请升级客户端后查看。',
      showCancel: false
    })
  }
})
