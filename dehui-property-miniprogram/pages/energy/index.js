const api = require('../../utils/request')
const { formatMoney, formatDate } = require('../../utils/format')

const meterTypeText = {
  ELECTRIC: '电',
  ELECTRICITY: '电',
  WATER: '水',
  GAS: '燃气'
}

const billStatusText = {
  NOT_GENERATED: '未生成',
  GENERATED: '已生成',
  POSTED: '已入账'
}

const anomalyReasonText = {
  NEGATIVE_USAGE: '负用量',
  ZERO_USAGE: '零用量',
  HIGH_USAGE: '高用量',
  LOW_USAGE: '低用量',
  NO_CHANGE: '连续无变化'
}

function formatNumber(value) {
  const number = Number(value || 0)
  if (!Number.isFinite(number)) return '0'
  return number.toFixed(2).replace(/\.00$/, '')
}

Page({
  data: {
    loading: false,
    errorMessage: '',
    stats: {
      recordCount: 0,
      electricUsageText: '0',
      waterUsageText: '0',
      gasUsageText: '0',
      totalAmountText: '0.00',
      abnormalCount: 0
    },
    readings: [],
    bills: []
  },

  onShow() {
    this.loadEnergy()
  },

  async loadEnergy() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const [stats, readings, bills] = await Promise.all([
        api.get('/mobile/energy/stats'),
        api.get('/mobile/energy/readings'),
        api.get('/mobile/energy/bills')
      ])

      this.setData({
        stats: {
          recordCount: stats.recordCount || 0,
          electricUsageText: formatNumber(stats.electricUsage),
          waterUsageText: formatNumber(stats.waterUsage),
          gasUsageText: formatNumber(stats.gasUsage),
          totalAmountText: formatMoney(stats.totalAmount),
          abnormalCount: stats.abnormalCount || 0
        },
        readings: (readings || []).map(item => ({
          ...item,
          typeText: meterTypeText[item.meterType] || item.meterType || '-',
          usageText: `${formatNumber(item.usageAmount)} ${item.unit || ''}`,
          amountText: formatMoney(item.settlementAmount),
          billStatusText: billStatusText[item.billStatus] || item.billStatus || '-',
          abnormalText: item.abnormalFlag
            ? anomalyReasonText[item.abnormalReason] || item.abnormalReason || '异常'
            : '正常',
          readingDateText: formatDate(item.readingDate)
        })),
        bills: (bills || []).map(item => ({
          ...item,
          amountText: formatMoney(item.amount),
          periodText: `${formatDate(item.periodStart)} ~ ${formatDate(item.periodEnd)}`,
          statusText: item.auditStatus === 'APPROVED' ? '已发布' : (item.auditStatus || '-')
        }))
      })
    } catch (error) {
      const message = error && error.message ? error.message : '请先登录并绑定租户后查看能耗'
      this.setData({
        errorMessage: message,
        readings: [],
        bills: []
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/me/index' })
  }
})
