import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildCustomFieldDefinitionPayload,
  createCustomFieldDefinitionDraft,
  customFieldDefinitionValidation,
  customFieldDefinitionTypeLabel,
  definitionToCustomFieldDraft
} from './customFieldDefinitionForm.js'

test('custom field definition draft exposes only implemented backend types', () => {
  const draft = createCustomFieldDefinitionDraft()

  assert.equal(draft.fieldType, 'TEXT')
  assert.deepEqual(draft.options, [])
  assert.equal(draft.required, false)
  assert.equal(draft.uniqueValue, false)
  assert.equal(customFieldDefinitionTypeLabel('IMAGE'), '图片')
})

test('custom field definition payload preserves stable option codes on edit', () => {
  const draft = definitionToCustomFieldDraft({
    id: 7,
    fieldCode: 'custom_priority',
    fieldName: '优先级',
    fieldType: 'SINGLE_SELECT',
    required: true,
    version: 3,
    optionItems: [
      { optionCode: 'high', optionLabel: '高', color: '#D92D20', displayOrder: 0 },
      { optionCode: 'normal', optionLabel: '普通', color: '#1570EF', displayOrder: 1 }
    ]
  })
  draft.options[0].label = '紧急'

  const payload = buildCustomFieldDefinitionPayload(draft)

  assert.equal(payload.fieldCode, 'custom_priority')
  assert.equal(payload.fieldType, 'SINGLE_SELECT')
  assert.equal(payload.version, 3)
  assert.deepEqual(payload.options, [
    { optionCode: 'high', optionLabel: '紧急', color: '#D92D20', displayOrder: 0 },
    { optionCode: 'normal', optionLabel: '普通', color: '#1570EF', displayOrder: 1 }
  ])
})

test('custom field definition validation rejects incomplete names and select options', () => {
  const emptyName = createCustomFieldDefinitionDraft({ fieldName: '   ' })
  const emptyOptions = createCustomFieldDefinitionDraft({
    fieldName: '客户等级',
    fieldType: 'MULTI_SELECT'
  })
  const duplicateOptions = createCustomFieldDefinitionDraft({
    fieldName: '客户等级',
    fieldType: 'SINGLE_SELECT',
    options: [{ label: 'A' }, { label: ' a ' }]
  })

  assert.equal(customFieldDefinitionValidation(emptyName).valid, false)
  assert.match(customFieldDefinitionValidation(emptyName).message, /字段名称/)
  assert.equal(customFieldDefinitionValidation(emptyOptions).valid, false)
  assert.match(customFieldDefinitionValidation(emptyOptions).message, /选项/)
  assert.equal(customFieldDefinitionValidation(duplicateOptions).valid, false)
  assert.match(customFieldDefinitionValidation(duplicateOptions).message, /重复/)
})

test('custom field definition payload keeps type configuration separate from values', () => {
  const draft = createCustomFieldDefinitionDraft({
    fieldName: '预算金额',
    fieldType: 'CURRENCY',
    defaultValue: '1200.50',
    decimalPlaces: 2,
    currency: 'CNY',
    minimum: 0,
    maximum: 100000
  })

  const payload = buildCustomFieldDefinitionPayload(draft)

  assert.equal(payload.defaultValue, '1200.50')
  assert.deepEqual({
    scale: payload.config.scale,
    currency: payload.config.currency,
    min: payload.config.min,
    max: payload.config.max
  }, {
    scale: 2,
    currency: 'CNY',
    min: 0,
    max: 100000
  })
  assert.ok(payload.config.surfaces.adminList.visible)
  assert.ok(payload.config.surfaces.miniProgramDetail.visible)
  assert.equal(Object.hasOwn(payload.config, 'values'), false)
})
