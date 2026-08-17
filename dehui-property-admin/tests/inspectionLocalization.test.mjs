import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

test('inspection list and detail enrich backend codes with Chinese labels', () => {
  const source = read('src/views/operation/InspectionList.vue')
  const schemaInput = read('src/components/schema/SchemaFieldInput.vue')
  const workspaceSource = read('src/workspaceConfigs/operationWorkspaceConfig.js')
  assert.match(source, /detailSchemaFieldsWithOptions/)
  assert.match(source, /inspectionTypeSelectOptions/)
  assert.match(source, /schemaFieldsWithOptions/)
  assert.match(source, /currentInspectionTypeOption/)
  assert.match(source, /String\(item\.code \|\| ''\)\.trim\(\)\.length <= 32/)
  assert.match(schemaInput, /field\.dataType === 'SELECT'/)
  assert.match(source, /inspectionStatusOptions/)
  assert.match(source, /loadInspectionTypes\(\)[\s\S]*loadInspections\(\)/)
  assert.match(
    workspaceSource,
    /key:\s*'inspectionType'[\s\S]*options:\s*\(\)\s*=>\s*inspectionTypeOptions/,
    '巡检类型列应动态读取后台字典，不能在页面初始化时固化空选项'
  )
  assert.match(
    workspaceSource,
    /inspectionTypeOptions\s*=\s*reactive\(\[\]\)/,
    '巡检类型字典必须是响应式数据，接口返回后表格才能立即更新中文标签'
  )
})
