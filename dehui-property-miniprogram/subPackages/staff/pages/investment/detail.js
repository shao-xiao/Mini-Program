const api = require('../../../../utils/request')
const { requireStaffFeature, has, message } = require('../../../../utils/staff')

Page({
  data: { id: 0, detail: null, canFollow: false, canConvert: false, content: '', submitting: false, error: '' },
  async onLoad(options) {
    this.setData({ id: Number(options.id || 0) })
    try {
      const profile = await requireStaffFeature('staffInvestment')
      this.setData({
        canFollow: has('investment.lead.staff.follow', profile),
        canConvert: has('investment.lead.staff.convert', profile)
      })
      await this.load()
    } catch (error) { this.setData({ error: message(error) }) }
  },
  async load() {
    const detail = await api.get(`/mobile/staff/investment-leads/${this.data.id}`, null, { silent: true })
    this.setData({ detail })
  },
  input(event) { this.setData({ content: event.detail.value }) },
  async follow() {
    if (!this.data.content.trim() || this.data.submitting) return
    this.setData({ submitting: true })
    try {
      await api.post(`/mobile/staff/investment-leads/${this.data.id}/follow`, { followType: 'PHONE', content: this.data.content })
      this.setData({ content: '' })
      await this.load()
    } catch (error) { wx.showToast({ title: message(error), icon: 'none' }) } finally {
      this.setData({ submitting: false })
    }
  },
  async status(event) {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    try {
      await api.post(`/mobile/staff/investment-leads/${this.data.id}/status`, { status: event.currentTarget.dataset.status })
      await this.load()
    } catch (error) { wx.showToast({ title: message(error), icon: 'none' }) } finally {
      this.setData({ submitting: false })
    }
  }
})
