const api = require('../../../utils/request')
const { setSession, clearSession } = require('../../../utils/auth')

Page({
  data: {
    form: {
      username: '',
      password: ''
    },
    submitting: false
  },

  onInternalInput(event) {
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

  async bindInternal() {
    if (!this.data.form.username || !this.data.form.password) {
      wx.showToast({ title: '请填写内部账号和密码', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      await this.ensureSession()
      const session = await api.post('/mobile/auth/bind-internal', this.data.form)
      setSession(session)
      wx.showToast({ title: '内部员工绑定成功', icon: 'success' })
      wx.navigateBack()
    } catch (error) {
      wx.showToast({ title: error && error.message ? error.message : '绑定失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
