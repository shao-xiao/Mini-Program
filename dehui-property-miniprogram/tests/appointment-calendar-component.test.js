const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

test('shared calendar date field component exists for appointment flows', () => {
  const componentJson = read('components/calendar-date-field/index.json')
  const componentWxml = read('components/calendar-date-field/index.wxml')
  const componentJs = read('components/calendar-date-field/index.js')

  assert.match(componentJson, /"component"\s*:\s*true/)
  assert.match(componentWxml, /calendar-panel/)
  assert.match(componentWxml, /week-header/)
  assert.match(componentWxml, /prevMonth/)
  assert.match(componentWxml, /nextMonth/)
  assert.match(componentJs, /selectDay/)
  assert.match(componentJs, /buildCalendarDays/)
})

test('shared calendar date field renders visible navigation chevrons without escaped entity text', () => {
  const componentWxml = read('components/calendar-date-field/index.wxml')

  assert.doesNotMatch(componentWxml, /&lt;|&gt;/)
  assert.match(componentWxml, />‹<\/view>/)
  assert.match(componentWxml, />›<\/view>/)
})

test('visitor, investment, and meeting pages use shared calendar date field instead of native date picker', () => {
  const pageFiles = [
    ['pages/visitors/index.json', 'pages/visitors/index.wxml'],
    ['pages/investment/index.json', 'pages/investment/index.wxml'],
    ['pages/meeting/index.json', 'pages/meeting/index.wxml']
  ]

  for (const [jsonPath, wxmlPath] of pageFiles) {
    const pageJson = read(jsonPath)
    const pageWxml = read(wxmlPath)

    assert.match(pageJson, /calendar-date-field/)
    assert.match(pageWxml, /<calendar-date-field/)
    assert.doesNotMatch(pageWxml, /picker\s+mode="date"/)
  }
})
