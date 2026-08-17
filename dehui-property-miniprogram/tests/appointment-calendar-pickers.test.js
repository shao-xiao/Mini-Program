const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')

test('visitor reservation uses shared calendar date field plus time picker', () => {
  const wxml = fs.readFileSync(path.join(root, 'pages/visitors/index.wxml'), 'utf8')
  const js = fs.readFileSync(path.join(root, 'pages/visitors/index.js'), 'utf8')

  assert.match(wxml, /calendar-date-field/)
  assert.match(wxml, /calendar-time-field/)
  assert.doesNotMatch(wxml, /picker\s+mode="time"/)
  assert.match(js, /visitDate/)
  assert.match(js, /visitClock/)
  assert.match(js, /visitTime:\s*toApiDateTime\(form\.visitDate,\s*form\.visitClock\)/)
})

test('investment lead appointment uses shared calendar date field plus time picker', () => {
  const wxml = fs.readFileSync(path.join(root, 'pages/investment/index.wxml'), 'utf8')
  const js = fs.readFileSync(path.join(root, 'pages/investment/index.js'), 'utf8')

  assert.match(wxml, /calendar-date-field/)
  assert.match(wxml, /calendar-time-field/)
  assert.doesNotMatch(wxml, /picker\s+mode="time"/)
  assert.match(js, /preferredVisitDate/)
  assert.match(js, /preferredVisitClock/)
  assert.match(js, /preferredVisitTime:\s*buildPreferredVisitTime\(form\.preferredVisitDate,\s*form\.preferredVisitClock\)/)
})

test('meeting appointment uses shared calendar date field and keeps time slot selection', () => {
  const wxml = fs.readFileSync(path.join(root, 'pages/meeting/index.wxml'), 'utf8')
  const js = fs.readFileSync(path.join(root, 'pages/meeting/index.js'), 'utf8')

  assert.match(wxml, /calendar-date-field/)
  assert.match(wxml, /calendar-time-field/)
  assert.doesNotMatch(wxml, /picker\s+mode="time"/)
  assert.match(js, /combineDateTime\(this\.data\.form\.date,\s*this\.data\.form\.startTime\)/)
  assert.match(js, /combineDateTime\(this\.data\.form\.date,\s*this\.data\.form\.endTime\)/)
})
