const api = require('../../utils/request')

function pad(value) {
  return String(value).padStart(2, '0')
}

function toInputDateTime(date = new Date()) {
  const next = new Date(date.getTime() + 60 * 60 * 1000)
  return `${next.getFullYear()}-${pad(next.getMonth() + 1)}-${pad(next.getDate())} ${pad(next.getHours())}:${pad(next.getMinutes())}`
}

function toApiDateTime(value) {
  return value ? `${value.replace(' ', 'T')}:00` : ''
}

function toDateTimeText(value) {
  if (!value) return '-'
  const [date, time = ''] = value.replace('T', ' ').split(' ')
  const [year, month, day] = date.split('-')
  return `${year}年${month}月${day}日 ${time.slice(0, 5)}`
}

function initialForm() {
  return {
    visitorName: '',
    visitorPhone: '',
    tenantId: '',
    visitedPerson: '',
    visitReason: '',
    visitTime: toInputDateTime(),
    carPlateNo: '',
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
    visitors: []
  },

  onShow() {
    this.loadVisitors()
  },

  async loadVisitors() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const data = await api.get('/mobile/visitors')
      const visitors = (data.visitors || []).map(item => ({
        ...item,
        visitTimeText: toDateTimeText(item.visitTime),
        leaveTimeText: toDateTimeText(item.leaveTime),
        cancellable: item.status === 'REGISTERED',
        statusClass: this.toStatusClass(item.status)
      }))
      this.setData({
        identityText: this.toIdentityText(data.profile),
        visitors
      })
    } catch (error) {
      this.setData({
        errorMessage: error && error.message ? error.message : '请先登录后预约访客',
        visitors: []
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

  async submitVisitor() {
    const form = this.data.form
    if (!form.visitorName.trim()) {
      wx.showToast({ title: '请填写访客姓名', icon: 'none' })
      return
    }
    if (!form.visitorPhone.trim()) {
      wx.showToast({ title: '请填写访客手机号', icon: 'none' })
      return
    }
    if (!form.visitedPerson.trim()) {
      wx.showToast({ title: '请填写被访人', icon: 'none' })
      return
    }
    if (!form.visitReason.trim()) {
      wx.showToast({ title: '请填写来访事由', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      await api.post('/mobile/visitors', {
        visitorName: form.visitorName,
        visitorPhone: form.visitorPhone,
        tenantId: form.tenantId ? Number(form.tenantId) : null,
        visitedPerson: form.visitedPerson,
        visitReason: form.visitReason,
        visitTime: toApiDateTime(form.visitTime),
        carPlateNo: form.carPlateNo,
        remark: form.remark
      })
      wx.showToast({ title: '预约成功', icon: 'success' })
      this.setData({ form: initialForm() })
      this.loadVisitors()
    } finally {
      this.setData({ submitting: false })
    }
  },

  cancelVisitor(event) {
    const id = event.currentTarget.dataset.id
    const visitor = this.data.visitors.find(item => item.id === id)
    wx.showModal({
      title: '取消预约',
      content: visitor ? `确定取消“${visitor.visitorName}”的来访预约吗？` : '确定取消该预约吗？',
      confirmText: '取消预约',
      confirmColor: '#d93025',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.patch(`/mobile/visitors/${id}/cancel`)
          wx.showToast({ title: '已取消', icon: 'success' })
          this.loadVisitors()
        } catch (error) {
          // request.js already shows the backend message.
        }
      }
    })
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/me/index' })
  },

  toIdentityText(profile) {
    if (!profile) return ''
    if (profile.userType === 'INTERNAL') return `内部员工：${profile.boundSysRealName || profile.boundSysUsername || profile.nickname || ''}`
    if (profile.userType === 'TENANT') return `租户：${profile.boundTenantName || profile.nickname || ''}`
    return `访客：${profile.nickname || ''}`
  },

  toStatusClass(status) {
    if (status === 'CANCELLED') return 'cancelled'
    if (status === 'LEFT') return 'left'
    if (status === 'ENTERED') return 'entered'
    return 'registered'
  }
})
