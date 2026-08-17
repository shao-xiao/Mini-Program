const {
  getIdentity,
  getStoredProfile,
  refreshCurrentProfile
} = require('./utils/auth')
const { synchronizeRuntimeEnvironment } = require('./config/env')

App({
  globalData: {
    token: '',
    userInfo: null,
    identity: 'PUBLIC',
    sessionReady: null,
    sessionError: null
  },

  onLaunch() {
    synchronizeRuntimeEnvironment()
    this.restoreStoredSession()
    this.globalData.sessionReady = this.bootstrapSession()
  },

  restoreStoredSession() {
    this.globalData.token = wx.getStorageSync('token') || ''
    this.globalData.userInfo = getStoredProfile()
    this.globalData.identity = getIdentity()
  },

  async bootstrapSession() {
    try {
      const me = await refreshCurrentProfile({
        ensureSession: true,
        retryAsPublic: true,
        silent: true
      })
      this.restoreStoredSession()
      this.globalData.sessionError = null
      return me
    } catch (error) {
      this.restoreStoredSession()
      this.globalData.sessionError = error
      return null
    }
  }
})
