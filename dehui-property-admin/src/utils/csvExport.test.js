import test from 'node:test'
import assert from 'node:assert/strict'
import { buildCsvText, escapeCsvCell } from './csvExport.js'

test('escapeCsvCell quotes commas, line breaks and double quotes', () => {
  assert.equal(escapeCsvCell('设备,一号'), '"设备,一号"')
  assert.equal(escapeCsvCell('一行\n二行'), '"一行\n二行"')
  assert.equal(escapeCsvCell('型号"A"'), '"型号""A"""')
  assert.equal(escapeCsvCell('=1+1'), "'=1+1")
  assert.equal(escapeCsvCell(-12), '-12')
})

test('buildCsvText exports configured columns in display order', () => {
  const text = buildCsvText(
    [{ name: '空调机组', status: 'NORMAL' }],
    [
      { label: '序号', valueGetter: (_row, index) => index + 1 },
      { key: 'name', label: '设备名称' },
      { label: '状态', valueGetter: row => row.status === 'NORMAL' ? '正常' : row.status }
    ]
  )

  assert.equal(text, '序号,设备名称,状态\r\n1,空调机组,正常')
})
