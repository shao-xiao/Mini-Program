const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const vm = require('node:vm')

const root = path.resolve(__dirname, '..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

function loadBusinessDetailPage(detailResponse, download, wxOverrides = {}) {
  let definition
  const filename = path.join(root, 'pages/business-detail/index.js')
  const source = fs.readFileSync(filename, 'utf8')
  const getDetail = typeof detailResponse === 'function'
    ? detailResponse
    : async () => detailResponse

  vm.runInNewContext(source, {
    Page(value) {
      definition = value
    },
    require(request) {
      if (request === '../../utils/request') {
        return {
          get: getDetail,
          ...(download ? { download } : {})
        }
      }
      if (request === '../../utils/energyDetailPresentation') {
        return { presentEnergyDetailRows: () => [] }
      }
      if (request === '../../utils/moduleFieldSchema') {
        return {
          SURFACES: { detail: 'MINIPROGRAM_DETAIL' },
          loadModuleFieldSchema: async () => ({ fields: [] })
        }
      }
      if (request === '../../utils/workorderDetailPresentation') {
        return require(path.join(root, 'utils/workorderDetailPresentation'))
      }
      throw new Error(`Unexpected dependency: ${request}`)
    },
    console,
    Set,
    wx: {
      setNavigationBarTitle() {},
      showToast() {},
      openDocument(options) {
        if (options && options.success) options.success()
      },
      ...wxOverrides
    }
  }, { filename })

  const page = {
    ...definition,
    type: 'workorder',
    id: Number(detailResponse && detailResponse.id) || 42,
    _active: true,
    data: structuredClone(definition.data),
    setData(update) {
      Object.assign(this.data, update)
    }
  }
  return page
}

test('workorder detail localizes every field and presents photos, records, and a top history entry', async () => {
  const page = loadBusinessDetailPage({
    id: 42,
    orderNumber: 'WO-20260730-0042',
    title: '配电箱跳闸',
    orderType: 'REPAIR',
    category: 'ELECTRIC',
    priority: 'HIGH',
    status: 'PROCESSING',
    location: 'A座 8层',
    description: '办公室配电箱反复跳闸',
    reporterName: '测试租户',
    reporterPhone: '13900000000',
    companyName: '德汇测试企业',
    reportedAt: '2026-07-30T09:30:00',
    attachments: [
      { id: 101, name: '现场一.jpg', url: '/api/mobile/files/101', mimeType: 'image/jpeg' },
      { id: 102, name: '现场二.png', url: '/api/mobile/files/102', mimeType: 'image/png' }
    ],
    images: [
      { id: 101, name: '现场一.jpg', url: '/api/mobile/files/101', mimeType: 'image/jpeg' },
      { id: 102, name: '现场二.png', url: '/api/mobile/files/102', mimeType: 'image/png' }
    ],
    records: [
      {
        id: 201,
        action: 'CREATE',
        content: '租户提交报修',
        operatorName: '测试租户',
        createdTime: '2026-07-30T09:30:00',
        attachments: []
      },
      {
        id: 202,
        action: 'PROGRESS',
        content: '工程人员已到场排查',
        operatorName: '工程人员',
        createdTime: '2026-07-30T10:20:00',
        attachments: [
          { id: 103, name: '处理现场.jpg', url: '/api/mobile/files/103', mimeType: 'image/jpeg' }
        ]
      }
    ]
  })

  await page.loadDetail()

  const rows = page.data.rows || []
  const collections = Object.entries(page.data)
  const photoCollection = collections.find(([key, value]) =>
    key !== 'rows'
    && /image|photo|attachment/i.test(key)
    && Array.isArray(value)
    && value.length === 2
    && value.every(item => item && item.url)
  )
  const recordCollection = collections.find(([key, value]) =>
    /record|history/i.test(key)
    && Array.isArray(value)
    && value.length === 2
    && value.every(item => item && item.createdTime)
  )
  const rowValues = Object.fromEntries(rows.map(item => [item.key, item.value]))
  const detailMarkup = read('pages/business-detail/index.wxml')
  const workorderMarkup = read('pages/workorders/index.wxml')
  const workorderStyle = read('pages/workorders/index.wxss')

  const presentationContract = {
    chineseLabelsOnly: rows.length > 0 && rows.every(item =>
      /[\u4e00-\u9fff]/.test(String(item.label))
      && !/^[a-z][a-z0-9]*$/i.test(String(item.label))
    ),
    chineseEnumsOnly: rowValues.orderType === '维修报修'
      && rowValues.category === '电路'
      && rowValues.priority === '较高'
      && rowValues.status === '处理中',
    structuredFieldsExcludedFromRows: ['attachments', 'images', 'records']
      .every(key => !rows.some(item => item.key === key || String(item.value).includes(`"${key}"`))),
    photosRenderedAsGrid: Boolean(
      photoCollection
      && detailMarkup.includes(`wx:for="{{${photoCollection[0]}}}"`)
      && /<image\b/.test(detailMarkup)
      && /class="[^"]*(?:image|photo)[^"]*grid[^"]*"/i.test(detailMarkup)
    ),
    recordsStackedByTime: Boolean(
      recordCollection
      && detailMarkup.includes(`wx:for="{{${recordCollection[0]}}}"`)
      && recordCollection[1][0].createdTime > recordCollection[1][1].createdTime
      && recordCollection[1].every(item =>
        /[\u4e00-\u9fff]/.test(String(item.actionText || item.actionLabel || ''))
      )
    ),
    topRightHistoryEntry: /class="workorder-history-query"[\s\S]*?查询历史记录/.test(workorderMarkup)
      && /\.workorder-history-query\s*\{[\s\S]*?margin-left:\s*auto/.test(workorderStyle)
  }

  assert.deepEqual(presentationContract, {
    chineseLabelsOnly: true,
    chineseEnumsOnly: true,
    structuredFieldsExcludedFromRows: true,
    photosRenderedAsGrid: true,
    recordsStackedByTime: true,
    topRightHistoryEntry: true
  })
})

test('workorder presentation keeps report, custom, and handling photos in separate Chinese groups', () => {
  const { presentWorkOrderDetail } = require('../utils/workorderDetailPresentation')
  const file36 = {
    fileId: 36,
    name: '报修现场.jpg',
    url: '/api/mobile/files/36',
    mimeType: 'image/jpeg'
  }
  const file37 = {
    fileId: 37,
    name: '补充图片.png',
    url: '/api/mobile/files/37',
    mimeType: 'image/png'
  }
  const file38 = {
    fileId: 38,
    name: '处理结果.jpg',
    url: '/api/mobile/files/38',
    mimeType: 'image/jpeg'
  }

  const presentation = presentWorkOrderDetail({
    id: 42,
    orderNumber: 'WO-20260730-0042',
    status: 'PROCESSING',
    images: [file36],
    attachments: [file36],
    customFields: {
      repairSupplementImages: {
        version: 1,
        files: [file37]
      }
    },
    records: [{
      id: 202,
      action: 'PROGRESS',
      content: '工程人员已完成现场处理',
      createdTime: '2026-07-30T10:20:00',
      attachments: [file38]
    }]
  }, {
    fields: [{
      fieldKey: 'repairSupplementImages',
      apiField: 'repairSupplementImages',
      label: '补充现场照片',
      dataType: 'IMAGE',
      source: 'CUSTOM',
      order: 90
    }]
  })

  const reportGroup = {
    label: presentation.workorderImageLabel,
    files: presentation.workorderImages
  }
  const customGroup = presentation.customImageGroups[0]
  const handlingGroup = presentation.workorderRecords[0]
  const fileIdsByGroup = [
    reportGroup.files.map(file => file.fileId),
    customGroup.files.map(file => file.fileId),
    handlingGroup.images.map(file => file.fileId)
  ]
  const allFileIds = fileIdsByGroup.flat()
  const businessTitles = [
    reportGroup.label,
    customGroup.label,
    handlingGroup.imageGroupLabel
  ]

  assert.deepEqual(fileIdsByGroup, [[36], [37], [38]])
  assert.equal(new Set(allFileIds).size, 3)
  assert.deepEqual(businessTitles, ['报修现场照片', '补充现场照片', '处理记录照片'])
  assert.ok(businessTitles.every(title => /[\u4e00-\u9fff]/.test(title)))
  assert.equal(
    presentation.rows.some(row =>
      ['attachments', 'images', 'records'].includes(row.key)
      || /^[\[{]/.test(String(row.value).trim())
    ),
    false
  )
})

test('workorder custom values stay Chinese and legacy image metadata remains visible', () => {
  const {
    presentWorkOrderDetail,
    documentFileType,
    isImageFile
  } = require('../utils/workorderDetailPresentation')
  const legacyImage = {
    fileId: 88,
    name: '历史文件',
    url: 'https://legacy.example.invalid/photo.jpg'
  }
  const presentation = presentWorkOrderDetail({
    id: 42,
    status: 'PROCESSING',
    customFields: {
      riskTypes: ['URGENT', 'SAFETY'],
      powerIsolated: true,
      legacyPhotos: { version: 1, files: [legacyImage] }
    }
  }, {
    fields: [
      {
        fieldKey: 'riskTypes',
        label: '风险类型',
        dataType: 'MULTISELECT',
        source: 'CUSTOM',
        options: [
          { value: 'URGENT', label: '紧急风险' },
          { value: 'SAFETY', label: '安全风险' }
        ]
      },
      {
        fieldKey: 'powerIsolated',
        label: '是否断电',
        dataType: 'BOOLEAN',
        source: 'CUSTOM'
      },
      {
        fieldKey: 'legacyPhotos',
        label: '历史现场照片',
        dataType: 'IMAGE',
        source: 'CUSTOM'
      }
    ]
  })
  const values = Object.fromEntries(presentation.customRows.map(row => [row.label, row.value]))

  assert.equal(values.风险类型, '紧急风险、安全风险')
  assert.equal(values.是否断电, '是')
  assert.deepEqual(presentation.customImageGroups[0].files.map(file => file.fileId), [88])
  assert.equal(isImageFile(legacyImage), true)
  assert.equal(documentFileType('维修记录.xlsx'), 'xlsx')
  assert.equal(documentFileType('现场说明.pptx'), 'pptx')
  assert.equal(documentFileType('未知附件.bin'), '')
})

test('workorder detail paints Chinese content before protected image hydration finishes', async () => {
  const neverFinishes = () => new Promise(() => {})
  const page = loadBusinessDetailPage({
    id: 42,
    orderNumber: 'WO-0042',
    title: '配电箱跳闸',
    status: 'PROCESSING',
    images: [{
      fileId: 101,
      name: '现场照片.jpg',
      url: '/api/mobile/files/101',
      mimeType: 'image/jpeg'
    }],
    records: []
  }, neverFinishes)

  const outcome = await Promise.race([
    page.loadDetail().then(() => 'painted'),
    new Promise(resolve => setTimeout(() => resolve('blocked'), 50))
  ])

  assert.equal(outcome, 'painted')
  assert.equal(page.data.pageState, 'success')
  assert.equal(page.data.workorder.title, '配电箱跳闸')
  assert.equal(page.data.workorderImages.length, 1)
  assert.equal(page.data.workorderImages[0].previewUrl, undefined)
  assert.match(read('pages/business-detail/index.js'), /temporary:\s*true/)
})

test('workorder detail ignores an older response after a retry completes', async () => {
  const requests = []
  const page = loadBusinessDetailPage(() => new Promise((resolve, reject) => {
    requests.push({ resolve, reject })
  }))

  const olderLoad = page.loadDetail()
  const newerLoad = page.loadDetail()
  requests[1].resolve({
    id: 42,
    orderNumber: 'WO-NEW',
    title: '最新工单详情',
    status: 'PROCESSING',
    records: []
  })
  await newerLoad
  requests[0].resolve({
    id: 42,
    orderNumber: 'WO-OLD',
    title: '过期工单详情',
    status: 'CREATED',
    records: []
  })
  await olderLoad

  assert.equal(page.data.workorder.orderNumber, 'WO-NEW')
  assert.equal(page.data.workorder.title, '最新工单详情')
  assert.equal(page.data.pageState, 'success')
})

test('workorder detail ignores a rejected request after the page unloads', async () => {
  let rejectRequest
  const page = loadBusinessDetailPage(() => new Promise((resolve, reject) => {
    rejectRequest = reject
  }))

  const loading = page.loadDetail()
  page.onUnload()
  rejectRequest(new Error('旧请求失败'))
  await loading

  assert.equal(page.data.pageState, 'loading')
  assert.equal(page.data.workorder, null)
  assert.equal(page.data.errorMessage, '')
})

test('workorder attachment does not open after the detail page unloads', async () => {
  let resolveDownload
  const openedDocuments = []
  const page = loadBusinessDetailPage(
    { id: 42, status: 'PROCESSING', records: [] },
    () => new Promise(resolve => {
      resolveDownload = resolve
    }),
    {
      openDocument(options) {
        openedDocuments.push(options.filePath)
        options.success()
      }
    }
  )
  page.data.workorderFiles = [{
    fileId: 66,
    name: '维修说明.pdf',
    url: '/api/mobile/files/66'
  }]

  const opening = page.openWorkOrderFile({
    currentTarget: {
      dataset: {
        scope: 'report-files',
        index: 0
      }
    }
  })
  page.onUnload()
  resolveDownload('wxfile://temporary/维修说明.pdf')
  await opening

  assert.deepEqual(openedDocuments, [])
})
