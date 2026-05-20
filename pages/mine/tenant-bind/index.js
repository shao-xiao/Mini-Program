const api = require('../../../utils/request')
const { setSession } = require('../../../utils/auth')

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
    if (wx.getStorageSync('token')) return

    const phone = this.data.form.phone || '13800000000'
    const session = await api.post('/mobile/auth/dev-login', {
      phone,
      nickname: '德汇访客'
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

