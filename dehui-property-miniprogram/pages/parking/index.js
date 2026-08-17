const api = require('../../utils/request')
const { refreshCurrentProfile } = require('../../utils/auth')
const { canAccessFeature } = require('../../utils/capabilities')

function normalize(row) {
  return {
    ...row,
    priceText: Number(row.unitPriceSnapshot || 0).toFixed(2),
    periodText: `${row.effectiveFrom || '-'} 至 ${row.effectiveTo || '长期有效'}`
  }
}

function normalizeSummary(summary = {}) {
  return {
    totalCount: Number(summary.totalCount || 0),
    fixedCount: Number(summary.fixedCount || 0),
    unboundCount: Number(summary.unboundCount || 0)
  }
}

Page({
  data: {
    state: 'loading',
    message: '',
    scope: 'mine',
    title: '我的车位',
    rows: [],
    page: 1,
    pageSize: 20,
    total: 0,
    summary: normalizeSummary(),
    noMore: false,
    loadingMore: false
  },

  onLoad(options = {}) {
    this._active = true
    this._sequence = 0
    const scope = options.scope === 'company' ? 'company' : 'mine'
    this.setData({ scope, title: scope === 'company' ? '企业车位' : '我的车位' })
    wx.setNavigationBarTitle({ title: scope === 'company' ? '企业车位' : '我的车位' })
  },

  onShow() {
    this._active = true
    this.load()
  },

  onUnload() {
    this._active = false
    this._sequence += 1
  },

  safeSetData(value) {
    if (this._active) this.setData(value)
  },

  async load(append = false) {
    if (append && (this.data.loadingMore || this.data.noMore)) return
    const requestId = ++this._sequence
    this.safeSetData(append ? { loadingMore: true } : { state: 'loading', message: '' })
    try {
      const profile = await refreshCurrentProfile()
      const feature = this.data.scope === 'company' ? 'parkingCompany' : 'parkingMine'
      if (!canAccessFeature(feature, profile)) {
        this.safeSetData({ state: 'unauthorized', message: '当前身份没有该停车数据查看能力。' })
        return
      }
      const page = append ? this.data.page + 1 : 1
      const data = await api.get(`/mobile/parking/${this.data.scope}`, { page, pageSize: this.data.pageSize })
      if (!this._active || requestId !== this._sequence) return
      const incoming = (data.records || data.parking || []).map(normalize)
      const rows = append ? this.data.rows.concat(incoming) : incoming
      this.safeSetData({
        state: rows.length ? 'success' : 'empty',
        rows,
        summary: this.data.scope === 'company' ? normalizeSummary(data.summary) : normalizeSummary(),
        loadingMore: false,
        page: Number(data.page || page),
        total: Number(data.total || 0),
        noMore: rows.length >= Number(data.total || 0)
      })
    } catch (error) {
      if (!this._active || requestId !== this._sequence) return
      const code = Number(error && (error.code || error.statusCode))
      this.safeSetData({
        state: code === 403 ? 'unauthorized' : 'error',
        loadingMore: false,
        message: error && error.message ? error.message : '停车数据加载失败，请稍后重试。'
      })
    }
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/business-detail/index?type=parking&id=${event.currentTarget.dataset.id}` })
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.load(true)
  }
})
