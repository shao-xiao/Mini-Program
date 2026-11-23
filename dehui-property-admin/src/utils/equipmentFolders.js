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

export function groupEquipmentRecords(rows) {
  const folders = new Map()
  for (const row of rows) {
    const name = String(row.equipmentType ?? '').trim()
    const standard = standardTypes.get(name.toUpperCase())
    const key = standard?.value ?? name.toLowerCase()
    if (!folders.has(key)) {
      folders.set(key, { key, label: standard?.label ?? (name || '未分类'), count: 0, rows: [] })
    }
    const folder = folders.get(key)
    folder.rows.push(row)
    folder.count++
  }
  const rank = folder => folder.key === '' ? typeRanks.size + 1 : (typeRanks.get(folder.key) ?? typeRanks.size)
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
