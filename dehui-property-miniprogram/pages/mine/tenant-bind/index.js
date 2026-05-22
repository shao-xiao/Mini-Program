const api = require('../../../utils/request')
const { setSession, clearSession } = require('../../../utils/auth')

Page({
  data: {
    form: {
      phone: '',
      password: ''
    },
    submitting: false
  },

  onTenantInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`form.${field}`]: event.detail.value
    })
  },

  async ensureSession() {
    const token = wx.getStorageSync('token')
    if (token) {
      try {
        await api.request({
          url: '/mobile/auth/me',
          method: 'GET',
          silent: true
        })
        return
      } catch (error) {
        clearSession()
      }
    }

    const loginResult = await wx.login()
    const session = await api.post('/mobile/auth/wechat-login', {
      code: loginResult.code
    })
    setSession(session)
  },

  async bindTenantAccount() {
    if (!this.data.form.phone || !this.data.form.password) {
      wx.showToast({ title: '请填写手机号和密码', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      await this.ensureSession()
      const session = await api.post('/mobile/auth/bind-tenant', this.data.form)
      setSession(session)
      wx.showToast({ title: '租户绑定成功', icon: 'success' })
      wx.navigateBack()
    } catch (error) {
      wx.showToast({ title: error && error.message ? error.message : '绑定失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
