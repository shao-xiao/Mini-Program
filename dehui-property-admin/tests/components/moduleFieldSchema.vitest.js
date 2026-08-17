import { describe, expect, it } from 'vitest'
import { normalizeModuleSchema, splitModuleValues, validateModuleValues } from '../../src/utils/moduleFieldSchema.js'

describe('module field schema', () => {
  const fields = normalizeModuleSchema({
    fields: [
      { fieldKey: 'description', apiField: 'description', dataType: 'LONG_TEXT', required: true, order: 2 },
      { fieldKey: 'custom_level', apiField: 'customFields.custom_level', fieldType: 'SINGLE_SELECT', source: 'CUSTOM', order: 3 },
      { fieldKey: 'status', apiField: 'status', dataType: 'SELECT', readonly: true, order: 1 }
    ]
  })

  it('normalizes types and surface order', () => {
    expect(fields.map(field => field.fieldKey)).toEqual(['status', 'description', 'custom_level'])
    expect(fields[1].dataType).toBe('TEXTAREA')
    expect(fields[2].dataType).toBe('SELECT')
  })

  it('splits canonical system values from customFields', () => {
    expect(splitModuleValues(fields, { description: '异常', custom_level: 'HIGH' })).toEqual({
      status: undefined,
      description: '异常',
      customFields: { custom_level: 'HIGH' }
    })
  })

  it('validates required editable fields without treating readonly state as input', () => {
    expect(validateModuleValues(fields, {}).valid).toBe(false)
    expect(validateModuleValues(fields, { description: '已处理' }).valid).toBe(true)
  })
})
