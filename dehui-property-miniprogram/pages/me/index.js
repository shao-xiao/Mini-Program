const { getUserType } = require('../../utils/capabilities')
const {
  ensureWechatSession,
  refreshCurrentProfile,
  authorizeWechatPhone,
  selectIdentity,
  unbindCurrentIdentity,
  logout: logoutSession
} = require('../../utils/auth')
const { isConsentCurrent, acceptCurrentConsent, readConsent } = require('../../utils/consent')

const PUBLIC_PROFILE = {
  userType: 'PUBLIC',
  capabilities: [],
  availableIdentities: [],
  profile: { name: '', maskedPhone: '', avatarUrl: '' }
}

function normalizeProfile(payload) {
  if (!payload || typeof payload !== 'object') return { ...PUBLIC_PROFILE }
  const display = payload.profile && typeof payload.profile === 'object' ? payload.profile : {}
  return {
    ...payload,
    ...display,
    profile: display,
    userType: getUserType(payload),
    capabilities: Array.isArray(payload.capabilities) ? payload.capabilities : [],
    availableIdentities: Array.isArray(payload.availableIdentities)
      ? payload.availableIdentities
      : []
  }
}

function accountPresentation(profile) {
  const userType = getUserType(profile)
  const tenantInfo = profile.tenantInfo || {}
  const staffInfo = profile.staffInfo || {}
  const bindingState = profile.bindingState || 'UNVERIFIED'
  const availableIdentities = Array.isArray(profile.availableIdentities)
    ? profile.availableIdentities
    : []
  const showIdentitySelector = availableIdentities.length > 1 ||
    (bindingState === 'PENDING_SELECTION' && availableIdentities.length > 0)
  const identityText = userType === 'TENANT'
    ? '租户'
    : (userType === 'STAFF' ? '物业员工' : '公开访问')
  const statusText = profile.bindingState === 'PENDING_SELECTION'
    ? '请选择业务身份'
    : (profile.bindingState === 'UNMATCHED'
        ? '手机号已验证，未匹配业务档案'
        : (profile.bindingState === 'BOUND' ? '身份已绑定' : '尚未验证手机号'))
  return {
    userType,
    isTenant: userType === 'TENANT',
    isInternal: userType === 'STAFF',
    identityText,
    statusText,
    tenantName: tenantInfo.name || '',
    contactRoleLabel: tenantInfo.contactRoleLabel || '',
    contactName: profile.name || staffInfo.name || '',
    phone: profile.maskedPhone || '',
    availableIdentities,
    showIdentitySelector,
    bindingState,
    sessionState: profile.sessionState || 'ANONYMOUS'
  }
}

Page({
  data: {
    pageState: 'loading',
    errorMessage: '',
    profile: PUBLIC_PROFILE,
    userType: 'PUBLIC',
    isTenant: false,
    isInternal: false,
    identityText: '公开访问',
    statusText: '正在确认身份',
    tenantName: '',
    contactRoleLabel: '',
    contactName: '',
    phone: '',
    bindingState: 'UNVERIFIED',
    sessionState: 'ANONYMOUS',
    availableIdentities: [],
    showIdentitySelector: false,
    agreementsAccepted: false,
    consentRecord: null,
    phoneLoginLoading: false,
    identityLoading: false,
    unbindLoading: false,
    logoutLoading: false
  },

  onLoad() {
    this._active = true
    this._refreshSequence = 0
  },

  onShow() {
    this._active = true
    this.safeSetData({
      agreementsAccepted: isConsentCurrent(),
      consentRecord: readConsent()
    })
    this.refreshIdentity()
  },

  onUnload() {
    this._active = false
    this._refreshSequence += 1
  },

  safeSetData(payload) {
    if (this._active) this.setData(payload)
  },

  applyProfile(payload) {
    const profile = normalizeProfile(payload)
    this.safeSetData({
      pageState: 'success',
      errorMessage: '',
      profile,
      ...accountPresentation(profile)
    })
    return profile
  },

  async refreshIdentity() {
    const requestId = ++this._refreshSequence
    this.safeSetData({ pageState: 'loading', errorMessage: '' })
    try {
      await ensureWechatSession()
      const payload = await refreshCurrentProfile()
      if (!this._active || requestId !== this._refreshSequence) return
      this.applyProfile(payload)
    } catch (error) {
      if (!this._active || requestId !== this._refreshSequence) return
      this.safeSetData({
        pageState: 'error',
        errorMessage: error && error.message ? error.message : '暂时无法确认账号状态',
        profile: PUBLIC_PROFILE,
        ...accountPresentation(PUBLIC_PROFILE)
      })
    }
  },

  onAgreementChange(event) {
    const accepted = (event.detail.value || []).includes('accepted')
    if (accepted) acceptCurrentConsent()
    this.safeSetData({ agreementsAccepted: accepted, consentRecord: readConsent() })
  },

  ensureAgreementAccepted() {
    if (this.data.agreementsAccepted && isConsentCurrent()) return true
    wx.showToast({ title: '请先阅读并同意服务协议和隐私保护指引', icon: 'none' })
    return false
  },

  promptAgreementRequired() {
    this.ensureAgreementAccepted()
  },

  async loginWithPhoneNumber(event) {
    if (!this.ensureAgreementAccepted()) return
    const detail = (event && event.detail) || {}
    if (!detail.code || (detail.errMsg && !detail.errMsg.includes('ok'))) {
      wx.showToast({ title: '未完成手机号快捷登录', icon: 'none' })
      return
    }
    this.safeSetData({ phoneLoginLoading: true })
    try {
      await ensureWechatSession({ userInitiated: true })
      const profile = await authorizeWechatPhone(detail.code, { silent: true })
      this.applyProfile(profile)
      if (profile.bindingState === 'UNMATCHED') {
        wx.showModal({
          title: '尚未匹配业务身份',
          content: profile.bindingMessage || '手机号已验证，请联系管理员维护租户联系人或物业员工档案。',
          showCancel: false
        })
      } else if (profile.bindingState === 'PENDING_SELECTION') {
        wx.showToast({ title: '请选择要进入的身份', icon: 'none' })
      } else {
        wx.showToast({ title: '身份匹配成功', icon: 'success' })
      }
    } catch (error) {
      wx.showToast({ title: error && error.message ? error.message : '手机号登录失败', icon: 'none' })
    } finally {
      this.safeSetData({ phoneLoginLoading: false })
    }
  },

  async chooseIdentity(event) {
    const candidateToken = event.currentTarget.dataset.token
    if (!candidateToken) return
    this.safeSetData({ identityLoading: true })
    try {
      const profile = await selectIdentity(candidateToken, { silent: true })
      this.applyProfile(profile)
      wx.showToast({ title: '身份已切换', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error && error.message ? error.message : '身份切换失败', icon: 'none' })
      await this.refreshIdentity()
    } finally {
      this.safeSetData({ identityLoading: false })
    }
  },

  async unbindCurrent() {
    this.safeSetData({ unbindLoading: true })
    try {
      const profile = await unbindCurrentIdentity({ silent: true })
      this.applyProfile(profile)
      wx.showToast({ title: '当前业务绑定已解除', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error && error.message ? error.message : '解除绑定失败', icon: 'none' })
    } finally {
      this.safeSetData({ unbindLoading: false })
    }
  },

  async logout() {
    this.safeSetData({ logoutLoading: true })
    await logoutSession()
    this.applyProfile(PUBLIC_PROFILE)
    this.safeSetData({ logoutLoading: false })
    wx.showToast({ title: '已退出登录', icon: 'success' })
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
            content: '请在小程序资料页查看用户隐私保护指引。',
            showCancel: false
          })
        }
      })
    }
  }
})
