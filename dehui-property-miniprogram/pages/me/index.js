const api = require('../../utils/request')
const { clearSession, getIdentity, setSession } = require('../../utils/auth')

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
    }
  },

  onShow() {
    this.refreshIdentity()
    this.loadProfile()
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
