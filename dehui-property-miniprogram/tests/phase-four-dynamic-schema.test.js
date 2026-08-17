const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const schema = require('../utils/moduleFieldSchema')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

test('legacy file values normalize to versioned file references', () => {
  assert.deepEqual(schema.normalizeFileValue([{ id: 7 }, '/files/legacy.jpg']), {
    version: 1,
    files: [
      { id: 7, fileId: 7, sortOrder: 0 },
      { url: '/files/legacy.jpg', sortOrder: 1 }
    ]
  })
})

test('dynamic form renders all common types and blocks submission during upload', () => {
  const js = read('components/dynamic-form/index.js')
  const wxml = read('components/dynamic-form/index.wxml')
  for (const type of ['TEXTAREA', 'NUMBER', 'DATE', 'TIME', 'SELECT', 'MULTISELECT', 'BOOLEAN', 'IMAGE', 'ATTACHMENT']) {
    assert.match(wxml, new RegExp(type))
  }
  assert.match(js, /uploading/)
  assert.match(js, /buildSubmission/)
  assert.match(js, /selectDisplayValues/)
  assert.match(wxml, /displayValues\[item\.fieldKey\]/)
})

test('inspection workorder and visitor pages consume shared schema components', () => {
  for (const file of [
    'pages/workorders/index.wxml',
    'pages/visitors/index.wxml',
    'subPackages/staff/pages/inspections/form.wxml',
    'subPackages/staff/pages/workorders/process.wxml'
  ]) assert.match(read(file), /dynamic-form/)
  for (const file of [
    'subPackages/staff/pages/workorders/detail.wxml',
    'subPackages/staff/pages/visitors/detail.wxml'
  ]) assert.match(read(file), /dynamic-detail/)

  const inspectionDetail = read('subPackages/staff/pages/inspections/detail.wxml')
  const inspectionDetailSource = read('subPackages/staff/pages/inspections/detail.js')
  assert.match(inspectionDetail, /field-renderer/)
  assert.match(inspectionDetailSource, /normalizeFields/)
  assert.match(inspectionDetailSource, /fieldValue/)
  assert.match(inspectionDetailSource, /supplementalFields/)
})

test('dynamic image details download protected and relative files before rendering or previewing', () => {
  const js = read('components/field-renderer/index.js')
  const wxml = read('components/field-renderer/index.wxml')
  assert.match(js, /require\('\.\.\/\.\.\/utils\/request'\)/)
  assert.match(js, /api\.download/)
  assert.match(js, /value\.startsWith\('\/'\)/)
  assert.match(js, /previewUrl/)
  assert.match(wxml, /src="\{\{item\.previewUrl\}\}"/)
  assert.doesNotMatch(wxml, /src="\{\{item\.url\}\}"/)
})
