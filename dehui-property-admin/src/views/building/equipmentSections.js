import { equipmentIllustrationKind } from './equipmentIllustrations.js'

const collator = new Intl.Collator('zh-CN', { numeric: true })
const folderSections = [
  ['楼宇机电', '建筑运行、能源供应与给排水', ['ELEVATOR', 'HVAC', 'POWER', 'WATER', '通风与净化', '照明']],
  ['安全与通行', '消防防护、出入管理与车辆充电', ['FIRE', 'SECURITY', '出入口设施', '充电设施']],
  ['厨房设备', '从备料、烹饪到供餐与清洁', ['工作台与储物', '制冷与制冰', '热厨与蒸烤', '备餐与保温', '清洗与消毒', '回收与转运']]
]

export function sectionFolders(folders) {
  const byKey = new Map(folders.map(folder => [folder.key, folder]))
  const sections = []
  for (const [label, description, keys] of folderSections) {
    const items = keys.map(key => byKey.get(key)).filter(Boolean)
    if (items.length) sections.push({ label, description, items })
    for (const key of keys) byKey.delete(key)
  }
  const pending = byKey.get('待确认')
  byKey.delete('待确认')
  if (byKey.size) sections.push({ label: '其他设备类型', description: '按台账登记类型浏览', items: [...byKey.values()] })
  if (pending) sections.push({ label: '待核对设备', description: '现有资料尚不能确定类型', items: [pending] })
  return sections
}

const types = {
  faucet: ['水龙头', '取水与冲洗'],
  sink: ['水槽与盆台', '清洗食材、餐具及双手'],
  purifier: ['净化设备', '过滤或净化处理'],
  heater: ['热水设备', '加热和供应热水'],
  dishwasher: ['机械清洗设备', '机械清洗食材或餐具'],
  disinfect: ['消毒柜', '器具消毒'],
  hygieneLamp: ['消毒与灭虫灯具', '环境卫生与虫害控制'],
  worktable: ['工作台', '物料整理与操作'],
  'work-cabinet': ['工作柜', '操作与柜内收纳'],
  shelf: ['层架与货架', '物料分层存放'],
  plate: ['连接板件', '台面连接与补位'],
  oven: ['蒸烤设备', '蒸制与烘烤'],
  range: ['炉灶', '灶面烹饪'],
  fridge: ['冷藏冷冻柜', '低温储存'],
  'ice-maker': ['制冰机', '制取冰块'],
  'rice-pail': ['保温饭桶', '米饭保温'],
  'soup-pail': ['保温汤桶', '汤品保温'],
  'serving-counter': ['发餐柜', '餐食保温与分发'],
  'warming-lamp': ['保温灯', '供餐区辅助保温'],
  'glass-guard': ['玻璃挡罩', '供餐台面防护'],
  returnWindow: ['回收窗口', '餐盘回收交接'],
  cart: ['推车', '物品周转与运输'],
  bin: ['收集桶', '废弃物收集'],
  fan: ['风机与送排风设备', '空气输送与换气'],
  hood: ['集气与排烟罩', '收集油烟或蒸汽'],
  silencer: ['消声设备', '降低风道噪声'],
  control: ['控制箱柜', '电气控制与配电'],
  ac: ['空调设备', '室内空气调节'],
  pump: ['泵组', '液体输送与增压'],
  separator: ['分离设备', '油水分离处理'],
  hydrant: ['消火栓箱', '消防取水与初期灭火'],
  'fire-unit': ['灭火装置', '火灾防护'],
  elevator: ['电梯', '楼层间垂直运输'],
  bollard: ['升降路桩', '车辆出入控制'],
  'revolving-door': ['旋转门', '建筑出入口通行'],
  'ev-charger': ['充电桩', '电动车辆充电'],
  solar: ['光伏组件', '太阳能发电'],
  inverter: ['逆变器', '电能转换'],
  transformer: ['变压器', '配电电压转换'],
  light: ['照明设备', '区域照明'],
  display: ['显示屏', '现场信息展示'],
  camera: ['摄像设备', '现场视频采集'],
  'network-rack': ['网络机柜', '网络设备安装与收纳'],
  'network-switch': ['交换机', '网络数据交换'],
  record: ['其他在册设备', '按档案名称查阅']
}

function recordKind(row) {
  const name = row.equipmentName || ''
  if (/消毒灯|灭蝇灯/.test(name)) return 'hygieneLamp'
  if (/回收窗口/.test(name)) return 'returnWindow'
  return equipmentIllustrationKind(name)
}

export function sectionRecords(rows, hasCustomSort) {
  const groups = new Map()
  for (const row of rows) {
    const kind = recordKind(row)
    if (!groups.has(kind)) groups.set(kind, [])
    groups.get(kind).push(row)
  }
  return Object.entries(types).filter(([kind]) => groups.has(kind)).map(([kind, [label, description]]) => {
    const items = groups.get(kind)
    if (!hasCustomSort) items.sort((a, b) => collator.compare(a.equipmentName || '', b.equipmentName || '')
      || collator.compare(a.equipmentCode || '', b.equipmentCode || '')
      || collator.compare(String(a.id), String(b.id)))
    return { label, description, items }
  })
}

export function pageSections(sections, page, size) {
  const start = (page - 1) * size
  const end = start + size
  const result = []
  let offset = 0
  for (const section of sections) {
    const total = section.items.length
    if (offset < end && offset + total > start) {
      const items = section.items.slice(Math.max(0, start - offset), end - offset)
      result.push({ ...section, total, items })
    }
    offset += total
  }
  return result
}
