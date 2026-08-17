const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const capabilities = require(path.join(root, 'utils/capabilities'))
const staff = require(path.join(root, 'utils/staff'))

function inspectionListPresentation() {
  const filename = path.join(root, 'subPackages/staff/pages/inspections/index.js')
  const sandbox = {
    module: { exports: {} },
    exports: {},
    Page() {},
    require(request) {
      if (request.endsWith('utils/request')) return {}
      if (request.endsWith('utils/staff')) return {}
      throw new Error(`Unexpected inspection list dependency: ${request}`)
    }
  }
  vm.runInNewContext(
    `${fs.readFileSync(filename, 'utf8')}\nmodule.exports = { localizeInspectionPage }`,
    sandbox,
    { filename }
  )
  return sandbox.module.exports.localizeInspectionPage
}

test('STAFF home shows one real workbench only when a server capability exists', () => {
  const empty = capabilities.getVisibleHomeFeatures({
    userType: 'STAFF',
    capabilities: ['announcement.view', 'investment.view']
  }).map(item => item.key)
  assert.equal(empty.includes('staffWorkbench'), false)

  const allowed = capabilities.getVisibleHomeFeatures({
    userType: 'STAFF',
    capabilities: ['announcement.view', 'investment.view', 'inspection.staff.view']
  }).map(item => item.key)
  assert.equal(allowed.includes('staffWorkbench'), true)
})

test('workbench modules are independently capability guarded', () => {
  const profile = { userType: 'STAFF', capabilities: ['workorder.staff.view', 'visitor.staff.view'] }
  assert.equal(capabilities.canAccessFeature('staffWorkorder', profile), true)
  assert.equal(capabilities.canAccessFeature('staffVisitor', profile), true)
  assert.equal(capabilities.canAccessFeature('staffEnergy', profile), false)
  assert.equal(capabilities.canAccessFeature('staffInspection', profile), false)
})

test('six workbench cards have a fixed capability order and support the RBAC matrix', () => {
  const all = staff.staffModules({
    userType: 'STAFF',
    capabilities: [
      'inspection.staff.view',
      'workorder.staff.view',
      'energy.staff.view',
      'meeting.staff.view',
      'visitor.staff.view',
      'investment.lead.staff.view'
    ]
  }).map(item => item.title)
  assert.deepEqual(all, ['巡检记录', '报修工单', '能耗统计', '会议预约', '访客预约', '看房预约'])

  const security = staff.staffModules({
    userType: 'STAFF',
    capabilities: ['inspection.staff.view', 'visitor.staff.view']
  }).map(item => item.title)
  assert.deepEqual(security, ['巡检记录', '访客预约'])

  const electrician = staff.staffModules({
    userType: 'STAFF',
    capabilities: ['workorder.staff.view', 'energy.staff.view']
  }).map(item => item.title)
  assert.deepEqual(electrician, ['报修工单', '能耗统计'])

  const readonly = staff.staffModules({
    userType: 'STAFF',
    capabilities: ['meeting.staff.view', 'investment.lead.staff.view']
  }).map(item => item.title)
  assert.deepEqual(readonly, ['会议预约', '看房预约'])
  assert.deepEqual(staff.staffModules({ userType: 'STAFF', capabilities: [] }), [])

  const inspection = capabilities.FEATURE_DEFINITIONS.staffInspection
  assert.equal(inspection.title, '巡检记录')
  assert.equal(inspection.url, '/subPackages/staff/pages/inspections/index')
  assert.equal(inspection.capability, 'inspection.staff.view')
})

test('all STAFF routes are isolated in one subpackage', () => {
  const app = JSON.parse(read('app.json'))
  const staff = app.subPackages.find(item => item.root === 'subPackages/staff')
  assert.ok(staff)
  for (const route of [
    'pages/workbench/index',
    'pages/inspections/index',
    'pages/workorders/index',
    'pages/energy/index',
    'pages/meetings/index',
    'pages/visitors/index',
    'pages/investment/index'
  ]) assert.ok(staff.pages.includes(route), route)
})

test('deep links refresh me and fail closed through shared STAFF guard', () => {
  const guard = read('utils/staff.js')
  assert.match(guard, /refreshCurrentProfile/)
  assert.match(guard, /getUserType\(profile\) !== 'STAFF'/)
  assert.match(guard, /canAccessFeature\(featureKey, profile\)/)
  for (const page of [
    'inspections/index', 'workorders/index', 'energy/index',
    'meetings/index', 'visitors/index', 'investment/index'
  ]) {
    assert.match(read(`subPackages/staff/pages/${page}.js`), /requireStaffFeature/)
  }
})

test('workbench requests one authorized aggregate and never client-counts six lists', () => {
  const source = read('subPackages/staff/pages/workbench/index.js')
  assert.match(source, /api\.get\('\/mobile\/staff\/workbench'/)
  assert.doesNotMatch(source, /Promise\.all|\/mobile\/workorders|\/mobile\/meetings|\/mobile\/staff\/visitors/)
  assert.match(read('subPackages/staff/pages/workbench/index.wxml'), /暂无已授权移动功能/)
})

test('STAFF record lists use server pagination, pull refresh and bottom loading', () => {
  for (const page of ['inspections', 'workorders', 'energy', 'meetings', 'visitors', 'investment']) {
    const source = read(`subPackages/staff/pages/${page}/index.js`)
    assert.match(source, /pageSize:\s*20/, page)
    assert.match(source, /onPullDownRefresh/, page)
    assert.match(source, /onReachBottom/, page)
    assert.match(source, /mergePage/, page)
  }
  for (const page of ['inspections', 'workorders', 'meetings', 'visitors', 'investment']) {
    assert.match(read(`subPackages/staff/pages/${page}/index.js`), /compactQuery/, page)
  }
})

test('inspection form keeps a custom inspector but restores the configured type picker', () => {
  const source = read('subPackages/staff/pages/inspections/form.js')
  const template = read('subPackages/staff/pages/inspections/form.wxml')
  const dynamicForm = read('components/dynamic-form/index.wxml')
  const dynamicFormLogic = read('components/dynamic-form/index.js')
  assert.match(source, /loadSchema\('inspections'\)/)
  assert.match(source, /\/mobile\/staff\/inspection-options/)
  assert.match(source, /\/mobile\/staff\/inspection-types/)
  assert.match(source, /const typeOptions = normalizeOptions\(types\)/)
  assert.match(source, /field\.fieldKey === 'inspectionType'[\s\S]*dataType: 'SELECT'[\s\S]*options: typeOptions/)
  assert.match(source, /inspector:\s*''/)
  assert.doesNotMatch(source, /inspectorCode|inspectorOptions/)
  assert.match(source, /!form\.inspector\s*\|\|/)
  assert.match(source, /field\.fieldKey === 'area'/)
  assert.doesNotMatch(template, /wx:if="\{\{false\}\}"/)
  assert.match(dynamicForm, /item\.dataType === 'TEXT'[\s\S]*maxlength="\{\{item\.validation\.maxLength \|\| 140\}\}"/)
  assert.match(dynamicForm, /item\.dataType === 'SELECT'[\s\S]*range="\{\{item\.options\}\}"[\s\S]*bindchange="onPicker"/)
  assert.match(dynamicFormLogic, /onPicker\(event\)[\s\S]*option \? option\.value : ''/)
  assert.match(dynamicForm, /aria-label="\{\{item\.label\}\}"/)
  assert.match(source, /inspectionResult === 'ABNORMAL'/)
  assert.match(source, /attachmentFileIds/)
  assert.match(source, /normalizeFileValue\(payload\.images\)/)
  assert.doesNotMatch(source, /inspectorId|sysUserId|tenantId/)
})

test('inspection image upload keeps a local preview and submits schema image file ids', () => {
  const component = read('components/dynamic-form/index.js')
  const template = read('components/dynamic-form/index.wxml')
  const form = read('subPackages/staff/pages/inspections/form.js')

  assert.match(component, /previewUrl:\s*item\.path/)
  assert.match(component, /file\.previewUrl\s*\|\|\s*file\.url/)
  assert.match(template, /file\.previewUrl\s*\|\|\s*file\.url/)
  assert.match(form, /normalizeFileValue\(payload\.images\)\.files/)
  assert.match(form, /new Set/)
})

test('inspection list presents result and status with Chinese business labels', () => {
  const source = read('subPackages/staff/pages/inspections/index.js')
  const config = read('subPackages/staff/pages/inspections/index.json')
  const template = read('subPackages/staff/pages/inspections/index.wxml')
  const styles = read('subPackages/staff/pages/inspections/index.wxss')
  const globalStyles = read('app.wxss')
  const localize = inspectionListPresentation()

  assert.match(config, /"navigationBarTitleText":\s*"巡检记录"/)
  assert.match(template, /staff-header-title">巡检记录</)
  assert.doesNotMatch(`${config}\n${template}`, /日常巡检/)
  assert.match(template, /inspectionTypeName\s*\|\|\s*item\.inspectionType/)

  assert.match(source, /OPEN:\s*'待处理'/)
  assert.match(source, /CLOSED:\s*'已关闭'/)
  assert.match(source, /resultLabel:/)
  assert.match(source, /resultClass:/)
  assert.match(source, /statusLabel:/)
  assert.match(template, /\{\{item\.resultLabel\}\}/)
  assert.match(template, /\{\{item\.statusLabel\}\}/)
  assert.match(template, /class="staff-status inspection-result \{\{item\.resultClass\}\}"/)
  assert.doesNotMatch(template, /\{\{item\.inspectionResult\}\}/)
  assert.doesNotMatch(template, /\{\{item\.status\}\}/)

  const cases = [
    { field: 'inspectionResult', value: 'NORMAL', label: '正常', className: 'inspection-result--normal' },
    { field: 'result', value: '正常', label: '正常', className: 'inspection-result--normal' },
    { field: 'result', value: 'ABNORMAL', label: '异常', className: 'inspection-result--abnormal' },
    { field: 'inspectionResult', value: '异常', label: '异常', className: 'inspection-result--abnormal' },
    { field: 'inspectionResult', value: 'PENDING_REVIEW', label: '结果待确认', className: '' },
    { field: 'result', value: 'NORMALIZED', label: '结果待确认', className: '' },
    { field: 'result', value: 'NOT_ABNORMAL', label: '结果待确认', className: '' }
  ]
  for (const item of cases) {
    const record = localize({
      records: [{
        [item.field]: item.value,
        status: item.className === 'inspection-result--abnormal' ? 'OPEN' : 'CLOSED'
      }]
    }).records[0]
    assert.equal(record.resultLabel, item.label, item.value)
    assert.equal(record.resultClass, item.className, item.value)
    assert.equal(
      record.statusLabel,
      item.className === 'inspection-result--abnormal' ? '待处理' : '已关闭',
      item.value
    )
  }

  assert.match(styles, /\.inspection-result--normal\s*\{[\s\S]*?var\(--dhic-success-soft\)[\s\S]*?var\(--dhic-success\)/)
  assert.match(styles, /\.inspection-result--abnormal\s*\{[\s\S]*?var\(--dhic-danger-soft\)[\s\S]*?var\(--dhic-danger\)/)
  assert.doesNotMatch(styles, /\.inspection-result--default\s*\{/)
  assert.match(globalStyles, /\.staff-status\s*\{[\s\S]*?var\(--dhic-brand-soft\)[\s\S]*?var\(--dhic-brand-primary\)/)
})

test('workorder STAFF flow cannot submit an assignee or operator', () => {
  const detail = read('subPackages/staff/pages/workorders/detail.js')
  const process = read('subPackages/staff/pages/workorders/process.js')
  assert.match(detail, /\/mobile\/workorders\/\$\{this\.data\.id\}\/start/)
  assert.match(detail, /\/mobile\/staff\/workorders\/\$\{this\.data\.id\}\/records/)
  assert.match(detail, /uploadInternalImages/)
  assert.match(detail, /attachmentFileIds/)
  assert.match(detail, /previewRecordImage/)
  assert.match(process, /\/mobile\/staff\/workorders\/\$\{this\.data\.id\}\/records/)
  assert.match(process, /\/mobile\/workorders\/\$\{this\.data\.id\}\/complete/)
  assert.doesNotMatch(`${detail}\n${process}`, /assigneeId|operatorId|sysUserId/)
})

test('inspection detail shows protected photos and appends feedback without replacing history', () => {
  const detail = read('subPackages/staff/pages/inspections/detail.js')
  const template = read('subPackages/staff/pages/inspections/detail.wxml')
  assert.match(detail, /\/mobile\/staff\/inspections\/\$\{this\.data\.id\}/)
  assert.match(detail, /normalizeFileValue/)
  assert.match(detail, /deduplicateFiles/)
  assert.match(detail, /supplementalFields/)
  assert.match(detail, /feedbackLength/)
  assert.match(detail, /actionLabel/)
  assert.match(template, /class="info-table"/)
  assert.match(template, /class="photo-section"/)
  assert.match(template, /现场照片/)
  assert.match(template, /field-renderer/)
  assert.doesNotMatch(template, /现场反馈/)
  assert.match(template, /追加到处理历史，不覆盖原备注和处理措施/)
  assert.match(template, /保存反馈/)
})

test('inspection feedback uploads and renders resolution images per history entry', () => {
  const detail = read('subPackages/staff/pages/inspections/detail.js')
  const template = read('subPackages/staff/pages/inspections/detail.wxml')
  assert.match(detail, /uploadInternalImages/)
  assert.match(detail, /resolutionFiles/)
  assert.match(detail, /resolutionFileIds/)
  assert.match(detail, /chooseResolutionImages/)
  assert.match(detail, /removeResolutionImage/)
  assert.match(detail, /resolutionImages/)
  assert.match(template, /处理后照片/)
  assert.match(template, /bindtap="chooseResolutionImages"/)
  assert.match(template, /bindtap="removeResolutionImage"/)
  assert.match(template, /item\.resolutionImages/)
  assert.match(template, /field-renderer/)
})

test('energy reading sends raw reading but never computes usage or fee', () => {
  const source = read('subPackages/staff/pages/energy/reading-form.js')
  assert.match(source, /loadSchema\('energy-records'\)/)
  assert.match(source, /currentReading/)
  assert.doesNotMatch(source, /usageAmount\s*=|unitPrice\s*=|settlementAmount\s*=/)
  assert.doesNotMatch(source, /operatorId|sysUserId|tenantId/)
})

test('meeting management exposes only backend-confirmed operations', () => {
  const source = read('subPackages/staff/pages/meetings/detail.js')
  const template = read('subPackages/staff/pages/meetings/detail.wxml')
  assert.match(source, /meeting\.staff\.confirm/)
  assert.match(source, /meeting\.staff\.cancel/)
  assert.match(source, /meeting\.staff\.complete/)
  assert.doesNotMatch(`${source}\n${template}`, /收款|手工改价|审批/)
})

test('visitor page uses masked phone and permission-gated verification', () => {
  const source = read('subPackages/staff/pages/visitors/detail.js')
  const template = read('subPackages/staff/pages/visitors/detail.wxml')
  assert.match(source, /visitor\.staff\.verify/)
  assert.match(template, /maskedPhone/)
  assert.match(template, /data-action="enter"/)
  assert.match(template, /data-action="leave"/)
})

test('viewing appointments reuse investment leads and follow endpoints', () => {
  const list = read('subPackages/staff/pages/investment/index.js')
  const detail = read('subPackages/staff/pages/investment/detail.js')
  assert.match(list, /\/mobile\/staff\/investment-leads/)
  assert.match(detail, /\/follow/)
  assert.match(detail, /\/status/)
})

test('all third-stage images use authenticated internal file transport', () => {
  const staff = read('utils/staff.js')
  assert.match(staff, /ensurePrivacyAuthorization\('staffInternalImage'\)/)
  assert.match(staff, /api\.upload\('\/mobile\/staff\/files'/)
  assert.match(staff, /api\.download/)
  assert.match(staff, /function customFileValue/)
  for (const page of [
    'subPackages/staff/pages/inspections/form.js',
    'subPackages/staff/pages/workorders/process.js',
    'subPackages/staff/pages/energy/reading-form.js'
  ]) {
    assert.match(read(page), /uploadInternalImages/, page)
    assert.match(read(page), /chooseCustomImages/, page)
    assert.match(read(page), /customFileValue/, page)
    assert.doesNotMatch(read(page), /files\/public/, page)
  }
  const inspectionTemplate = read('subPackages/staff/pages/inspections/form.wxml')
  const dynamicFormTemplate = read('components/dynamic-form/index.wxml')
  assert.match(inspectionTemplate, /<dynamic-form/)
  assert.match(dynamicFormTemplate, /dataType === 'IMAGE'/)
  assert.match(dynamicFormTemplate, /previewFile/)
  for (const page of [
    'subPackages/staff/pages/workorders/process.wxml',
    'subPackages/staff/pages/energy/reading-form.wxml'
  ]) {
    assert.match(read(page), /dataType === 'IMAGE'/, page)
    assert.match(read(page), /previewCustomImage/, page)
  }
})
