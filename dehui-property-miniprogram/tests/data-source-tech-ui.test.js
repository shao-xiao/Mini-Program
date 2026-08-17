const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

const businessPages = [
  'announcements',
  'contracts',
  'parking',
  'meeting',
  'visitors',
  'workorders'
]

test('shared data source panel exposes source, scope and live status without fake AI claims', () => {
  const component = read('components/data-source-panel/index.wxml')
  const styles = read('components/data-source-panel/index.wxss')

  assert.match(component, /DHIC DATA SERVICE/)
  assert.match(component, /信息源/)
  assert.match(component, /数据范围/)
  assert.match(component, /\{\{source/)
  assert.match(component, /\{\{scope\}\}/)
  assert.match(styles, /linear-gradient/)
  assert.match(styles, /source-status-dot/)
  assert.doesNotMatch(component, /AI分析|智能预测|自动决策/)
})

test('tenant service pages register and render the shared traceable source panel', () => {
  for (const page of businessPages) {
    const config = JSON.parse(read(`pages/${page}/index.json`))
    const wxml = read(`pages/${page}/index.wxml`)

    assert.equal(
      config.usingComponents?.['data-source-panel'],
      '../../components/data-source-panel/index',
      `${page} must register the shared data source panel`
    )
    assert.match(wxml, /<data-source-panel\b/, `${page} must render a source panel`)
  }
})

test('page source labels map to existing backend business modules', () => {
  assert.match(read('pages/announcements/index.wxml'), /园区运营后台|园区公告中心/)
  assert.match(read('pages/contracts/index.wxml'), /租赁合同管理|合同档案/)
  assert.match(read('pages/parking/index.wxml'), /资产中心/)
  assert.match(read('pages/meeting/index.wxml'), /会议室资源|会议预约台账/)
  assert.match(read('pages/visitors/index.wxml'), /物业访客/)
  assert.match(read('pages/workorders/index.wxml'), /物业工单中心/)
})

test('dense information layouts keep contract and parking data in fixed grids', () => {
  assert.match(read('pages/contracts/index.wxml'), /contract-info-grid/)
  assert.match(read('pages/parking/index.wxml'), /parking-info-grid/)
})
