const api = require('../../utils/request')
const { refreshCurrentProfile } = require('../../utils/auth')
const { canAccessFeature, getFeatureAccessState } = require('../../utils/capabilities')

const FEATURE_KEY = 'contract'

function formatMoney(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

function formatDate(value) {
  if (!value) return '-'
  const datePart = String(value).split('T')[0]
  const [year, month, day] = datePart.split('-')
  return year && month && day ? `${year}年${month}月${day}日` : value
}

function fileType(filename) {
  const suffix = String(filename || '').split('.').pop().toLowerCase()
  return ['pdf', 'doc', 'docx'].includes(suffix) ? suffix : 'pdf'
}

function statusText(value) {
  return {
    DRAFT: '草稿',
    PENDING: '待生效',
    ACTIVE: '履约中',
    EXPIRED: '已到期',
    TERMINATED: '已终止',
    CANCELLED: '已作废'
  }[value] || value || '-'
}

function roomText(item) {
  if (item.roomSummary) return item.roomSummary
  if (Array.isArray(item.roomNames) && item.roomNames.length) return item.roomNames.join('、')
  return item.roomName || '-'
}

function attachmentTypeText(value) {
  return value === 'SUPPLEMENTARY_AGREEMENT' ? '补充协议' : '主合同'
}

function requestFailureState(error) {
  const code = Number(error && (error.code || error.statusCode || error.status))
  if (code === 401) {
    return {
      pageState: 'login-required',
      errorMessage: '登录状态已失效，请重新登录后查看合同。'
    }
  }
  if (code === 403) {
    return {
      pageState: 'unauthorized',
      errorMessage: '当前账号没有租户合同查看资格，请联系物业确认租户联系人状态。'
    }
  }
  return {
    pageState: 'error',
    errorMessage: error && error.message ? error.message : '电子合同加载失败，请稍后重试。'
  }
}

function deniedState(profile) {
  const result = getFeatureAccessState(FEATURE_KEY, profile)
  const state = typeof result === 'string' ? result : result && (result.state || result.reason)
  if (state === 'login-required' || (result && result.loginRequired)) {
    return {
      pageState: 'login-required',
      errorMessage: '请先登录租户账号，再查看电子合同。'
    }
  }
  return {
    pageState: 'unauthorized',
    errorMessage: '电子合同仅向已匹配的有效租户联系人开放，当前账号暂无查看资格。'
  }
}

Page({
  data: {
    pageState: 'loading',
    loading: true,
    errorMessage: '',
    tenantName: '',
    contracts: [],
    page: 1,
    pageSize: 20,
    total: 0,
    noMore: false,
    loadingMore: false,
    downloadingContractId: null
  },

  onLoad() {
    this._active = true
    this._loadSequence = 0
  },

  onShow() {
    this._active = true
    this.loadContracts()
  },

  onUnload() {
    this._active = false
    this._loadSequence = Number(this._loadSequence || 0) + 1
  },

  safeSetData(payload) {
    if (!this._active) return
    this.setData(payload)
  },

  isCurrentRequest(requestId) {
    return this._active && requestId === this._loadSequence
  },

  async loadContracts(append = false) {
    if (append && (this.data.loadingMore || this.data.noMore)) return
    const requestId = Number(this._loadSequence || 0) + 1
    this._loadSequence = requestId
    this.safeSetData(append ? { loadingMore: true } : {
      pageState: 'loading',
      loading: true,
      errorMessage: '',
      tenantName: '',
      contracts: []
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
      const data = await api.get('/mobile/contracts', { page, pageSize: this.data.pageSize })
      if (!this.isCurrentRequest(requestId)) return
      const incoming = (data.records || data.contracts || []).map(item => ({
        ...item,
        contractName: item.contractName || '租赁合同',
        statusText: statusText(item.status),
        roomText: roomText(item),
        startDateText: formatDate(item.startDate),
        endDateText: formatDate(item.endDate),
        rentAmountText: formatMoney(item.rentAmount),
        depositAmountText: formatMoney(item.depositAmount),
        attachments: (item.attachments || []).map(attachment => ({
          ...attachment,
          typeText: attachmentTypeText(attachment.attachmentType),
          fileName: attachment.fileName || '合同附件.pdf'
        })),
        hasContractFile: Boolean((item.attachments || []).length || (item.hasContractFile && item.contractDownloadUrl)),
        contractDownloadUrl: item.contractDownloadUrl || '',
        contractFileName: item.contractFileName || `${item.contractNumber || 'contract'}.pdf`
      }))
      const contracts = append ? this.data.contracts.concat(incoming) : incoming
      this.safeSetData({
        pageState: contracts.length ? 'success' : 'empty',
        loading: false,
        loadingMore: false,
        page: Number(data.page || page),
        total: Number(data.total || 0),
        noMore: contracts.length >= Number(data.total || 0),
        tenantName: (data.profile && data.profile.boundTenantName) || profile.boundTenantName || '',
        contracts
      })
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({
        ...requestFailureState(error),
        loading: false,
        loadingMore: false,
        tenantName: '',
        contracts: []
      })
    }
  },

  retryLoad() {
    this.loadContracts()
  },

  onPullDownRefresh() {
    this.loadContracts().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadContracts(true)
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/business-detail/index?type=contract&id=${event.currentTarget.dataset.id}` })
  },

  handleAccessError(error) {
    const state = requestFailureState(error)
    if (state.pageState === 'error') return false
    this.safeSetData({
      ...state,
      loading: false,
      tenantName: '',
      contracts: []
    })
    return true
  },

  async openElectronicContract(event) {
    const { id, url, filename, attachmentId } = event.currentTarget.dataset
    if (!url) {
      wx.showToast({ title: '暂无电子合同', icon: 'none' })
      return
    }

    this.safeSetData({ downloadingContractId: Number(id), downloadingAttachmentId: Number(attachmentId || 0) })
    try {
      wx.showLoading({ title: '正在打开合同', mask: true })
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
        title: '合同打开失败',
        content: '电子合同文件已请求下载，但当前环境无法打开。请稍后重试或联系物业。',
        showCancel: false
      })
    } finally {
      this.safeSetData({ downloadingContractId: null, downloadingAttachmentId: null })
    }
  },

  goProfile() {
    wx.switchTab({ url: '/pages/mine/index' })
  }
})
