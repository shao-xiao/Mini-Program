const api = require('../../../../utils/request')
const { requireStaffFeature, mergePage, compactQuery, message } = require('../../../../utils/staff')

const STATUS_META = Object.freeze({
  PENDING: { text: '待审核', tone: 'pending' },
  REGISTERED: { text: '待核验', tone: 'ready' },
  ENTERED: { text: '已到访', tone: 'entered' },
  LEFT: { text: '已离场', tone: 'left' },
  REJECTED: { text: '已驳回', tone: 'rejected' },
  CANCELLED: { text: '已取消', tone: 'cancelled' },
  EXPIRED: { text: '已过期', tone: 'expired' }
})

function decorate(record) {
  const status = String(record.status || '')
  const meta = STATUS_META[status] || { text: status || '未知', tone: 'default' }
  return { ...record, statusText: meta.text, statusTone: meta.tone }
}

Page({
  data: {
    records: [],
    page: 1,
    pageSize: 20,
    total: 0,
    hasMore: true,
    loading: false,
    status: '',
    error: ''
  },
  onLoad() { this.authorizeAndLoad() },
  onShow() { if (this._loaded) this.load(true) },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()) },
  onReachBottom() { if (this.data.hasMore && !this.data.loading) this.load(false) },
  async authorizeAndLoad() {
    try {
      await requireStaffFeature('staffVisitor')
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
      const payload = await api.get('/mobile/staff/visitors', compactQuery({
        page: refresh ? 1 : this.data.page + 1,
        pageSize: this.data.pageSize,
        status: this.data.status || undefined
      }), { silent: true })
      const pageData = mergePage(this.data.records, payload, refresh)
      this.setData({ ...pageData, records: pageData.records.map(decorate), loading: false })
    } catch (error) {
      this.setData({ loading: false, error: message(error) })
    }
  },
  filter(event) {
    this.setData({ status: event.currentTarget.dataset.status || '' })
    this.load(true)
  },
  detail(event) {
    wx.navigateTo({ url: `/subPackages/staff/pages/visitors/detail?id=${event.currentTarget.dataset.id}` })
  }
})
