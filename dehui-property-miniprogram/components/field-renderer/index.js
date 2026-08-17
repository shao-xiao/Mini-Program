const { normalizeFileValue } = require('../../utils/moduleFieldSchema')
const api = require('../../utils/request')

function isProtectedUrl(url) {
  const value = String(url || '')
  return value.startsWith('/api/files/') || value.startsWith('/api/mobile/files/')
}

function requiresAuthenticatedDownload(url) {
  const value = String(url || '')
  if (!value) return false
  if (/^(wxfile|file|blob|data):/i.test(value)) return false
  return isProtectedUrl(value) || /^https?:\/\//i.test(value) || value.startsWith('/')
}

Component({
  properties: { field: Object, value: null },
  data: { text: '-', files: [] },
  observers: {
    'field,value': function (field, value) {
      if (field && ['IMAGE', 'ATTACHMENT'].includes(field.dataType)) {
        const files = normalizeFileValue(value).files.map(file => ({
          ...file,
          previewUrl: field.dataType === 'IMAGE' && !requiresAuthenticatedDownload(file.url) ? file.url : '',
          previewLoading: field.dataType === 'IMAGE' && requiresAuthenticatedDownload(file.url),
          previewError: false
        }))
        const version = (this._previewVersion || 0) + 1
        this._previewVersion = version
        this.setData({ files, text: '' })
        if (field.dataType === 'IMAGE') this.resolveProtectedPreviews(files, version)
        return
      }
      this._previewVersion = (this._previewVersion || 0) + 1
      const option = field && (field.options || []).find(item => String(item.value) === String(value))
      this.setData({ text: option ? option.label : (Array.isArray(value) ? value.join('、') : (value == null || value === '' ? '-' : String(value))), files: [] })
    }
  },
  lifetimes: {
    attached() {
      this._detached = false
    },
    detached() {
      this._detached = true
      this._previewVersion = (this._previewVersion || 0) + 1
    }
  },
  methods: {
    async resolveProtectedPreviews(files, version) {
      const resolved = await Promise.all((files || []).map(async file => {
        if (!requiresAuthenticatedDownload(file.url)) return file
        try {
          const previewUrl = await api.download(file.url, file.name || 'image.jpg', { silent: true })
          return { ...file, previewUrl, previewLoading: false, previewError: false }
        } catch (_) {
          return { ...file, previewUrl: '', previewLoading: false, previewError: true }
        }
      }))
      if (this._detached || version !== this._previewVersion) return
      this.setData({ files: resolved })
    },
    preview(event) {
      const index = Number(event.currentTarget.dataset.index)
      const current = this.data.files[index] && this.data.files[index].previewUrl
      const urls = this.data.files.map(file => file.previewUrl).filter(Boolean)
      if (current && urls.length) wx.previewImage({ current, urls })
    }
  }
})
