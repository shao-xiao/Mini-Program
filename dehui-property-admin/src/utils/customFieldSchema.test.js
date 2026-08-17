import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeCustomFieldDefinition, normalizeCustomFieldDefinitions } from './customFieldSchema.js'

test('custom-field definitions preserve machine codes and option codes as query values', () => {
  const field = normalizeCustomFieldDefinition({
    id: 7,
    fieldCode: 'customer_level',
    fieldName: '客户等级',
    fieldType: 'SINGLE_SELECT',
    sortable: true,
    groupable: true,
    optionItems: [
      { optionCode: 'level_b', optionLabel: 'B级', displayOrder: 2, status: 'ENABLED' },
      { optionCode: 'level_a', optionLabel: 'A级', displayOrder: 1, status: 'DISABLED' }
    ]
  })

  assert.equal(field.key, 'customer_level')
  assert.equal(field.type, 'select')
  assert.equal(field.custom, true)
  assert.equal(field.platformCustom, true)
  assert.deepEqual(field.options, [
    { value: 'level_a', label: 'A级', color: '', disabled: true },
    { value: 'level_b', label: 'B级', color: '', disabled: false }
  ])
})

test('custom-field definitions support every implemented backend field type and stable ordering', () => {
  const definitions = normalizeCustomFieldDefinitions([
    { fieldCode: 'reviewed_at', fieldName: '复核时间', fieldType: 'DATETIME', displayOrder: 2 },
    { fieldCode: 'amount_limit', fieldName: '金额上限', fieldType: 'CURRENCY', displayOrder: 1 },
    { fieldCode: 'notes', fieldName: '说明', fieldType: 'LONG_TEXT', displayOrder: 3 }
  ])

  assert.deepEqual(definitions.map(field => [field.key, field.type]), [
    ['amount_limit', 'money'],
    ['reviewed_at', 'datetime'],
    ['notes', 'long-text']
  ])
})

test('custom-field definitions expose backend numeric config to renderers and editors', () => {
  const field = normalizeCustomFieldDefinition({
    fieldCode: 'budget',
    fieldName: 'Budget',
    fieldType: 'CURRENCY',
    config: { scale: 2, currency: 'CNY', min: 0, max: 100 }
  })

  assert.equal(field.decimals, 2)
  assert.equal(field.currency, 'CNY')
  assert.deepEqual(field.config, { scale: 2, currency: 'CNY', min: 0, max: 100 })
})
