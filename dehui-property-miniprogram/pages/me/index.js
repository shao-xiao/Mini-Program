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
    devForm: {
      phone: '13800000000',
      nickname: '德汇访客'
    },
    internalForm: {
      username: 'admin',
      password: ''
    },
    tenantForm: {
      tenantId: '1',
      name: '账单测试用户',
      phone: '13900000001',
      role: '财务联系人'
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
    fixtures: null,
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

  onDevInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`devForm.${field}`]: event.detail.value
    })
  },

  onInternalInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`internalForm.${field}`]: event.detail.value
    })
  },

  onTenantInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`tenantForm.${field}`]: event.detail.value
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
      apiMessage: '',
      fixtures: null
    })
    try {
      const health = await api.get('/ping')
      let fixtures = null
      try {
        fixtures = await api.request({
          url: '/mobile/dev/fixtures',
          method: 'GET',
          silent: true
        })
      } catch (error) {
        fixtures = null
      }
      this.setData({
        apiStatus: '可用',
        apiStatusClass: 'success',
        apiMessage: health && health.time ? `服务时间：${health.time.replace('T', ' ').slice(0, 19)}` : '后端已响应',
        fixtures
      })
      if (fixtures && fixtures.testTenantId) {
        this.setData({
          'tenantForm.tenantId': String(fixtures.testTenantId)
        })
      }
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
      const session = await api.post('/mobile/auth/dev-login', {
        phone: this.data.devForm.phone,
        nickname: this.data.devForm.nickname
      })
      setSession(session)
      this.setData({ profile: session.profile || {} })
      this.refreshIdentity()
      this.checkBackend()
      wx.showToast({ title: '登录成功', icon: 'success' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async bindInternal() {
    if (!wx.getStorageSync('token')) {
      wx.showToast({ title: '请先开发态登录', icon: 'none' })
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

  async bindTenant() {
    if (!wx.getStorageSync('token')) {
      wx.showToast({ title: '请先开发态登录', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    try {
      const session = await api.post('/mobile/auth/bind-tenant', {
        ...this.data.tenantForm,
        tenantId: Number(this.data.tenantForm.tenantId)
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
