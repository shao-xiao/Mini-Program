const api = require('../../utils/request')
const { getBaseURL } = require('../../config/env')
const {
  formatMoney,
  formatDate,
  formatDateRange,
  invoiceStatusText
} = require('../../utils/format')

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

function billStatusClass(item) {
  if (item.status === 'PAID') return 'paid'
  if (item.status === 'CANCELLED') return 'cancelled'
  if (item.overdue) return 'overdue'
  return 'unpaid'
}

function absoluteApiUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return `${getBaseURL()}${url}`
}

Page({
  data: {
    loading: false,
    errorMessage: '',
    activeStatus: '',
    filters: [
      { label: '全部', value: '' },
      { label: '待缴', value: 'UNPAID' },
      { label: '已缴', value: 'PAID' },
      { label: '逾期', value: 'OVERDUE' }
    ],
    tenantName: '',
    summary: emptySummary(),
    bills: []
  },

  onShow() {
    this.loadBills()
  },

  async loadBills() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const query = this.data.activeStatus ? { status: this.data.activeStatus } : {}
      const data = await api.get('/mobile/bills', query)
      const summary = data.summary || {}
      const bills = (data.bills || []).map(item => ({
        ...item,
        displayTitle: item.title || item.billTypeText || '账单',
        amountText: formatMoney(item.amount),
        paidAmountText: formatMoney(item.paidAmount),
        unpaidAmountText: formatMoney(item.unpaidAmount),
        periodText: formatDateRange(item.periodStart, item.periodEnd),
        dueDateText: formatDate(item.dueDate),
        statusDisplay: item.overdue && item.status !== 'PAID' ? '已逾期' : (item.statusText || item.status || '未知'),
        statusClass: billStatusClass(item),
        invoiceStatusText: invoiceStatusText(item.invoiceStatus),
        invoiceAvailable: item.invoiceStatus === 'INVOICED' && Boolean(item.invoiceDownloadUrl),
        invoiceUrl: absoluteApiUrl(item.invoiceDownloadUrl)
      }))

      this.setData({
        tenantName: data.profile && data.profile.boundTenantName ? data.profile.boundTenantName : '',
        summary: {
          totalAmount: formatMoney(summary.totalAmount),
          unpaidAmount: formatMoney(summary.unpaidAmount),
          overdueAmount: formatMoney(summary.overdueAmount),
          paidAmount: formatMoney(summary.paidAmount),
          totalCount: summary.totalCount || 0,
          unpaidCount: summary.unpaidCount || 0,
          overdueCount: summary.overdueCount || 0,
          paidCount: summary.paidCount || 0
        },
        bills
      })
    } catch (error) {
      const message = error && error.message ? error.message : '账单加载失败，请重新登录或绑定租户身份'
      this.setData({
        tenantName: '',
        summary: emptySummary(),
        bills: [],
        errorMessage: message
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  changeStatus(event) {
    const status = event.currentTarget.dataset.value
    this.setData({ activeStatus: status }, () => this.loadBills())
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

  downloadInvoice(event) {
    const { url, name } = event.currentTarget.dataset
    if (!url) {
      wx.showToast({ title: '暂无发票文件', icon: 'none' })
      return
    }

    wx.showLoading({ title: '下载发票中', mask: true })
    wx.downloadFile({
      url,
      header: wx.getStorageSync('token') ? { Authorization: wx.getStorageSync('token') } : {},
      success(res) {
        wx.hideLoading()
        if (res.statusCode < 200 || res.statusCode >= 300) {
          wx.showToast({ title: '发票下载失败', icon: 'none' })
          return
        }
        wx.openDocument({
          filePath: res.tempFilePath,
          fileType: 'pdf',
          showMenu: true,
          fail() {
            wx.showToast({ title: name || '发票文件已下载', icon: 'none' })
          }
        })
      },
      fail() {
        wx.hideLoading()
        wx.showToast({ title: '发票下载失败', icon: 'none' })
      }
    })
  },

  goProfile() {
    wx.navigateTo({
      url: '/pages/me/index'
    })
  }
})
