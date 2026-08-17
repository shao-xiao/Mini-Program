const api = require('../../../../utils/request')
const { requireStaffFeature, mergePage, compactQuery, message } = require('../../../../utils/staff')

Page({
  data: { records: [], page: 1, pageSize: 20, total: 0, hasMore: true, loading: false, status: '', error: '' },
  onLoad() { this.authorizeAndLoad() },
  onShow() { if (this._loaded) this.load(true) },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (this.data.hasMore && !this.data.loading) this.load(false) },
  async authorizeAndLoad() {
    try { await requireStaffFeature('staffMeeting'); await this.load(true); this._loaded = true } catch (error) {
      if (!error || error.kind !== 'FORBIDDEN') this.setData({ error: message(error) })
    }
  },
  async load(refresh) {
    if (this.data.loading) return
    this.setData({ loading: true, error: '' })
    try {
      const payload = await api.get('/mobile/meetings', compactQuery({
        page: refresh ? 1 : this.data.page + 1,
        pageSize: this.data.pageSize,
        status: this.data.status || undefined
      }), { silent: true })
      this.setData({ ...mergePage(this.data.records, payload, refresh), loading: false })
    } catch (error) { this.setData({ loading: false, error: message(error) }) }
  },
  filter(event) { this.setData({ status: event.currentTarget.dataset.status || '' }); this.load(true) },
  detail(event) { wx.navigateTo({ url: `/subPackages/staff/pages/meetings/detail?id=${event.currentTarget.dataset.id}` }) }
})
