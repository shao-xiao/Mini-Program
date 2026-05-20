const api = require('../../../utils/request')
const { setSession } = require('../../../utils/auth')

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
    if (wx.getStorageSync('token')) return

    const session = await api.post('/mobile/auth/dev-login', {
      phone: '13800000000',
      nickname: '德汇访客'
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

