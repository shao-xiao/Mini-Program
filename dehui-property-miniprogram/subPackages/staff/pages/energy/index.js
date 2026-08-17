const api = require('../../../../utils/request')
const { requireStaffFeature, has, mergePage, message } = require('../../../../utils/staff')
const {
  presentEnergySummary,
  presentEnergyReading
} = require('../../../../utils/energyPresentation')

Page({
  data: {
    periodMonth: currentMonth(),
    summary: { byType: [], byTenant: [] },
    records: [],
    page: 1,
    pageSize: 20,
    total: 0,
    hasMore: true,
    loading: false,
    error: '',
    canCreate: false
  },
  onLoad() { this.authorizeAndLoad() },
  onShow() { if (this._loaded) this.load(true) },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (this.data.hasMore && !this.data.loading) this.load(false) },
  async authorizeAndLoad() {
    try {
      const profile = await requireStaffFeature('staffEnergy')
      this.setData({ canCreate: has('energy.staff.reading.create', profile) })
      await this.load(true)
      this._loaded = true
    } catch (error) {
      if (!error || error.kind !== 'FORBIDDEN') this.setData({ error: message(error) })
    }
  },
  async load(refresh) {
    if (this.data.loading) return
    this.setData({ loading: true, error: '' })
    try {
      const page = refresh ? 1 : this.data.page + 1
      const [summary, payload] = await Promise.all([
        refresh
          ? api.get('/mobile/staff/energy/summary', { periodMonth: this.data.periodMonth }, { silent: true })
          : Promise.resolve(this.data.summary),
        api.get('/mobile/staff/energy/readings', {
          periodMonth: this.data.periodMonth,
          page,
          pageSize: this.data.pageSize
        }, { silent: true })
      ])
      const pageState = mergePage(this.data.records, payload, refresh)
      this.setData({
        summary: refresh ? presentEnergySummary(summary) : this.data.summary,
        ...pageState,
        records: pageState.records.map(presentEnergyReading),
        loading: false
      })
    } catch (error) {
      this.setData({ loading: false, error: message(error) })
    }
  },
  changeMonth(event) { this.setData({ periodMonth: event.detail.value }); this.load(true) },
  retry() { this.load(true) },
  create() { wx.navigateTo({ url: '/subPackages/staff/pages/energy/reading-form' }) }
})

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}
