const api = require('../../utils/request')
const { refreshCurrentProfile } = require('../../utils/auth')
const { canAccessFeature } = require('../../utils/capabilities')

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function meterTypeText(value) {
  return {
    WATER: '水',
    ELECTRIC: '电',
    ELECTRICITY: '电',
    GAS: '燃气',
    OTHER: '其他'
  }[String(value || '').toUpperCase()] || value || '其他'
}

function normalizeRow(row) {
  return {
    ...row,
    meterTypeText: meterTypeText(row.meterType),
    meterTypeClass: String(row.meterType || 'OTHER').toLowerCase(),
    usageText: Number(row.usage || 0).toFixed(2),
    amountText: Number(row.amount || 0).toFixed(2)
  }
}

Page({
  data: {
    state: 'loading',
    message: '',
    periodMonth: currentMonth(),
    dataScope: '',
    summary: [],
    history: [],
    readings: [],
    page: 1,
    pageSize: 20,
    historyTotal: 0,
    readingsTotal: 0,
    noMore: false,
    loadingMore: false
  },

  onLoad() {
    this._active = true
    this._sequence = 0
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
      if (!canAccessFeature('energy', profile)) {
        this.safeSetData({ state: 'unauthorized', message: '当前身份没有企业能耗查看能力。' })
        return
      }
      const page = append ? this.data.page + 1 : 1
      const [summary, history, readings] = await Promise.all([
        append ? Promise.resolve({ dataScope: this.data.dataScope, types: this.data.summary }) : api.get('/mobile/energy/summary', { periodMonth: this.data.periodMonth }),
        api.get('/mobile/energy/history', { page, pageSize: this.data.pageSize }),
        api.get('/mobile/energy/readings', { page, pageSize: this.data.pageSize, periodMonth: this.data.periodMonth })
      ])
      if (!this._active || requestId !== this._sequence) return
      const incomingHistory = (history.records || history.history || []).map(normalizeRow)
      const incomingReadings = (readings.records || []).map(normalizeRow)
      const nextHistory = append ? this.data.history.concat(incomingHistory) : incomingHistory
      const nextReadings = append ? this.data.readings.concat(incomingReadings) : incomingReadings
      const historyTotal = Number(history.total || 0)
      const readingsTotal = Number(readings.total || 0)
      this.safeSetData({
        state: 'success',
        loadingMore: false,
        dataScope: summary.dataScope || '',
        summary: append ? this.data.summary : (summary.types || []).map(normalizeRow),
        history: nextHistory,
        readings: nextReadings,
        page,
        historyTotal,
        readingsTotal,
        noMore: nextHistory.length >= historyTotal && nextReadings.length >= readingsTotal
      })
    } catch (error) {
      if (!this._active || requestId !== this._sequence) return
      const code = Number(error && (error.code || error.statusCode))
      this.safeSetData({
        state: code === 403 ? 'unauthorized' : 'error',
        loadingMore: false,
        message: error && error.message ? error.message : '能耗数据加载失败，请稍后重试。'
      })
    }
  },

  onMonthChange(event) {
    this.safeSetData({ periodMonth: event.detail.value })
    this.load()
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/business-detail/index?type=energy&id=${event.currentTarget.dataset.id}` })
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.load(true)
  }
})
