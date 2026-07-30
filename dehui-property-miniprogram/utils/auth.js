const api = require('./request')

const MANUAL_LOGOUT_KEY = 'manualLogout'
const PUBLIC_AUTHORITY = Object.freeze({
  sessionState: 'ANONYMOUS',
  userType: 'PUBLIC',
  bindingState: 'UNVERIFIED',
  contactRole: null,
  profile: Object.freeze({ name: '', maskedPhone: '', avatarUrl: '' }),
  tenantInfo: null,
  staffInfo: null,
  capabilities: Object.freeze([]),
  dataScope: Object.freeze({ type: 'PUBLIC' }),
  availableIdentities: Object.freeze([])
})

let wechatSessionPromise = null
let sessionEpoch = 0
let profileRefreshPromise = null
let profileRefreshContext = null

function appInstance() {
  if (typeof getApp !== 'function') return null
  try {
    return getApp()
  } catch (error) {
    return null
  }
}

function normalizeUserType(value) {
  const type = String(value || 'PUBLIC').trim().toUpperCase()
  if (type === 'TENANT') return 'TENANT'
  if (type === 'STAFF' || type === 'INTERNAL') return 'STAFF'
  return 'PUBLIC'
}

function normalizeAuthority(payload) {
  if (!payload || typeof payload !== 'object') return { ...PUBLIC_AUTHORITY }
  const display = payload.profile && typeof payload.profile === 'object' ? payload.profile : {}
  const userType = normalizeUserType(payload.userType || payload.identity || display.userType)
  const capabilities = Array.isArray(payload.capabilities)
    ? payload.capabilities
    : (Array.isArray(display.capabilities) ? display.capabilities : [])
  return {
    ...payload,
    ...display,
    sessionState: payload.sessionState || (payload.token ? 'AUTHENTICATED' : 'ANONYMOUS'),
    loginStatus: (payload.sessionState || (payload.token ? 'AUTHENTICATED' : 'ANONYMOUS')) === 'AUTHENTICATED',
    userType,
    bindingState: payload.bindingState || display.bindingState || 'UNVERIFIED',
    contactRole: payload.contactRole || display.contactRole || null,
    profile: {
      name: display.name || payload.name || '',
      maskedPhone: display.maskedPhone || payload.maskedPhone || '',
      avatarUrl: display.avatarUrl || payload.avatarUrl || ''
    },
    name: display.name || payload.name || '',
    maskedPhone: display.maskedPhone || payload.maskedPhone || '',
    avatarUrl: display.avatarUrl || payload.avatarUrl || '',
    tenantInfo: payload.tenantInfo || display.tenantInfo || null,
    staffInfo: payload.staffInfo || display.staffInfo || null,
    capabilities: Array.from(new Set(capabilities
      .map(item => String(item || '').trim())
      .filter(Boolean))),
    dataScope: payload.dataScope && typeof payload.dataScope === 'object'
      ? payload.dataScope
      : { type: String(payload.dataScope || display.dataScope || 'PUBLIC') },
    availableIdentities: Array.isArray(payload.availableIdentities)
      ? payload.availableIdentities
      : [],
    tenantBound: userType === 'TENANT',
    staffBound: userType === 'STAFF',
    role: userType === 'TENANT' ? 'tenant' : (userType === 'STAFF' ? 'staff' : 'guest')
  }
}

function syncGlobalData() {
  const app = appInstance()
  if (!app || !app.globalData) return
  app.globalData.token = wx.getStorageSync('token') || ''
  app.globalData.userInfo = getStoredProfile()
  app.globalData.identity = getIdentity()
}

function getStoredProfile() {
  const stored = wx.getStorageSync('userInfo')
  return stored && typeof stored === 'object' ? normalizeAuthority(stored) : null
}

function getIdentity() {
  const profile = getStoredProfile()
  return normalizeUserType((profile && profile.userType) || wx.getStorageSync('identity'))
}

function isManualLogout() {
  return wx.getStorageSync(MANUAL_LOGOUT_KEY) === true
}

function markManualLogout() {
  wx.setStorageSync(MANUAL_LOGOUT_KEY, true)
}

function clearManualLogout() {
  wx.removeStorageSync(MANUAL_LOGOUT_KEY)
}

function saveProfile(profile) {
  if (!profile || typeof profile !== 'object') {
    wx.removeStorageSync('userInfo')
    wx.setStorageSync('identity', 'PUBLIC')
    syncGlobalData()
    return null
  }
  const authority = normalizeAuthority(profile)
  wx.setStorageSync('userInfo', authority)
  wx.setStorageSync('identity', authority.userType)
  syncGlobalData()
  return authority
}

function failClosed() {
  return saveProfile({ ...PUBLIC_AUTHORITY })
}

function setSession(session, options = {}) {
  if (!session || typeof session !== 'object') return null
  sessionEpoch += 1
  if (options.preserveManualLogout !== true) clearManualLogout()
  if (Object.prototype.hasOwnProperty.call(session, 'token')) {
    if (session.token) wx.setStorageSync('token', session.token)
    else wx.removeStorageSync('token')
  }
  saveProfile(session)
  syncGlobalData()
  return session
}

function clearSession() {
  sessionEpoch += 1
  wx.removeStorageSync('token')
  wx.removeStorageSync('userInfo')
  wx.removeStorageSync('identity')
  syncGlobalData()
}

function loggedOutPublicSession() {
  return {
    token: '',
    identity: 'PUBLIC',
    ...PUBLIC_AUTHORITY,
    reused: true,
    loggedOut: true
  }
}

function loggedOutPublicMe() {
  return {
    ...PUBLIC_AUTHORITY,
    sessionState: 'ANONYMOUS'
  }
}

function getWechatCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(result) {
        if (result && result.code) resolve(result.code)
        else reject(new Error('微信登录失败'))
      },
      fail: reject
    })
  })
}

function storedSession() {
  return {
    token: wx.getStorageSync('token') || '',
    identity: getIdentity(),
    ...getStoredProfile(),
    reused: true
  }
}

function ensureWechatSession(options = {}) {
  const force = options.force === true
  const silent = options.silent !== false
  const userInitiated = options.userInitiated === true
  if (isManualLogout() && !userInitiated) {
    return Promise.resolve(loggedOutPublicSession())
  }
  const restoreBoundIdentity = options.restoreBoundIdentity !== false && !isManualLogout()
  const requestEpoch = sessionEpoch
  if (wechatSessionPromise) return wechatSessionPromise
  if (!force && wx.getStorageSync('token')) return Promise.resolve(storedSession())

  wechatSessionPromise = (async () => {
    const code = await getWechatCode()
    const session = await api.request({
      url: '/mobile/auth/wechat-login',
      method: 'POST',
      data: { code, restoreBoundIdentity },
      silent
    })
    if (!session || !session.token) throw new Error('微信登录未返回有效会话')
    const returnedIdentity = normalizeUserType(session.userType || session.identity)
    if (!restoreBoundIdentity && returnedIdentity !== 'PUBLIC') {
      const error = new Error('公开会话不能恢复业务身份')
      error.kind = 'INVALID_SESSION_SCOPE'
      throw error
    }
    if (requestEpoch !== sessionEpoch) {
      const error = new Error('会话已更新，请重新加载')
      error.kind = 'STALE_SESSION'
      error.staleSession = true
      throw error
    }
    setSession(session, { preserveManualLogout: !restoreBoundIdentity })
    return session
  })().finally(() => {
    wechatSessionPromise = null
  })
  return wechatSessionPromise
}

function loginWithWechat() {
  clearManualLogout()
  return ensureWechatSession({ force: true, silent: false, restoreBoundIdentity: true })
}

async function authorizeWechatPhone(phoneCode, options = {}) {
  if (!phoneCode) throw new Error('未取得微信手机号授权码')
  await ensureWechatSession({ silent: options.silent !== false, userInitiated: true })
  const login = () => api.request({
    url: '/mobile/auth/wechat-phone-login',
    method: 'POST',
    data: { phoneCode },
    silent: options.silent !== false
  })
  let session
  try {
    session = await login()
  } catch (error) {
    if (!isUnauthorized(error)) throw error
    clearSession()
    await ensureWechatSession({
      force: true,
      silent: options.silent !== false,
      userInitiated: true
    })
    session = await login()
  }
  if (session && session.token) setSession(session)
  return normalizeAuthority(session)
}

async function selectIdentity(candidateToken, options = {}) {
  if (!candidateToken) throw new Error('candidateToken不能为空')
  const session = await api.request({
    url: '/mobile/auth/select-identity',
    method: 'POST',
    data: { candidateToken },
    silent: options.silent !== false
  })
  if (session && session.token) setSession(session)
  return normalizeAuthority(session)
}

async function unbindCurrentIdentity(options = {}) {
  const session = await api.request({
    url: '/mobile/auth/unbind-current',
    method: 'POST',
    silent: options.silent !== false
  })
  if (session && session.token) setSession(session)
  return normalizeAuthority(session)
}

function isUnauthorized(error) {
  return !!(error && (
    error.kind === 'AUTH_REQUIRED' ||
    error.code === 401 ||
    error.statusCode === 401
  ))
}

function loadCurrentProfile(silent) {
  const requestToken = wx.getStorageSync('token') || ''
  const requestEpoch = sessionEpoch
  const context = `${requestEpoch}:${requestToken}`
  if (profileRefreshPromise && profileRefreshContext === context) return profileRefreshPromise

  const refreshPromise = (async () => {
    const me = await api.request({ url: '/mobile/me', method: 'GET', silent })
    const currentToken = wx.getStorageSync('token') || ''
    if (requestEpoch !== sessionEpoch || requestToken !== currentToken) {
      const error = new Error('会话已更新，请重新加载')
      error.kind = 'STALE_SESSION'
      error.staleSession = true
      throw error
    }
    saveProfile(me)
    return me
  })().finally(() => {
    if (profileRefreshPromise === refreshPromise) {
      profileRefreshPromise = null
      profileRefreshContext = null
    }
  })
  profileRefreshContext = context
  profileRefreshPromise = refreshPromise
  return refreshPromise
}

async function refreshCurrentProfile(options = {}) {
  const silent = options.silent !== false
  if (isManualLogout() && !wx.getStorageSync('token')) return loggedOutPublicMe()
  try {
    if (options.ensureSession !== false) await ensureWechatSession({ silent })
    else if (!wx.getStorageSync('token')) return loggedOutPublicMe()
    return await loadCurrentProfile(silent)
  } catch (error) {
    if (error && error.staleSession) throw error
    if (isUnauthorized(error)) wx.removeStorageSync('token')
    failClosed()
    throw error
  }
}

async function logout(options = {}) {
  const silent = options.silent !== false
  const token = wx.getStorageSync('token')
  let revoked = !token
  markManualLogout()
  clearSession()
  try {
    if (token) {
      await api.request({
        url: '/mobile/auth/logout',
        method: 'POST',
        header: { Authorization: token },
        silent
      })
      revoked = true
    }
  } catch (error) {
    revoked = false
  }
  return revoked
}

module.exports = {
  getIdentity,
  getStoredProfile,
  normalizeAuthority,
  saveProfile,
  setSession,
  clearSession,
  failClosed,
  isManualLogout,
  clearManualLogout,
  getWechatCode,
  loginWithWechat,
  authorizeWechatPhone,
  selectIdentity,
  unbindCurrentIdentity,
  ensureWechatSession,
  refreshCurrentProfile,
  logout
}
