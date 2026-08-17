import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { buildEquipmentImportRows, mapEquipmentImportRow, normalizeImportHeader } from '../src/utils/equipmentImportMapping.js'

const root = path.resolve(process.cwd())

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

test('equipment ledger exposes csv template export and import actions', () => {
  const source = read('src/views/building/EquipmentList.vue')
  const config = read('src/workspaceConfigs/equipment.js')
  const manager = read('src/components/ImportExportManager.vue')

  assert.match(config, /label: '导出模板'/)
  assert.match(config, /label: '导入设备'/)
  assert.match(manager, /type="file"/)
  assert.match(config, /accept: '[^']*\.csv[^']*text\/csv/)
  assert.match(source, /request\.get\('\/equipments\/template'/)
  assert.match(source, /responseType:\s*'blob'/)
  assert.match(source, /request\.post\('\/equipments\/import'/)
  assert.match(source, /insertedCount/)
  assert.match(source, /updatedCount/)
  assert.match(source, /restoredCount/)
  assert.match(source, /导入完成：新增/)
  assert.match(source, /恢复/)
})

test('equipment import maps major handover columns to equipment payload fields', () => {
  const componentSource = read('src/views/building/EquipmentList.vue')
  const configSource = read('src/workspaceConfigs/equipment.js')
  const mappingSource = read('src/utils/equipmentImportMapping.js')
  const source = mappingSource

  assert.match(componentSource, /buildEquipmentImportRows/)
  assert.match(componentSource, /equipmentImportMapping/)
  assert.match(source, /设备\/材料名称/)
  assert.match(source, /固定资产编码/)
  assert.match(source, /厂家联系方式/)
  assert.match(source, /质保期/)
  assert.match(source, /所属系统/)
  assert.match(mappingSource, /equipmentName/)
  assert.match(mappingSource, /assetCode/)
  assert.match(mappingSource, /manufacturerContact/)
  assert.match(mappingSource, /warrantyPeriod/)
  assert.match(mappingSource, /systemName/)
  assert.match(componentSource, /activeCustomFields/)
  assert.match(componentSource, /saveCustomFieldRecord/)
  assert.doesNotMatch(componentSource, /listCustomFieldValues\('equipment'/)
  assert.doesNotMatch(componentSource, /mergeCustomFieldValues/)
  assert.match(configSource, /custom_quantity/)
  assert.match(configSource, /custom_manufacturer_contact/)
  assert.match(configSource, /custom_warranty_period/)
})

test('equipment import maps messy Excel handover headers to normal values', () => {
  assert.equal(normalizeImportHeader('  \ufeff设备 / 材料名称\n'), '设备材料名称')
  assert.equal(normalizeImportHeader('设备／材料名称'), '设备材料名称')
  assert.equal(normalizeImportHeader('厂家联系方式（电话）'), '厂家联系方式电话')

  const mapped = mapEquipmentImportRow(
    [
      ' 序号 ',
      '设备 / 材料名称',
      '规格 型号',
      '数量\n单位',
      '安装位置',
      '生产 厂家',
      '厂家联系方式（电话）',
      '质保期限',
      '所属 系统',
      '备注',
      '固定资产编号'
    ],
    [
      ' 1 ',
      ' 排气扇EF02 ',
      ' 风量250m3/h，功率32W ',
      ' 10 ',
      ' 备配电间 ',
      ' 上虞清明风机有限公司 ',
      ' 李明洋-18689442292 ',
      ' 2025年6月18日-2027年6月18日 ',
      ' 通风 ',
      ' ',
      ' CZXXTF001007 '
    ]
  )

  assert.deepEqual(mapped, {
    sequence: '1',
    equipmentName: '排气扇EF02',
    model: '风量250m3/h，功率32W',
    quantity: '10',
    location: '备配电间',
    manufacturer: '上虞清明风机有限公司',
    manufacturerContact: '李明洋-18689442292',
    warrantyPeriod: '2025年6月18日-2027年6月18日',
    systemName: '通风',
    remark: '',
    assetCode: 'CZXXTF001007',
    equipmentCode: 'CZXXTF001007'
  })
})

test('equipment import maps full-width slash equipment name header', () => {
  const mapped = mapEquipmentImportRow(
    ['序号', '设备／材料名称', '规格型号', '数量', '安装位置', '所属系统', '固定资产编码'],
    ['6', '消防稳压泵', 'XBD3.0/1.0W-DFCL-2', '2', '屋面稳压泵房', '消防', 'CZXXTF000306']
  )

  assert.equal(mapped.sequence, '6')
  assert.equal(mapped.equipmentName, '消防稳压泵')
  assert.equal(mapped.model, 'XBD3.0/1.0W-DFCL-2')
  assert.equal(mapped.quantity, '2')
  assert.equal(mapped.location, '屋面稳压泵房')
  assert.equal(mapped.systemName, '消防')
  assert.equal(mapped.equipmentCode, 'CZXXTF000306')
})

test('equipment import preserves unknown headers for platform custom-field matching', () => {
  const mapped = mapEquipmentImportRow(
    ['设备名称', '固定资产编码', '客户等级', 'custom_review_date'],
    ['空调主机', 'EQ-100', '重点', '2026-08-01']
  )

  assert.equal(mapped.equipmentName, '空调主机')
  assert.equal(mapped.equipmentCode, 'EQ-100')
  assert.equal(mapped['客户等级'], '重点')
  assert.equal(mapped.custom_review_date, '2026-08-01')
})

test('equipment import detects header row after title and blank rows', () => {
  const importRows = buildEquipmentImportRows([
    ['德汇创新中心设备移交清单'],
    ['', '', '', ''],
    ['填报日期', '2026-06-30'],
    [
      '序号',
      '设备 / 材料名称',
      '规格 型号',
      '数量',
      '安装位置',
      '生产厂家',
      '厂家联系方式',
      '质保期',
      '所属系统',
      '备注',
      '固定资产编号'
    ],
    [
      '1',
      '排气扇EF02',
      '风量250m3/h，功率32W',
      '10',
      '备配电间',
      '上虞清明风机有限公司',
      '李明洋-18689442292',
      '2025年6月18日-2027年6月18日',
      '通风',
      '',
      'CZXXTF001007'
    ]
  ])

  assert.equal(importRows.length, 1)
  assert.equal(importRows[0].equipmentName, '排气扇EF02')
  assert.equal(importRows[0].equipmentCode, 'CZXXTF001007')
  assert.equal(importRows[0].quantity, '10')
  assert.equal(importRows[0].manufacturerContact, '李明洋-18689442292')
})

test('equipment import accepts Excel workbook files', () => {
  const source = read('src/views/building/EquipmentList.vue')
  const config = read('src/workspaceConfigs/equipment.js')
  const packageJson = JSON.parse(read('package.json'))

  assert.match(config, /accept: '[^']*\.xlsx/)
  assert.doesNotMatch(config, /accept: '[^']*\.xls[,']/)
  assert.doesNotMatch(source, /import\s+JSZip\s+from\s+['"]jszip['"]/)
  assert.doesNotMatch(source, /await import\('jszip'\)/)
  assert.match(source, /jszip\/dist\/jszip\.min\.js\?url/)
  assert.match(source, /async function loadJsZip/)
  assert.match(source, /document\.createElement\('script'\)/)
  assert.match(source, /window\.JSZip/)
  assert.match(source, /file\.arrayBuffer\(\)/)
  assert.match(source, /parseXlsx/)
  assert.match(source, /worksheetPaths/)
  assert.doesNotMatch(source, /firstWorksheetPath/)
  assert.equal(packageJson.dependencies.jszip, '^3.10.1')
  assert.equal(packageJson.dependencies.xlsx, undefined)
})

test('equipment ledger supports multi-select batch delete', () => {
  const source = read('src/views/building/EquipmentList.vue')
  const grid = read('src/components/workspace/WorkspaceDataGrid.vue')

  assert.match(source, /<WorkspaceDataGrid[\s\S]*show-selection/)
  assert.match(grid, /type="selection"/)
  assert.match(source, /@selection-change="handleSelectionChange"/)
  assert.match(source, /selectedRows/)
  assert.match(source, /batchDeleteEquipment/)
  assert.match(source, /批量删除/)
  assert.match(source, /Promise\.allSettled/)
  assert.match(source, /request\.delete\(`\/equipments\/\$\{row\.id\}`\)/)
})
