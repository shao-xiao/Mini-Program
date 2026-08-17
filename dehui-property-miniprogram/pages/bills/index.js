const api = require('../../utils/request')
const { refreshCurrentProfile } = require('../../utils/auth')
const { canAccessFeature, getFeatureAccessState } = require('../../utils/capabilities')

const FEATURE_KEY = 'bill'

function hasValue(value) {
  return value !== null && value !== undefined && value !== ''
}

function formatMoney(value) {
  return Number(value || 0).toFixed(2)
}

function formatDate(value) {
  if (!value) return '-'
  const datePart = String(value).split('T')[0]
  const [year, month, day] = datePart.split('-')
  return year && month && day ? `${year}年${month}月${day}日` : value
}

function formatDateTime(value) {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  const [datePart, timePart = ''] = text.split(' ')
  const dateText = formatDate(datePart)
  return timePart ? `${dateText} ${timePart.slice(0, 5)}` : dateText
}

function fileType(filename) {
  const suffix = String(filename || '').split('.').pop().toLowerCase()
  return ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(suffix) ? suffix : 'pdf'
}

function emptySummary() {
  return {
    totalAmount: '0.00',
    unpaidAmount: '0.00',
    overdueAmount: '0.00',
    paidAmount: '0.00',
    totalCount: 0,
    unpaidCount: 0,
    overdueCount: 0,
    paidCount: 0
  }
}

function firstValue(item, fields) {
  for (const field of fields) {
    if (hasValue(item[field])) return item[field]
  }
  return ''
}

function normalizeCode(value) {
  return String(value || '').trim().toUpperCase()
}

function billStatusText(status, returnedText) {
  if (returnedText && returnedText !== status) return returnedText
  return {
    UNPAID: '待缴',
    PAID: '已缴',
    OVERDUE: '已逾期',
    CANCELLED: '已取消'
  }[status] || status || '未知'
}

function billCategory(item) {
  const code = [
    item.billType,
    item.billTypeCode,
    item.sourceType,
    item.sourceTypeCode
  ].map(normalizeCode).join('_')

  if (/ENERGY|ELECTRIC|WATER|GAS|UTILITY/.test(code)) return 'energy'
  if (/PARKING/.test(code)) return 'parking'
  if (/RENT|PROPERTY|MANAGEMENT|LEASE/.test(code)) return 'rent'
  return 'other'
}

function billCategoryLabel(category) {
  return {
    rent: '租金物业',
    energy: '水电能耗',
    parking: '停车服务',
    other: '其他服务'
  }[category] || '其他服务'
}

function billPeriodText(item) {
  const billingPeriod = String(item.billingPeriod || '').trim()
  const match = billingPeriod.match(/^(\d{4})-(\d{2})$/)
  if (match) return `${match[1]}年${match[2]}月`
  if (billingPeriod) return billingPeriod
  if (item.periodStart && item.periodEnd) {
    return `${formatDate(item.periodStart)} 至 ${formatDate(item.periodEnd)}`
  }
  if (item.periodStart) return formatDate(item.periodStart)
  return ''
}

function normalizeInitialType(value) {
  return ['energy', 'rent', 'parking'].includes(value) ? value : 'all'
}

function requestFailureState(error) {
  const code = Number(error && (error.code || error.statusCode || error.status))
  if (code === 401) {
    return {
      pageState: 'login-required',
      errorMessage: '登录状态已失效，请重新登录后查看账单。'
    }
  }
  if (code === 403) {
    return {
      pageState: 'unauthorized',
      errorMessage: '当前账号没有租户账单查看资格，请联系物业确认租户联系人状态。'
    }
  }
  return {
    pageState: 'error',
    errorMessage: error && error.message ? error.message : '账单加载失败，请稍后重试。'
  }
}

function deniedState(profile) {
  const result = getFeatureAccessState(FEATURE_KEY, profile)
  const state = typeof result === 'string' ? result : result && (result.state || result.reason)
  if (state === 'login-required' || (result && result.loginRequired)) {
    return {
      pageState: 'login-required',
      errorMessage: '请先登录租户账号，再查看账单。'
    }
  }
  return {
    pageState: 'unauthorized',
    errorMessage: '账单仅向已匹配的有效租户联系人开放，当前账号暂无查看资格。'
  }
}

Page({
  data: {
    pageState: 'loading',
    loading: true,
    errorMessage: '',
    activeType: 'all',
    activeTypeIndex: 0,
    activeTypeLabel: '全部类型',
    activeStatus: '',
    typeFilters: [
      { label: '全部类型', value: 'all' },
      { label: '租金/物业', value: 'rent' },
      { label: '水电能耗', value: 'energy' },
      { label: '停车', value: 'parking' },
      { label: '其他服务', value: 'other' }
    ],
    statusFilters: [
      { label: '全部', value: '' },
      { label: '待缴', value: 'UNPAID' },
      { label: '已结清', value: 'PAID' },
      { label: '逾期', value: 'OVERDUE' }
    ],
    tenantName: '',
    summary: emptySummary(),
    bills: [],
    page: 1,
    pageSize: 20,
    total: 0,
    noMore: false,
    loadingMore: false,
    downloadingBillId: null
  },

  onLoad(options = {}) {
    this._active = true
    this._loadSequence = 0
    const activeType = normalizeInitialType(options.type)
    const activeTypeIndex = this.data.typeFilters.findIndex(item => item.value === activeType)
    this.setData({
      activeType,
      activeTypeIndex: activeTypeIndex < 0 ? 0 : activeTypeIndex,
      activeTypeLabel: this.data.typeFilters[activeTypeIndex < 0 ? 0 : activeTypeIndex].label
    })
  },

  onShow() {
    this._active = true
    this.loadBills()
  },

  onUnload() {
    this._active = false
    this._loadSequence = Number(this._loadSequence || 0) + 1
  },

  safeSetData(payload, callback) {
    if (!this._active) return
    this.setData(payload, callback)
  },

  isCurrentRequest(requestId) {
    return this._active && requestId === this._loadSequence
  },

  async loadBills(append = false) {
    if (append && (this.data.loadingMore || this.data.noMore)) return
    const requestId = Number(this._loadSequence || 0) + 1
    this._loadSequence = requestId
    this.safeSetData(append ? { loadingMore: true } : {
      pageState: 'loading',
      loading: true,
      errorMessage: '',
      tenantName: '',
      summary: emptySummary(),
      bills: []
    })

    try {
      const profile = await refreshCurrentProfile()
      if (!this.isCurrentRequest(requestId)) return
      if (!canAccessFeature(FEATURE_KEY, profile)) {
        this.safeSetData({
          ...deniedState(profile),
          loading: false
        })
        return
      }

      const page = append ? this.data.page + 1 : 1
      const query = { page, pageSize: this.data.pageSize }
      if (this.data.activeStatus) query.status = this.data.activeStatus
      if (this.data.activeType !== 'all') query.category = this.data.activeType
      const data = await api.get('/mobile/bills', query)
      if (!this.isCurrentRequest(requestId)) return
      const summary = data.summary || {}
      const incoming = (data.records || data.bills || []).map(item => {
        const status = item.status || item.payStatus || ''
        const category = billCategory(item)
        const categoryLabel = billCategoryLabel(category)
        const previousReading = firstValue(item, ['previousReading', 'lastReading', 'previousMeterReading'])
        const currentReading = firstValue(item, ['currentReading', 'currentMeterReading'])
        const usage = firstValue(item, ['usage', 'consumption', 'currentUsage'])
        const unitPrice = firstValue(item, ['unitPrice', 'pricePerUnit'])
        const meterReadingDate = firstValue(item, ['meterReadingDate', 'readingDate'])
        const finalAmount = hasValue(item.finalAmount) ? item.finalAmount : item.amount
        const paidAmount = hasValue(item.paidAmount) ? item.paidAmount : 0
        const unpaidAmount = hasValue(item.unpaidAmount)
          ? item.unpaidAmount
          : Math.max(Number(finalAmount || 0) - Number(paidAmount || 0), 0)
        const isPaid = status === 'PAID'
        const isCancelled = status === 'CANCELLED'
        const isOverdue = Boolean(item.overdue && !isPaid && !isCancelled)
        const invoiceStatus = normalizeCode(item.invoiceStatus)
        const hasInvoice = !['', 'NONE', 'NOT_INVOICED'].includes(invoiceStatus)
        return {
          ...item,
          status,
          category,
          categoryLabel,
          displayTitle: item.title || `${categoryLabel}账单`,
          amountText: formatMoney(finalAmount),
          calculatedAmountText: formatMoney(item.calculatedTotal || item.amount),
          adjustmentAmountText: formatMoney(item.adjustmentTotal),
          paidAmountText: formatMoney(paidAmount),
          unpaidAmountText: formatMoney(unpaidAmount),
          primaryAmountLabel: isPaid ? '已缴金额' : (isCancelled ? '账单金额' : '待缴金额'),
          primaryAmountText: formatMoney(isPaid ? paidAmount : (isCancelled ? finalAmount : unpaidAmount)),
          periodText: billPeriodText(item),
          hasPeriod: Boolean(billPeriodText(item)),
          dueDateText: formatDate(item.dueDate),
          paidAtText: formatDateTime(item.paidAt),
          statusDisplay: isOverdue
            ? '已逾期'
            : billStatusText(status, item.statusText),
          statusClass: isPaid
            ? 'paid'
            : (isCancelled ? 'cancelled' : (isOverdue ? 'overdue' : 'unpaid')),
          hasInvoiceFile: Boolean(item.hasInvoiceFile && item.invoiceDownloadUrl),
          hasInvoice,
          invoiceStatusText: item.hasInvoiceFile
            ? '电子发票可下载'
            : (hasInvoice ? '发票资料处理中' : '暂未开票'),
          invoiceDownloadUrl: item.invoiceDownloadUrl || '',
          invoiceFileName: item.invoiceFileName || `${item.billNumber || 'invoice'}.pdf`,
          hasAdjustment: Number(item.adjustmentTotal || 0) !== 0,
          overdueDays: Number(item.overdueDays || 0),
          hasPreviousReading: hasValue(previousReading),
          previousReadingText: hasValue(previousReading) ? String(previousReading) : '',
          hasCurrentReading: hasValue(currentReading),
          currentReadingText: hasValue(currentReading) ? String(currentReading) : '',
          hasUsage: hasValue(usage),
          usageText: hasValue(usage) ? String(usage) : '',
          hasUnitPrice: hasValue(unitPrice),
          unitPriceText: hasValue(unitPrice) ? formatMoney(unitPrice) : '',
          hasMeterReadingDate: hasValue(meterReadingDate),
          meterReadingDateText: hasValue(meterReadingDate) ? formatDate(meterReadingDate) : '',
          hasEnergyDetail: category === 'energy' && [
            previousReading,
            currentReading,
            usage,
            unitPrice,
            meterReadingDate
          ].some(hasValue)
        }
      })

      const bills = append ? this.data.bills.concat(incoming) : incoming
      this.safeSetData({
        pageState: bills.length ? 'success' : 'empty',
        loading: false,
        loadingMore: false,
        page: Number(data.page || page),
        total: Number(data.total || 0),
        noMore: bills.length >= Number(data.total || 0),
        tenantName: (data.profile && data.profile.tenantInfo && data.profile.tenantInfo.name)
          || (profile.tenantInfo && profile.tenantInfo.name)
          || (data.profile && data.profile.boundTenantName)
          || profile.boundTenantName
          || '',
        summary: {
          totalAmount: formatMoney(summary.totalAmount),
          unpaidAmount: formatMoney(summary.unpaidAmount),
          overdueAmount: formatMoney(summary.overdueAmount),
          paidAmount: formatMoney(summary.paidAmount),
          totalCount: Number(summary.totalCount || 0),
          unpaidCount: Number(summary.unpaidCount || 0),
          overdueCount: Number(summary.overdueCount || 0),
          paidCount: Number(summary.paidCount || 0)
        },
        bills
      })
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({
        ...requestFailureState(error),
        loading: false,
        loadingMore: false,
        tenantName: '',
        summary: emptySummary(),
        bills: []
      })
    }
  },

  changeType(event) {
    const activeTypeIndex = Number(event.detail.value)
    const option = this.data.typeFilters[activeTypeIndex] || this.data.typeFilters[0]
    this.safeSetData({
      activeType: option.value,
      activeTypeIndex,
      activeTypeLabel: option.label
    }, () => this.loadBills())
  },

  changeStatus(event) {
    const activeStatus = event.currentTarget.dataset.value
    if (activeStatus === this.data.activeStatus) return
    this.safeSetData({ activeStatus }, () => this.loadBills())
  },

  retryLoad() {
    this.loadBills()
  },

  onPullDownRefresh() {
    this.loadBills().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadBills(true)
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/business-detail/index?type=bill&id=${event.currentTarget.dataset.id}` })
  },

  handleAccessError(error) {
    const state = requestFailureState(error)
    if (state.pageState === 'error') return false
    this.safeSetData({
      ...state,
      loading: false,
      tenantName: '',
      summary: emptySummary(),
      bills: []
    })
    return true
  },

  showPaymentGuide(event) {
    const { number, title, amount } = event.currentTarget.dataset
    wx.showModal({
      title: '线下缴费说明',
      content: `${title || '账单'}\n编号：${number || '-'}\n待缴金额：¥${amount || '0.00'}\n\n当前暂未接入微信支付，请按物业通知线下缴费，后台确认收款后此处会更新为已缴。`,
      showCancel: false,
      confirmText: '知道了'
    })
  },

  async downloadInvoice(event) {
    const { id, url, filename } = event.currentTarget.dataset
    if (!url) {
      wx.showToast({ title: '暂无可下载发票', icon: 'none' })
      return
    }

    this.safeSetData({ downloadingBillId: Number(id) })
    try {
      wx.showLoading({ title: '正在下载发票', mask: true })
      const tempFilePath = await api.download(url, filename)
      wx.hideLoading()
      if (!this._active) return
      await new Promise((resolve, reject) => {
        wx.openDocument({
          filePath: tempFilePath,
          fileType: fileType(filename),
          showMenu: true,
          success: resolve,
          fail: reject
        })
      })
    } catch (error) {
      wx.hideLoading()
      if (!this._active) return
      if (this.handleAccessError(error)) return
      wx.showModal({
        title: '发票打开失败',
        content: '发票文件已请求下载，但当前环境无法打开。请稍后重试或联系物业。',
        showCancel: false
      })
    } finally {
      this.safeSetData({ downloadingBillId: null })
    }
  },

  goProfile() {
    wx.switchTab({ url: '/pages/mine/index' })
  }
})
