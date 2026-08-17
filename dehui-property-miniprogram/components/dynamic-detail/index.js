const { normalizeFields } = require('../../utils/moduleFieldSchema')
Component({
  properties: { schema: Object, values: Object },
  data: { rows: [] },
  observers: {
    'schema,values': function (schema, values) {
      const fields = normalizeFields(schema && schema.fields || schema)
      const source = values || {}
      const customFields = source.customFields || {}
      const rows = fields.map((field) => ({
        field,
        value: field.source === 'CUSTOM'
          ? customFields[field.fieldKey]
          : (source[field.apiField] !== undefined ? source[field.apiField] : source[field.fieldKey])
      }))
      this.setData({ rows })
    }
  }
})
