const api = require('../../../../utils/request')
const { requireStaffFeature, has, mergePage, compactQuery, message } = require('../../../../utils/staff')

const DEFAULT_RESULT_PRESENTATION = Object.freeze({
  label: '结果待确认',
  className: ''
})

const RESULT_PRESENTATIONS = Object.freeze({
  NORMAL: Object.freeze({ label: '正常', className: 'inspection-result--normal' }),
  '正常': Object.freeze({ label: '正常', className: 'inspection-result--normal' }),
  ABNORMAL: Object.freeze({ label: '异常', className: 'inspection-result--abnormal' }),
  '异常': Object.freeze({ label: '异常', className: 'inspection-result--abnormal' })
})

const STATUS_LABELS = {
  OPEN: '待处理',
  CLOSED: '已关闭'
}

function localizeInspectionPage(payload) {
  if (!payload || typeof payload !== 'object') return payload
  const localize = item => {
    const result = String(item && (item.inspectionResult || item.result) || '').trim()
    const resultPresentation = RESULT_PRESENTATIONS[result] || DEFAULT_RESULT_PRESENTATION
    const status = String(item && item.status || '')
    return {
      ...item,
      resultLabel: resultPresentation.label,
      resultClass: resultPresentation.className,
      statusLabel: STATUS_LABELS[status] || '状态待确认'
    }
  }
  if (Array.isArray(payload.records)) {
    return { ...payload, records: payload.records.map(localize) }
  }
  if (Array.isArray(payload.items)) {
    return { ...payload, items: payload.items.map(localize) }
  }
  return payload
}

Page({
  data: {
    records: [],
    page: 1,
    pageSize: 20,
    total: 0,
    hasMore: true,
    loading: false,
    error: '',
    status: '',
    canCreate: false
  },

  onLoad() {
    this.authorizeAndLoad()
  },

  onShow() {
    if (this._loaded) this.load(true)
  },

  onPullDownRefresh() {
    this.load(true).finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.load(false)
  },

  async authorizeAndLoad() {
    try {
      const profile = await requireStaffFeature('staffInspection')
      this.setData({ canCreate: has('inspection.staff.create', profile) })
      await this.load(true)
      this._loaded = true
    } catch (error) {
      if (!error || error.kind !== 'FORBIDDEN') this.setData({ error: message(error) })
    }
  },

  async load(refresh) {
    if (this.data.loading) return
    const page = refresh ? 1 : this.data.page + 1
    this.setData({ loading: true, error: '' })
    try {
      const payload = await api.get('/mobile/staff/inspections', compactQuery({
        page,
        pageSize: this.data.pageSize,
        status: this.data.status || undefined
      }), { silent: true })
      this.setData({
        ...mergePage(this.data.records, localizeInspectionPage(payload), refresh),
        loading: false
      })
    } catch (error) {
      this.setData({ loading: false, error: message(error) })
    }
  },

  filter(event) {
    this.setData({ status: event.currentTarget.dataset.status || '' })
    this.load(true)
  },

  create() {
    wx.navigateTo({ url: '/subPackages/staff/pages/inspections/form' })
  },

  detail(event) {
    wx.navigateTo({ url: `/subPackages/staff/pages/inspections/detail?id=${event.currentTarget.dataset.id}` })
  }
})
