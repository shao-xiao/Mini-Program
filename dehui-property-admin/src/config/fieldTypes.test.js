import test from 'node:test'
import assert from 'node:assert/strict'
import {
  backendFieldType,
  fieldTypeOptions,
  isDateFieldType,
  isGroupableFieldType,
  isMultiValueFieldType,
  isNumericFieldType,
  isRelationFieldType,
  normalizeFieldType
} from './fieldTypes.js'

test('field type registry normalizes legacy select aliases', () => {
  assert.equal(normalizeFieldType('singleSelect'), 'select')
  assert.equal(normalizeFieldType('multiSelect'), 'multi-select')
  assert.equal(backendFieldType('select'), 'singleSelect')
  assert.equal(backendFieldType('multi-select'), 'multiSelect')
})

test('field type registry exposes platform renderer types without claiming backend persistence', () => {
  const allTypes = fieldTypeOptions().map(item => item.value)
  const backendTypes = fieldTypeOptions({ backendPersistableOnly: true }).map(item => item.value)

  assert.ok(allTypes.includes('long-text'))
  assert.ok(allTypes.includes('datetime'))
  assert.equal(backendTypes.includes('user'), false)
  assert.equal(backendTypes.includes('attachment'), true)
  assert.equal(backendTypes.includes('formula'), false)
  assert.equal(backendTypes.includes('money'), true)
  assert.equal(backendTypes.includes('status'), false)
  assert.equal(allTypes.includes('relation'), true)
  assert.equal(backendTypes.includes('relation'), false)
  assert.equal(isMultiValueFieldType('multiSelect'), true)
  assert.equal(isDateFieldType('datetime'), true)
  assert.equal(isGroupableFieldType('select'), true)
  assert.equal(isNumericFieldType('money'), true)
  assert.equal(isRelationFieldType('relation'), true)
  assert.deepEqual(backendTypes, [
    'text',
    'long-text',
    'number',
    'money',
    'date',
    'datetime',
    'select',
    'multi-select',
    'image',
    'attachment',
    'checkbox',
    'phone',
    'email',
    'url'
  ])
})
