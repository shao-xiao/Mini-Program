const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

function read(filePath) {
  return fs.readFileSync(path.join(root, filePath), 'utf8')
}

test('shared calendar time field component exists for appointment flows', () => {
  const componentJson = read('components/calendar-time-field/index.json')
  const componentWxml = read('components/calendar-time-field/index.wxml')
  const componentJs = read('components/calendar-time-field/index.js')

  assert.match(componentJson, /"component"\s*:\s*true/)
  assert.match(componentWxml, /picker-view/)
  assert.match(componentWxml, /confirmTime/)
  assert.match(componentJs, /buildMinuteOptions/)
  assert.match(componentJs, /confirmTime/)
})

test('appointment pages register the shared time field component', () => {
  const pageFiles = [
    ['pages/visitors/index.json', 'pages/visitors/index.wxml'],
    ['pages/investment/index.json', 'pages/investment/index.wxml'],
    ['pages/meeting/index.json', 'pages/meeting/index.wxml']
  ]

  for (const [jsonPath, wxmlPath] of pageFiles) {
    const pageJson = read(jsonPath)
    const pageWxml = read(wxmlPath)

    assert.match(pageJson, /calendar-time-field/)
    assert.match(pageWxml, /<calendar-time-field/)
  }
})
