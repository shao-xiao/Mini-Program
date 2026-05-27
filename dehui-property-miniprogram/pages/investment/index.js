const api = require('../../utils/request')

const sectionKeys = [
  'hero',
  'highlight',
  'policy',
  'introduction',
  'location',
  'contact',
  'notice'
]

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

function emptySections() {
  return sectionKeys.reduce((result, key) => {
    result[key] = []
    return result
  }, {})
}

function normalizeSections(data) {
  const sections = emptySections()
  sectionKeys.forEach((key) => {
    const rows = Array.isArray(data && data[key]) ? data[key] : []
    sections[key] = rows
      .map((item) => ({
        title: item.title || '',
        subtitle: item.subtitle || '',
        content: item.content || '',
        imageUrl: item.imageUrl || '',
        sortOrder: Number(item.sortOrder || 0)
      }))
      .sort((a, b) => a.sortOrder - b.sortOrder)
  })
  return sections
}

Page({
  data: {
    loading: false,
    submitting: false,
    sections: emptySections(),
    hero: null,
    leadForm: initialLeadForm()
  },

  onShow() {
    this.loadInvestment()
  },

  async loadInvestment() {
    this.setData({ loading: true })
    try {
      const data = await api.get('/mobile/investment/overview')
      const sections = normalizeSections(data)
      this.setData({
        sections,
        hero: sections.hero[0] || null
      })
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
        content: result && result.id ? `线索ID：${result.id}\n${result.message || '招商顾问会尽快与您联系'}` : '招商顾问会尽快与您联系',
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
