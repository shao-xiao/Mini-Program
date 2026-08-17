const api = require('../../../../utils/request')
const {
  requireStaffFeature,
  has,
  pageRecords,
  loadSchema,
  uploadInternalImages,
  customFileValue,
  imageFieldLimit,
  previewProtected,
  message
} = require('../../../../utils/staff')

Page({
  data: {
    meters: [],
    meterIndex: -1,
    schemaFields: [],
    file: null,
    customFiles: {},
    submitting: false,
    form: {
      meterId: null,
      periodMonth: currentMonth(),
      currentReading: '',
      readingDate: today(),
      resetReason: '',
      remark: ''
    },
    customFields: {}
  },
  async onLoad() {
    try {
      const profile = await requireStaffFeature('staffEnergy')
      if (!has('energy.staff.reading.create', profile)) throw new Error('当前员工只有能耗查看权限')
      const [meterPayload, schema] = await Promise.all([
        api.get('/mobile/staff/energy/meters', { page: 1, pageSize: 100 }, { silent: true }),
        loadSchema('energy-records')
      ])
      this.setData({
        meters: pageRecords(meterPayload).records.filter(item => ['ACTIVE', 'ENABLED'].includes(item.status)),
        schemaFields: ((schema && schema.fields) || []).filter(field => field.source === 'CUSTOM' && !field.readonly)
      })
    } catch (error) {
      wx.showToast({ title: message(error), icon: 'none' })
    }
  },
  input(event) { this.setData({ [`form.${event.currentTarget.dataset.key}`]: event.detail.value }) },
  customInput(event) { this.setData({ [`customFields.${event.currentTarget.dataset.key}`]: event.detail.value }) },
  async chooseCustomImages(event) {
    if (this.data.submitting) return
    const key = event.currentTarget.dataset.key
    const field = this.data.schemaFields.find(item => item.fieldKey === key)
    const current = this.data.customFiles[key] || []
    const remaining = Math.max(0, imageFieldLimit(field) - current.length)
    if (!remaining) return
    this.setData({ submitting: true })
    try {
      const next = current.concat(await uploadInternalImages(remaining))
      this.setData({
        [`customFiles.${key}`]: next,
        [`customFields.${key}`]: customFileValue(next)
      })
    } catch (error) {
      wx.showToast({ title: message(error, '图片上传失败'), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
  removeCustomImage(event) {
    const { key } = event.currentTarget.dataset
    const index = Number(event.currentTarget.dataset.index)
    const next = (this.data.customFiles[key] || []).filter((_, current) => current !== index)
    this.setData({
      [`customFiles.${key}`]: next,
      [`customFields.${key}`]: customFileValue(next)
    })
  },
  previewCustomImage(event) {
    const { key, url } = event.currentTarget.dataset
    return previewProtected((this.data.customFiles[key] || []).map(file => file.url), url)
  },
  chooseMeter(event) {
    const meterIndex = Number(event.detail.value)
    this.setData({ meterIndex, 'form.meterId': this.data.meters[meterIndex].id })
  },
  async chooseImage() {
    if (this.data.submitting) return
    this.setData({ submitting: true })
    try {
      const files = await uploadInternalImages(1)
      if (files.length) this.setData({ file: files[0] })
    } catch (error) {
      wx.showToast({ title: message(error, '图片上传失败'), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
  async submit() {
    if (this.data.submitting) return
    if (!this.data.form.meterId || this.data.form.currentReading === '') {
      wx.showToast({ title: '请选择表具并填写本次读数', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      const result = await api.post('/mobile/staff/energy/readings', {
        ...this.data.form,
        attachmentFileIds: this.data.file ? [this.data.file.fileId] : [],
        customFields: this.data.customFields
      })
      wx.showModal({
        title: result.abnormal ? '抄表已保存为异常' : '抄表成功',
        content: `上期 ${result.previousReading}，本期用量 ${result.usageAmount}，费用依据 ¥${result.settlementAmount}`,
        showCancel: false,
        success: () => wx.navigateBack()
      })
    } catch (error) {
      wx.showToast({ title: message(error, '抄表失败'), icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})

function currentMonth() { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}` }
function today() { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}` }
