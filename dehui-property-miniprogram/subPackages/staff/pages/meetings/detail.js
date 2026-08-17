const api = require('../../../../utils/request')
const { requireStaffFeature, has, message } = require('../../../../utils/staff')

Page({
  data: { id: 0, detail: null, capabilities: {}, submitting: false, error: '' },
  async onLoad(options) {
    this.setData({ id: Number(options.id || 0) })
    try {
      const profile = await requireStaffFeature('staffMeeting')
      this.setData({ capabilities: {
        confirm: has('meeting.staff.confirm', profile),
        cancel: has('meeting.staff.cancel', profile),
        complete: has('meeting.staff.complete', profile)
      } })
      await this.load()
    } catch (error) { this.setData({ error: message(error) }) }
  },
  async load() {
    const detail = await api.get(`/mobile/staff/meetings/${this.data.id}`, null, { silent: true })
    this.setData({ detail })
  },
  async change(event) {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    try {
      await api.post(`/mobile/staff/meetings/${this.data.id}/status`, { status: event.currentTarget.dataset.status })
      await this.load()
    } catch (error) { wx.showToast({ title: message(error), icon: 'none' }) } finally {
      this.setData({ submitting: false })
    }
  }
})
