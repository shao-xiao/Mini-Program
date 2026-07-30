const api = require('../../utils/request')
const { presentEnergyDetailRows } = require('../../utils/energyDetailPresentation')
const { loadModuleFieldSchema, SURFACES } = require('../../utils/moduleFieldSchema')
const {
  presentWorkOrderDetail,
  documentFileType
} = require('../../utils/workorderDetailPresentation')

const TYPES = {
  bill: { title: '账单详情', url: id => `/mobile/bills/${id}` },
  contract: { title: '合同详情', url: id => `/mobile/contracts/${id}` },
  room: { title: '房间详情', url: id => `/mobile/my-business/rooms/${id}` },
  energy: { title: '能耗详情', url: id => `/mobile/energy/details/${id}` },
  parking: { title: '车位详情', url: id => `/mobile/parking/${id}` },
  meeting: { title: '会议预约详情', url: id => `/mobile/meetings/bookings/${id}` },
  workorder: { title: '工单详情', url: id => `/mobile/workorders/${id}` },
  visitor: { title: '访客预约详情', url: id => `/mobile/visitors/${id}` }
}

const DETAIL_LABELS = {
  meeting: {
    id: '记录序号',
    bookingNumber: '预约编号',
    meetingRoomId: '会议室序号',
    meetingRoomName: '会议室',
    location: '位置',
    startTime: '开始时间',
    endTime: '结束时间',
    status: '审核状态',
    unitPriceSnapshot: '单价快照',
    calculatedAmount: '预计费用',
    purpose: '会议用途',
    participantCount: '参会人数',
    contactName: '申请人',
    contactPhone: '联系电话',
    departmentName: '部门',
    reviewReply: '物业回复',
    reviewedAt: '审核时间',
    remark: '备注',
    createdAt: '提交时间'
  }
}

const MEETING_STATUS_TEXT = {
  PENDING: '待审核',
  BOOKED: '待审核',
  CONFIRMED: '审核通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  COMPLETED: '已完成'
}

function displayValue(value) {
  if (value === null || value === undefined || value === '') return '-'
  if (Array.isArray(value)) return value.map(item => typeof item === 'object' ? JSON.stringify(item) : item).join('；') || '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value).replace('T', ' ')
}

function displayDetailValue(type, key, value) {
  if (type === 'meeting' && key === 'status') {
    return MEETING_STATUS_TEXT[value] || displayValue(value)
  }
  return displayValue(value)
}

function buildMeetingReview(data = {}) {
  const status = String(data.status || '').trim()
  const reply = String(data.reviewReply || '').trim()
  const statusText = MEETING_STATUS_TEXT[status] || displayValue(status)
  let tone = 'neutral'
  let fallbackReply = '物业暂未填写审核回复'
  if (['PENDING', 'BOOKED'].includes(status)) {
    tone = 'pending'
    fallbackReply = '物业正在审核，请稍后查看审核结果'
  } else if (status === 'REJECTED') {
    tone = 'rejected'
    fallbackReply = '本次预约未通过审核'
  } else if (['CONFIRMED', 'COMPLETED'].includes(status)) {
    tone = 'approved'
    fallbackReply = '预约已审核通过'
  }
  return {
    status,
    statusText,
    tone,
    cancellable: ['PENDING', 'BOOKED', 'CONFIRMED'].includes(status),
    reply: reply || fallbackReply,
    reviewedAtText: data.reviewedAt ? displayValue(data.reviewedAt) : '',
    bookingNumber: String(data.bookingNumber || '').trim()
  }
}

function needsAuthenticatedDownload(url) {
  const value = String(url || '')
  if (!value || /^(wxfile|file|blob|data):/i.test(value)) return false
  return value.startsWith('/') || /^https?:\/\//i.test(value)
}

function openDocument(filePath, filename) {
  return new Promise((resolve, reject) => {
    const options = {
      filePath,
      showMenu: true,
      success: resolve,
      fail: reject
    }
    const fileType = documentFileType(filename)
    if (fileType) {
      options.fileType = fileType
    }
    wx.openDocument(options)
  })
}

Page({
  data: {
    loading: true, pageState: 'loading', errorMessage: '', title: '业务详情', rows: [],
    bill: null, lineItems: [], invoices: [], downloadingInvoiceId: null,
    contract: null, contractAttachments: [], downloadingContractAttachmentId: null,
    meetingReview: null,
    cancellingMeeting: false,
    workorder: null,
    workorderImageLabel: '报修现场照片',
    workorderImages: [],
    workorderFiles: [],
    workorderRecords: [],
    coreRows: [],
    customRows: [],
    customImageGroups: [],
    customFileGroups: [],
    openingWorkorderFileKey: ''
  },
  onLoad(options) {
    this._active = true
    this.type = String(options.type || '')
    this.id = Number(options.id)
    const config = TYPES[this.type]
    if (!config || !this.id) {
      this.setData({ loading: false, pageState: 'error', errorMessage: '无效的详情参数' })
      return
    }
    wx.setNavigationBarTitle({ title: config.title })
    this.setData({ title: config.title })
    this.loadDetail()
  },
  onUnload() {
    this._active = false
    this.workorderHydrationVersion = Number(this.workorderHydrationVersion || 0) + 1
  },
  isCurrentDetailRequest(requestVersion) {
    return this._active !== false && this.workorderHydrationVersion === requestVersion
  },
  async loadDetail() {
    if (this._active === false) return
    const config = TYPES[this.type]
    const hydrationVersion = Number(this.workorderHydrationVersion || 0) + 1
    this.workorderHydrationVersion = hydrationVersion
    this.setData({
      loading: true,
      pageState: 'loading',
      errorMessage: '',
      meetingReview: null,
      workorder: null,
      workorderImages: [],
      workorderFiles: [],
      workorderRecords: [],
      coreRows: [],
      customRows: [],
      customImageGroups: [],
      customFileGroups: []
    })
    try {
      let data
      let workorderSchema = { fields: [] }
      if (this.type === 'workorder') {
        const result = await Promise.all([
          api.get(config.url(this.id)),
          loadModuleFieldSchema('workorders', SURFACES.detail).catch(() => ({ fields: [] }))
        ])
        data = result[0]
        workorderSchema = result[1]
      } else {
        data = await api.get(config.url(this.id))
      }
      if (!this.isCurrentDetailRequest(hydrationVersion)) return
      if (this.type === 'bill') {
        const lineItems = (data.lineItems || []).map(item => ({
          ...item,
          calculatedAmountText: Number(item.calculatedAmount || 0).toFixed(2),
          adjustmentAmountText: Number(item.adjustmentAmount || 0).toFixed(2),
          finalAmountText: Number(item.finalAmount || 0).toFixed(2)
        }))
        const invoices = (data.invoices || []).map(item => ({
          ...item,
          amountText: Number(item.amount || 0).toFixed(2),
          downloadUrl: `/mobile/bills/${this.id}/invoices/${item.id}/download`
        }))
        this.setData({
          loading: false,
          pageState: 'success',
          bill: {
            ...data,
            calculatedTotalText: Number(data.calculatedTotal || 0).toFixed(2),
            adjustmentTotalText: Number(data.adjustmentTotal || 0).toFixed(2),
            finalAmountText: Number(data.finalAmount || data.amount || 0).toFixed(2),
            paidAmountText: Number(data.paidAmount || 0).toFixed(2)
          },
          lineItems,
          invoices
        })
        return
      }
      if (this.type === 'contract') {
        this.setData({
          loading: false,
          pageState: 'success',
          contract: { ...data, rooms: data.rooms || [] },
          contractAttachments: (data.attachments || []).map(item => ({
            ...item,
            typeText: item.attachmentType === 'SUPPLEMENTARY_AGREEMENT' ? '补充协议' : '主合同',
            fileName: item.fileName || '合同附件.pdf'
          }))
        })
        return
      }
      if (this.type === 'energy') {
        const rows = presentEnergyDetailRows(data)
        this.setData({
          loading: false,
          pageState: rows.length ? 'success' : 'empty',
          rows,
          meetingReview: null
        })
        return
      }
      if (this.type === 'workorder') {
        const presentation = presentWorkOrderDetail(data, workorderSchema)
        this.setData({
          loading: false,
          pageState: 'success',
          ...presentation
        })
        this.resolveWorkOrderPresentation(presentation)
          .then(hydrated => {
            if (this.isCurrentDetailRequest(hydrationVersion)) {
              this.setData(hydrated)
            }
          })
          .catch(() => {})
        return
      }
      const hidden = new Set(['tenantId', 'mobileUserId', 'tenantContactId', 'sysUserId'])
      const meetingReviewFields = new Set(['reviewReply', 'reviewedAt'])
      const labels = DETAIL_LABELS[this.type] || {}
      const rows = Object.keys(data || {})
        .filter(key => !hidden.has(key) && (this.type !== 'meeting' || !meetingReviewFields.has(key)))
        .map(key => ({
          key,
          label: labels[key] || key,
          value: displayDetailValue(this.type, key, data[key])
        }))
      this.setData({
        loading: false,
        pageState: rows.length ? 'success' : 'empty',
        rows,
        meetingReview: this.type === 'meeting' ? buildMeetingReview(data) : null
      })
    } catch (error) {
      if (!this.isCurrentDetailRequest(hydrationVersion)) return
      this.setData({ loading: false, pageState: 'error', errorMessage: error.message || '详情加载失败' })
    }
  },
  retryLoad() { this.loadDetail() },
  async resolveWorkOrderImages(files) {
    const source = files || []
    const resolved = new Array(source.length)
    let nextIndex = 0
    const workers = Array.from({ length: Math.min(4, source.length) }, async () => {
      while (nextIndex < source.length) {
        const index = nextIndex
        nextIndex += 1
        const file = source[index]
        resolved[index] = await this.resolveWorkOrderImage(file)
      }
    })
    await Promise.all(workers)
    return resolved
  },
  async resolveWorkOrderImage(file) {
      if (!file.url) return { ...file, previewUrl: '', previewError: true }
      if (!needsAuthenticatedDownload(file.url)) {
        return { ...file, previewUrl: file.url, previewError: false }
      }
      if (typeof api.download !== 'function') {
        return { ...file, previewUrl: '', previewError: true }
      }
      try {
        const previewUrl = await api.download(file.url, file.name || '工单照片.jpg', {
          silent: true,
          temporary: true
        })
        return { ...file, previewUrl, previewError: false }
      } catch (_) {
        return { ...file, previewUrl: '', previewError: true }
      }
  },
  async resolveWorkOrderPresentation(presentation) {
    const workorderImages = await this.resolveWorkOrderImages(presentation.workorderImages)
    const workorderRecords = []
    for (const record of presentation.workorderRecords) {
      workorderRecords.push({
        ...record,
        images: await this.resolveWorkOrderImages(record.images)
      })
    }
    const customImageGroups = []
    for (const group of presentation.customImageGroups) {
      customImageGroups.push({
        ...group,
        files: await this.resolveWorkOrderImages(group.files)
      })
    }
    return {
      ...presentation,
      workorderImages,
      workorderRecords,
      customImageGroups
    }
  },
  workOrderCollection(scope, recordId, groupKey) {
    if (scope === 'report-images') return this.data.workorderImages
    if (scope === 'report-files') return this.data.workorderFiles
    if (scope === 'record-images' || scope === 'record-files') {
      const record = this.data.workorderRecords.find(item => Number(item.id) === Number(recordId))
      return record ? (scope === 'record-images' ? record.images : record.files) : []
    }
    if (scope === 'custom-images' || scope === 'custom-files') {
      const groups = scope === 'custom-images' ? this.data.customImageGroups : this.data.customFileGroups
      const group = groups.find(item => String(item.key) === String(groupKey))
      return group ? group.files : []
    }
    return []
  },
  previewWorkOrderImage(event) {
    const dataset = event.currentTarget.dataset
    const files = this.workOrderCollection(dataset.scope, dataset.recordId, dataset.groupKey)
    const selected = files[Number(dataset.index)]
    const urls = files.map(file => file.previewUrl).filter(Boolean)
    if (selected && selected.previewUrl && urls.length) {
      wx.previewImage({ current: selected.previewUrl, urls })
    }
  },
  async openWorkOrderFile(event) {
    if (this._active === false || this.data.openingWorkorderFileKey) return
    const dataset = event.currentTarget.dataset
    const files = this.workOrderCollection(dataset.scope, dataset.recordId, dataset.groupKey)
    const file = files[Number(dataset.index)]
    if (!file || !file.url) return
    const fileKey = `${dataset.scope}:${dataset.recordId || dataset.groupKey || 'main'}:${dataset.index}`
    this.setData({ openingWorkorderFileKey: fileKey })
    try {
      const filename = file.name || file.url || '工单附件'
      const filePath = await api.download(file.url, filename, {
        silent: true,
        temporary: true
      })
      if (this._active === false) return
      await openDocument(filePath, filename)
    } catch (error) {
      if (this._active !== false) {
        wx.showToast({ title: error.message || '附件打开失败', icon: 'none' })
      }
    } finally {
      if (this._active !== false) {
        this.setData({ openingWorkorderFileKey: '' })
      }
    }
  },
  cancelMeetingBooking() {
    if (!this.data.meetingReview || !this.data.meetingReview.cancellable || this.data.cancellingMeeting) return
    wx.showModal({
      title: '取消预约',
      content: '取消后该时间段将重新开放，确定继续吗？',
      confirmText: '确认取消',
      cancelText: '暂不取消',
      success: async result => {
        if (!result.confirm) return
        this.setData({ cancellingMeeting: true })
        try {
          await api.post(`/mobile/meetings/bookings/${this.id}/cancel`)
          wx.showToast({ title: '预约已取消', icon: 'success' })
          await this.loadDetail()
        } catch (error) {
          wx.showToast({ title: error.message || '取消失败', icon: 'none' })
        } finally {
          this.setData({ cancellingMeeting: false })
        }
      }
    })
  },
  async downloadInvoice(event) {
    const invoiceId = Number(event.currentTarget.dataset.id)
    const url = event.currentTarget.dataset.url
    const filename = event.currentTarget.dataset.filename || `invoice-${invoiceId}.pdf`
    this.setData({ downloadingInvoiceId: invoiceId })
    try {
      const path = await api.download(url, filename)
      await new Promise((resolve, reject) => wx.openDocument({
        filePath: path, showMenu: true, success: resolve, fail: reject
      }))
    } catch (error) {
      wx.showToast({ title: error.message || '发票下载失败', icon: 'none' })
    } finally {
      this.setData({ downloadingInvoiceId: null })
    }
  },
  async downloadContractAttachment(event) {
    const attachmentId = Number(event.currentTarget.dataset.id)
    const url = event.currentTarget.dataset.url
    const filename = event.currentTarget.dataset.filename || `contract-${attachmentId}.pdf`
    this.setData({ downloadingContractAttachmentId: attachmentId })
    try {
      const path = await api.download(url, filename)
      await openDocument(path, filename)
    } catch (error) {
      wx.showToast({ title: error.message || '合同附件打开失败', icon: 'none' })
    } finally {
      this.setData({ downloadingContractAttachmentId: null })
    }
  }
})
