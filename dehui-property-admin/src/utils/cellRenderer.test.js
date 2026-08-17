import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatCellValue,
  normalizeCellAttachments,
  normalizeCellRelations,
  normalizeCellUsers,
  resolveCellOption,
  resolveCellValue,
  safeCellUrl
} from './cellRenderer.js'

test('resolveCellValue supports record, custom and configured values', () => {
  const row = { name: '电梯', customFields: { custom_level: '重点' } }
  assert.equal(resolveCellValue(row, { key: 'name' }), '电梯')
  assert.equal(resolveCellValue(row, { key: 'custom_level', custom: true }), '重点')
  assert.equal(resolveCellValue(row, { key: 'display', valueGetter: item => `${item.name}设备` }), '电梯设备')
})

test('resolveCellOption keeps configured label and tone', () => {
  const option = resolveCellOption('FAULT', {
    options: [{ label: '故障', value: 'FAULT', tone: 'danger' }]
  })
  assert.deepEqual(option, { label: '故障', value: 'FAULT', tone: 'danger' })
})

test('resolveCellOption uses a precompiled option map without reading options again', () => {
  let optionsReads = 0
  const field = {
    get options() {
      optionsReads += 1
      return [{ label: '旧标签', value: 'NORMAL' }]
    }
  }
  const optionMap = new Map([
    ['NORMAL', { label: '正常', value: 'NORMAL', tone: 'success' }]
  ])

  assert.deepEqual(resolveCellOption('NORMAL', field, optionMap), {
    label: '正常',
    value: 'NORMAL',
    tone: 'success'
  })
  assert.deepEqual(resolveCellOption('UNKNOWN', field, optionMap), {
    label: 'UNKNOWN',
    value: 'UNKNOWN',
    tone: 'info'
  })
  assert.equal(optionsReads, 0)
})

test('formatCellValue formats number, date and empty values without evaluating formulas', () => {
  assert.equal(formatCellValue(1234.5, { type: 'number', decimals: 1 }), '1,234.5')
  assert.equal(formatCellValue(1234.5, { type: 'money' }), '¥1,234.50')
  assert.equal(formatCellValue('2026-07-14T08:00:00', { type: 'date' }), '2026-07-14')
  assert.equal(formatCellValue('2026-07-14T08:00:00', { type: 'date', format: 'datetime' }), '2026-07-14 08:00')
  assert.equal(formatCellValue('A1+B1', { type: 'formula' }), 'A1+B1')
  assert.equal(formatCellValue('', { type: 'text' }), '-')
})

test('user and attachment values use normalized display contracts', () => {
  assert.deepEqual(normalizeCellUsers({ userId: 7, username: 'tech' }), [
    { id: 7, name: 'tech', avatarUrl: '' }
  ])
  assert.deepEqual(normalizeCellAttachments({ id: 1, fileName: '巡检.pdf', url: '/files/1' }), [
    { id: 1, name: '巡检.pdf', url: '/files/1' }
  ])
})

test('safeCellUrl rejects executable protocols', () => {
  assert.equal(safeCellUrl('javascript:alert(1)'), '')
  assert.equal(safeCellUrl('https://example.com/file.pdf'), 'https://example.com/file.pdf')
})

test('relation values use a backend-neutral display contract', () => {
  const relations = normalizeCellRelations([
    { buildingId: 12, buildingName: 'A座' },
    { buildingId: 18, buildingName: 'B座' }
  ], {
    type: 'relation',
    relation: {
      entityType: 'building',
      idField: 'buildingId',
      labelField: 'buildingName'
    }
  })

  assert.deepEqual(relations, [
    { id: 12, label: 'A座', entityType: 'building' },
    { id: 18, label: 'B座', entityType: 'building' }
  ])
})
