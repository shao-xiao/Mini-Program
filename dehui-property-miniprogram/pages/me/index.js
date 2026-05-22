const api = require('../../utils/request')
const { clearSession, getIdentity, setSession } = require('../../utils/auth')
const {
  defaultBaseURL,
  lanBaseURL,
  getBaseURL,
  setBaseURL,
  resetBaseURL
} = require('../../config/env')

Page({
  data: {
    identityText: '访客',
    profile: {},
    loading: false,
    internalForm: {
      username: 'admin',
      password: ''
    },
    tenantAccountForm: {
      phone: '',
      password: ''
    },
    apiBaseURL: '',
    apiForm: {
      baseURL: ''
    },
    defaultBaseURL,
    lanBaseURL,
    apiStatus: '未检查',
    apiStatusClass: 'muted',
    apiMessage: '',
    checkingApi: false
  },

  onShow() {
    this.refreshIdentity()
    this.refreshApiConfig()
    this.loadProfile()
    this.checkBackend()
  },

  refreshIdentity() {
    const identityMap = {
      INTERNAL: '内部员工',
      TENANT: '租户用户',
      PUBLIC: '访客'
    }
    const identity = getIdentity()
    this.setData({
      identityText: identityMap[identity] || '访客'
    })
  },

  async loadProfile() {
    const token = wx.getStorageSync('token')
    if (!token) return

    try {
      const profile = await api.get('/mobile/auth/me')
      wx.setStorageSync('userInfo', profile)
      wx.setStorageSync('identity', profile.userType || 'PUBLIC')
      this.setData({ profile })
      this.refreshIdentity()
    } catch (error) {
      clearSession()
      this.setData({ profile: {} })
      this.refreshIdentity()
    }
  },

  onInternalInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`internalForm.${field}`]: event.detail.value
    })
  },

  onTenantAccountInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`tenantAccountForm.${field}`]: event.detail.value
    })
  },

  refreshApiConfig() {
    const apiBaseURL = getBaseURL()
    this.setData({
      apiBaseURL,
      'apiForm.baseURL': apiBaseURL
    })
  },

  onApiInput(event) {
    this.setData({
      'apiForm.baseURL': event.detail.value
    })
  },

  useDefaultApi() {
    const apiBaseURL = resetBaseURL()
    this.setData({
      apiBaseURL,
      'apiForm.baseURL': apiBaseURL
    })
    this.checkBackend()
  },

  useLanApi() {
    const apiBaseURL = setBaseURL(lanBaseURL)
    this.setData({
      apiBaseURL,
      'apiForm.baseURL': apiBaseURL
    })
    this.checkBackend()
  },

  saveApiBaseURL() {
    if (!this.data.apiForm.baseURL.trim()) {
      wx.showToast({ title: '请填写接口地址', icon: 'none' })
      return
    }
    const apiBaseURL = setBaseURL(this.data.apiForm.baseURL)
    this.setData({
      apiBaseURL,
      'apiForm.baseURL': apiBaseURL
    })
    wx.showToast({ title: '接口地址已保存', icon: 'success' })
    this.checkBackend()
  },

  async checkBackend() {
    this.setData({
      checkingApi: true,
      apiStatus: '检查中',
      apiStatusClass: 'muted',
      apiMessage: ''
    })
    try {
      const health = await api.get('/ping')
      this.setData({
        apiStatus: '可用',
        apiStatusClass: 'success',
        apiMessage: health && health.time ? `服务时间：${health.time.replace('T', ' ').slice(0, 19)}` : '后端已响应'
      })
    } catch (error) {
      this.setData({
        apiStatus: '不可用',
        apiStatusClass: 'danger',
        apiMessage: error && error.message ? error.message : '无法连接后端，请检查地址和服务状态'
      })
    } finally {
      this.setData({ checkingApi: false })
    }
  },

  async devLogin() {
    this.setData({ loading: true })
    try {
      const loginResult = await wx.login()
      const session = await api.post('/mobile/auth/wechat-login', {
        code: loginResult.code
      })
      setSession(session)
      this.setData({ profile: session.profile || {} })
      this.refreshIdentity()
      this.checkBackend()
      wx.showToast({ title: '微信登录成功', icon: 'success' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async bindInternal() {
    if (!wx.getStorageSync('token')) {
      wx.showToast({ title: '请先微信登录', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    try {
      const session = await api.post('/mobile/auth/bind-internal', this.data.internalForm)
      setSession(session)
      this.setData({ profile: session.profile || {} })
      this.refreshIdentity()
      this.checkBackend()
      wx.showToast({ title: '绑定成功', icon: 'success' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async bindTenantAccount() {
    if (!wx.getStorageSync('token')) {
      wx.showToast({ title: '请先微信登录', icon: 'none' })
      return
    }
    if (!this.data.tenantAccountForm.phone || !this.data.tenantAccountForm.password) {
      wx.showToast({ title: '请输入手机号和初始密码', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    try {
      const session = await api.post('/mobile/auth/bind-tenant', {
        phone: this.data.tenantAccountForm.phone,
        password: this.data.tenantAccountForm.password
      })
      setSession(session)
      this.setData({ profile: session.profile || {} })
      this.refreshIdentity()
      this.checkBackend()
      wx.showToast({ title: '绑定成功', icon: 'success' })
    } finally {
      this.setData({ loading: false })
    }
  },

  logout() {
    clearSession()
    this.setData({ profile: {} })
    this.refreshIdentity()
    wx.showToast({ title: '已清除', icon: 'success' })
  }
})
