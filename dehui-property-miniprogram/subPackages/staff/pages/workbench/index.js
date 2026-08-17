const api = require('../../../../utils/request')
const { requireStaffFeature, staffModules, message } = require('../../../../utils/staff')

Page({
  data: {
    loading: true,
    error: '',
    profile: null,
    modules: [],
    summary: {}
  },

  onLoad() {
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const profile = await requireStaffFeature('staffWorkbench')
      const data = await api.get('/mobile/staff/workbench', null, { silent: true })
      const summary = data && data.modules ? data.modules : {}
      const modules = staffModules(profile).map(item => ({
        ...item,
        summary: summary[moduleKey(item.key)] || {}
      }))
      this.setData({ profile, modules, summary, loading: false })
    } catch (error) {
      if (error && error.kind === 'FORBIDDEN') return
      this.setData({ loading: false, error: message(error) })
    }
  },

  open(event) {
    const item = this.data.modules.find(module => module.key === event.currentTarget.dataset.key)
    if (item && item.url) wx.navigateTo({ url: item.url })
  }
})

function moduleKey(key) {
  return {
    staffInspection: 'inspection',
    staffWorkorder: 'workorder',
    staffEnergy: 'energy',
    staffMeeting: 'meeting',
    staffVisitor: 'visitor',
    staffInvestment: 'investmentLead'
  }[key]
}
