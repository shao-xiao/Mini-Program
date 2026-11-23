import { equipmentTypeOptions } from '../workspaceConfigs/equipment.js'

const pageSize = 100
const pagesPerBatch = 3
const standardTypes = new Map()
const typeRanks = new Map()
equipmentTypeOptions.forEach((option, rank) => {
  standardTypes.set(option.value, option)
  standardTypes.set(option.label, option)
  typeRanks.set(option.value, rank)
})
const byName = new Intl.Collator('zh-CN', { numeric: true })

// Display categories refine imported equipment without modifying its business type.
const filingRules = [
  ['通风与净化', /风机|风幕|排风|排烟|新风|烟罩|集气罩|净化器/],
  ['热厨与蒸烤', /煮汤炉|煮面炉|四头炉|矮仔炉|炒|灶|蒸柜|蒸烤箱|蒸饭箱/],
  ['制冷与制冰', /冰箱|冷藏柜|冷冻柜|制冰机/],
  ['备餐与保温', /饭桶|汤桶|发餐柜|售卖台玻璃罩|保温灯/],
  ['清洗与消毒', /星盆|龙头|洗手池|洗米机|洗碗机|消毒|灭蝇灯|开水器|热水器|残菜台|污碟台|洁碟台/],
  ['工作台与储物', /工作台|工作柜|补角板|挂墙|层架|货架|仓架|碗碟柜|小件柜|理鱼台/],
  ['回收与转运', /平板车|推车|回收车|回收窗口|垃圾桶/],
  ['出入口设施', /升降[墩柱]|旋转门|道闸/],
  ['充电设施', /充电桩/], ['照明', /泛光照明/],
  ['FIRE', /灭火系统|消防泵|消火栓/], ['WATER', /油水分离器/], ['HVAC', /空调/]
]
function category(key, source) {
  return { key, label: standardTypes.get(key)?.label ?? key, source }
}

export function getEquipmentCategory(row) {
  const raw = String(row.equipmentType ?? '').trim()
  const original = standardTypes.get(raw.toUpperCase())
  const name = String(row.equipmentName ?? '').normalize('NFKC').replace(/\s/g, '')
  // A table sold for an oven is furniture. Two historically labelled lifts are ventilation devices.
  if ((original?.value === 'OTHER' || !raw) && /工作台/.test(name)) return category('工作台与储物', '设备名称')
  if (original?.value === 'ELEVATOR' && /^(低噪音新风风机|净化器)$/.test(name)) return category('通风与净化', '设备名称')
  if (raw && original?.value !== 'OTHER' && !/^(未分类|其它)$/.test(raw)) {
    return { key: original?.value ?? raw.toLowerCase(), label: original?.label ?? raw, source: '台账设备类型' }
  }
  if (name === '特来电') return category('充电设施', '物业确认该设备为充电桩')
  const match = filingRules.find(([, pattern]) => pattern.test(name))
  if (match) return category(match[0], '设备名称')
  return category('待确认', '现有信息不足以判定设备类型')
}

export function groupEquipmentRecords(rows) {
  const folders = new Map()
  for (const row of rows) {
    const { key, label } = getEquipmentCategory(row)
    if (!folders.has(key)) folders.set(key, { key, label, count: 0, rows: [] })
    const folder = folders.get(key)
    folder.rows.push(row)
    folder.count++
  }
  const rank = folder => folder.key === '待确认' ? typeRanks.size + 1 : (typeRanks.get(folder.key) ?? typeRanks.size)
  return [...folders.values()].sort((a, b) => rank(a) - rank(b) || byName.compare(a.label, b.label))
}

class InconsistentPageError extends Error {
  constructor() {
    super('设备列表在读取期间发生变化，请重新加载。')
  }
}

async function readEquipmentPages(fetchPage, signal) {
  const rows = []
  const seen = new Set()
  let total

  function append(result, page) {
    if (!Array.isArray(result?.records) || !Number.isSafeInteger(result.total) || result.total < 0) {
      throw new Error('设备列表未返回有效的分页数据，请重新加载。')
    }
    total ??= result.total
    const expected = Math.min(pageSize, total - (page - 1) * pageSize)
    if (result.total !== total || result.records.length !== expected || (result.page != null && result.page !== page)) {
      throw new InconsistentPageError()
    }
    for (const row of result.records) {
      const id = String(row?.id ?? '')
      if (!id || seen.has(id)) throw new InconsistentPageError()
      seen.add(id)
      rows.push(row)
    }
  }

  signal?.throwIfAborted()
  const first = await fetchPage({ page: 1, pageSize }, signal)
  signal?.throwIfAborted()
  append(first, 1)
  const lastPage = Math.ceil(total / pageSize)
  for (let page = 2; page <= lastPage; page += pagesPerBatch) {
    const count = Math.min(pagesPerBatch, lastPage - page + 1)
    const pages = await Promise.allSettled(Array.from({ length: count }, (_, i) =>
      fetchPage({ page: page + i, pageSize }, signal)
    ))
    signal?.throwIfAborted()
    for (const [i, result] of pages.entries()) {
      if (result.status === 'rejected') throw result.reason
      append(result.value, page + i)
    }
  }
  return rows
}

export async function loadEquipmentFolderRecords(fetchPage, signal) {
  try {
    return await readEquipmentPages(fetchPage, signal)
  } catch (error) {
    if (!(error instanceof InconsistentPageError)) throw error
    return readEquipmentPages(fetchPage, signal)
  }
}
