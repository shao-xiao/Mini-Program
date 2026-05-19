const api = require('../../utils/request')
const { formatDateTime } = require('../../utils/format')

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

Page({
  data: {
    loading: false,
    submitting: false,
    errorMessage: '',
    identityText: '',
    form: {
      visitorName: '',
      visitorPhone: '',
      tenantId: '',
      visitedPerson: '',
      visitReason: '',
      visitTime: toInputDateTime(),
      carPlateNo: '',
      remark: ''
    },
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
        visitTimeText: formatDateTime(item.visitTime),
        leaveTimeText: formatDateTime(item.leaveTime),
        statusText: this.toStatusText(item.status),
        cancellable: item.status === 'REGISTERED',
        statusClass: this.toStatusClass(item.status)
      }))
      this.setData({
        identityText: this.toIdentityText(data.profile),
        visitors
      })
    } catch (error) {
      this.setData({
        errorMessage: error && error.message ? error.message : '请先登录后再获取访客记录',
        visitors: []
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: event.detail.value })
  },

  async submitVisitor() {
    const form = this.data.form
    if (!form.visitorName.trim()) {
      wx.showToast({ title: '请输入来访人姓名', icon: 'none' })
      return
    }
    if (!form.visitorPhone.trim()) {
      wx.showToast({ title: '请输入来访人电话', icon: 'none' })
      return
    }
    if (!form.visitedPerson.trim()) {
      wx.showToast({ title: '请输入接待人', icon: 'none' })
      return
    }
    if (!form.visitReason.trim()) {
      wx.showToast({ title: '请输入来访目的', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      const created = await api.post('/mobile/visitors', {
        visitorName: form.visitorName,
        visitorPhone: form.visitorPhone,
        tenantId: form.tenantId ? Number(form.tenantId) : null,
        visitedPerson: form.visitedPerson,
        visitReason: form.visitReason,
        visitTime: toApiDateTime(form.visitTime),
        carPlateNo: form.carPlateNo,
        remark: form.remark
      })
      wx.showModal({
        title: '提交成功',
        content: `来访记录ID：${created.id}`,
        showCancel: false
      })
      this.setData({
        form: {
          visitorName: '',
          visitorPhone: '',
          tenantId: '',
          visitedPerson: '',
          visitReason: '',
          visitTime: toInputDateTime(),
          carPlateNo: '',
          remark: ''
        }
      })
      this.loadVisitors()
    } finally {
      this.setData({ submitting: false })
    }
  },

  cancelVisitor(event) {
    const id = event.currentTarget.dataset.id
    const visitor = this.data.visitors.find(item => item.id === id)
    wx.showModal({
      title: '取消访客',
      content: visitor ? `确认取消 ${visitor.visitorName} 的来访记录？` : '确认取消该来访记录？',
      confirmText: '取消来访',
      confirmColor: '#d93025',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.post(`/mobile/visitors/${id}/cancel`)
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
    if (profile.userType === 'INTERNAL') return `内部人员：${profile.boundSysRealName || profile.boundSysUsername || profile.nickname || ''}`
    if (profile.userType === 'TENANT') return `租户：${profile.boundTenantName || profile.nickname || ''}`
    return `游客：${profile.nickname || ''}`
  },

  toStatusText(status) {
    if (status === 'REGISTERED') return '已登记'
    if (status === 'ENTERED') return '已进入'
    if (status === 'LEFT') return '已离场'
    if (status === 'CANCELLED') return '已取消'
    return status || '未到场'
  },

  toStatusClass(status) {
    if (status === 'CANCELLED') return 'cancelled'
    if (status === 'LEFT') return 'left'
    if (status === 'ENTERED') return 'entered'
    return 'registered'
  }
})
