const api = require('../../utils/request')

function initialLeadForm() {
  return {
    name: '',
    phone: '',
    companyName: '',
    desiredArea: '',
    intendedUse: '',
    preferredVisitTime: '',
    remark: ''
  }
}

Page({
  data: {
    loading: false,
    submitting: false,
    overview: null,
    leadForm: initialLeadForm()
  },

  onShow() {
    this.loadInvestment()
  },

  async loadInvestment() {
    this.setData({ loading: true })
    try {
      const overview = await api.get('/mobile/investment/overview')
      this.setData({ overview })
    } finally {
      this.setData({ loading: false })
    }
  },

  onLeadInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`leadForm.${field}`]: event.detail.value
    })
  },

  async submitLead() {
    const form = this.data.leadForm
    if (!form.name.trim()) {
      wx.showToast({ title: '请填写联系人', icon: 'none' })
      return
    }
    if (!form.phone.trim()) {
      wx.showToast({ title: '请填写联系电话', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      const result = await api.post('/mobile/investment/leads', {
        ...form,
        desiredArea: form.desiredArea ? Number(form.desiredArea) : null
      })
      wx.showModal({
        title: '已提交预约',
        content: result && result.message ? result.message : '招商顾问会尽快与您联系',
        showCancel: false
      })
      this.setData({
        leadForm: initialLeadForm()
      })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
