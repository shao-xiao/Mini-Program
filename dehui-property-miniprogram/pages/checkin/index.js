const api = require('../../utils/request')
const { formatDateTime } = require('../../utils/format')

function toInputDateTime(date = new Date()) {
  const next = new Date(date.getTime() + 60 * 60 * 1000)
  const year = next.getFullYear()
  const month = String(next.getMonth() + 1).padStart(2, '0')
  const day = String(next.getDate()).padStart(2, '0')
  const hour = String(next.getHours()).padStart(2, '0')
  const minute = String(next.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}`
}

function toApiDateTime(value) {
  return value ? `${value.replace(' ', 'T')}:00` : ''
}

Page({
  data: {
    loading: false,
    submitting: false,
    errorMessage: '',
    identityText: '',
    form: {
      checkinType: 'ON_DUTY',
      location: '',
      longitude: '',
      latitude: '',
      remark: ''
    },
    typeOptions: [
      { label: '上班打卡', value: 'ON_DUTY' },
      { label: '下班打卡', value: 'OFF_DUTY' }
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
        checkinTimeText: formatDateTime(item.checkinTime),
        checkinTypeText: this.toCheckinTypeText(item.checkinType),
        locationText: item.location || '未填写位置'
      }))
      this.setData({
        identityText: this.toIdentityText(data.profile),
        checkins
      })
    } catch (error) {
      this.setData({
        errorMessage: error && error.message ? error.message : '请先登录后再获取打卡记录',
        checkins: []
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: event.detail.value })
  },

  selectType(event) {
    this.setData({ 'form.checkinType': event.currentTarget.dataset.value })
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
        wx.showToast({ title: '定位失败，可手动输入位置', icon: 'none' })
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
        title: '提交成功',
        content: `记录ID：${created.id}\n时间：${formatDateTime(created.checkinTime)}`,
        showCancel: false
      })
      this.setData({
        form: {
          checkinType: 'ON_DUTY',
          location: '',
          longitude: '',
          latitude: '',
          remark: ''
        }
      })
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
    if (profile.userType === 'INTERNAL') return `内部人员：${profile.boundSysRealName || profile.boundSysUsername || profile.nickname || ''}`
    if (profile.userType === 'TENANT') return `租户：${profile.boundTenantName || profile.nickname || ''}`
    return `游客：${profile.nickname || ''}`
  },

  toCheckinTypeText(type) {
    if (type === 'ON_DUTY') return '上班'
    if (type === 'OFF_DUTY') return '下班'
    return type || '未知'
  }
})
