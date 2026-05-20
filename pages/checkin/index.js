const api = require('../../utils/request')

function toDateTimeText(value) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function initialForm() {
  return {
    checkinType: 'ON_DUTY',
    location: '',
    longitude: '',
    latitude: '',
    remark: ''
  }
}

Page({
  data: {
    loading: false,
    submitting: false,
    errorMessage: '',
    identityText: '',
    form: initialForm(),
    typeOptions: [
      { label: '上班签到', value: 'ON_DUTY' },
      { label: '下班签退', value: 'OFF_DUTY' }
    ],
    checkins: []
  },

  onShow() {
    this.loadCheckins()
  },

  async loadCheckins() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const data = await api.get('/mobile/checkins')
      const checkins = (data.checkins || []).map(item => ({
        ...item,
        checkinTimeText: toDateTimeText(item.checkinTime),
        locationText: item.location || '未填写地点'
      }))
      this.setData({
        identityText: this.toIdentityText(data.profile),
        checkins
      })
    } catch (error) {
      this.setData({
        errorMessage: error && error.message ? error.message : '请先登录并绑定内部员工身份后签到',
        checkins: []
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`form.${field}`]: event.detail.value
    })
  },

  selectType(event) {
    this.setData({
      'form.checkinType': event.currentTarget.dataset.value
    })
  },

  getLocation() {
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        this.setData({
          'form.longitude': String(res.longitude),
          'form.latitude': String(res.latitude),
          'form.location': this.data.form.location || '当前位置'
        })
      },
      fail: () => {
        wx.showToast({ title: '定位失败，可手动填写地点', icon: 'none' })
      }
    })
  },

  async submitCheckin() {
    const form = this.data.form
    this.setData({ submitting: true })
    try {
      const created = await api.post('/mobile/checkins', {
        checkinType: form.checkinType,
        location: form.location,
        longitude: form.longitude ? Number(form.longitude) : null,
        latitude: form.latitude ? Number(form.latitude) : null,
        remark: form.remark
      })
      wx.showModal({
        title: '签到成功',
        content: `记录ID：${created.id}\n时间：${toDateTimeText(created.checkinTime)}`,
        showCancel: false
      })
      this.setData({ form: initialForm() })
      this.loadCheckins()
    } finally {
      this.setData({ submitting: false })
    }
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/me/index' })
  },

  toIdentityText(profile) {
    if (!profile) return ''
    if (profile.userType === 'INTERNAL') return `内部员工：${profile.boundSysRealName || profile.boundSysUsername || profile.nickname || ''}`
    if (profile.userType === 'TENANT') return `租户：${profile.boundTenantName || profile.nickname || ''}`
    return `访客：${profile.nickname || ''}`
  }
})
