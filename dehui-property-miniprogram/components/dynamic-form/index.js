const api = require('../../utils/request')
const { normalizeFields, normalizeFileValue, buildSubmission } = require('../../utils/moduleFieldSchema')

Component({
  properties: {
    moduleKey: String,
    surface: { type: String, value: 'MINIPROGRAM_FORM' },
    schema: { type: Object, value: {} },
    values: { type: Object, value: {} },
    readonly: Boolean,
    compact: Boolean
  },
  data: { fields: [], model: {}, errors: {}, uploading: false },
  observers: {
    'schema,values': function (schema, values) {
      const fields = normalizeFields(schema && schema.fields || schema)
      const model = {}
      fields.forEach(field => {
        const raw = values && (values[field.apiField] ?? values[field.fieldKey] ?? (values.customFields || {})[field.fieldKey])
        model[field.fieldKey] = ['IMAGE', 'ATTACHMENT'].includes(field.dataType) ? normalizeFileValue(raw) : (raw ?? '')
      })
      this.setData({ fields, model })
    }
  },
  methods: {
    onInput(event) {
      this.change(event.currentTarget.dataset.key, event.detail.value)
    },
    onSwitch(event) {
      this.change(event.currentTarget.dataset.key, event.detail.value)
    },
    onPicker(event) {
      const field = this.data.fields.find(item => item.fieldKey === event.currentTarget.dataset.key)
      const option = field && field.options[Number(event.detail.value)]
      this.change(field.fieldKey, option ? option.value : '')
    },
    onMultiSelect(event) {
      this.change(event.currentTarget.dataset.key, event.detail.value || [])
    },
    async chooseFile(event) {
      const field = this.data.fields.find(item => item.fieldKey === event.currentTarget.dataset.key)
      if (!field || this.data.uploading) return
      const current = normalizeFileValue(this.data.model[field.fieldKey])
      const maxFiles = Number(field.validation && field.validation.maxFiles) || 30
      const remain = Math.max(0, maxFiles - current.files.length)
      if (!remain) return wx.showToast({ title: `最多上传${maxFiles}个文件`, icon: 'none' })
      const paths = await this.pickPaths(field.dataType, remain)
      this.setData({ uploading: true })
      this.triggerEvent('uploadstate', { uploading: true })
      try {
        for (const item of paths) {
          const uploaded = await api.upload('/mobile/files', item.path, 'file', {
            formData: { fieldType: field.dataType },
            silent: true
          })
          current.files.push({
            fileId: Number(uploaded.fileId || uploaded.id),
            name: uploaded.name || item.name || '',
            url: uploaded.url || '',
            sortOrder: current.files.length
          })
        }
        this.change(field.fieldKey, current)
      } finally {
        this.setData({ uploading: false })
        this.triggerEvent('uploadstate', { uploading: false })
      }
    },
    removeFile(event) {
      const key = event.currentTarget.dataset.key
      const index = Number(event.currentTarget.dataset.index)
      const current = normalizeFileValue(this.data.model[key])
      current.files.splice(index, 1)
      current.files.forEach((file, sortOrder) => { file.sortOrder = sortOrder })
      this.change(key, current)
    },
    previewFile(event) {
      const file = normalizeFileValue(this.data.model[event.currentTarget.dataset.key]).files[Number(event.currentTarget.dataset.index)]
      if (!file || !file.url) return
      wx.previewImage({ current: file.url, urls: normalizeFileValue(this.data.model[event.currentTarget.dataset.key]).files.map(item => item.url).filter(Boolean) })
    },
    change(key, value) {
      const model = { ...this.data.model, [key]: value }
      const errors = { ...this.data.errors }
      delete errors[key]
      this.setData({ model, errors })
      this.triggerEvent('change', { values: model, payload: buildSubmission({ fields: this.data.fields }, model) })
    },
    validate() {
      const errors = {}
      this.data.fields.forEach(field => {
        if (!field.editable) return
        const value = this.data.model[field.fieldKey]
        const empty = value == null || value === '' || (Array.isArray(value) && !value.length)
          || (value && value.files && !value.files.length)
        if (field.required && empty) errors[field.fieldKey] = `请填写${field.label}`
        const maxLength = Number(field.validation && field.validation.maxLength)
        if (!empty && maxLength > 0 && typeof value === 'string' && value.length > maxLength) {
          errors[field.fieldKey] = `${field.label}不能超过${maxLength}个字符`
        }
      })
      this.setData({ errors })
      return { valid: !Object.keys(errors).length && !this.data.uploading, errors, uploading: this.data.uploading }
    },
    getPayload() {
      return buildSubmission({ fields: this.data.fields }, this.data.model)
    },
    pickPaths(type, count) {
      if (type === 'IMAGE') {
        return new Promise((resolve, reject) => wx.chooseMedia({
          count, mediaType: ['image'],
          success: result => resolve((result.tempFiles || []).map(item => ({ path: item.tempFilePath }))),
          fail: reject
        }))
      }
      return new Promise((resolve, reject) => wx.chooseMessageFile({
        count, type: 'file',
        success: result => resolve((result.tempFiles || []).map(item => ({ path: item.path, name: item.name }))),
        fail: reject
      }))
    }
  }
})
