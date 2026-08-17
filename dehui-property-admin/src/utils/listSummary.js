import request from './request'
import { readPage } from './pagination'
import { buildBusinessSummaryCards } from './businessSummary'

export function countOf(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : 0
}

export function totalFromListResponse(data) {
  return readPage(data).total
}

export function buildCountSummaryCards({
  total,
  filtered,
  unit = '条数据',
  totalDesc,
  filteredDesc = '当前筛选条件命中',
  extraCards = []
}) {
  return buildBusinessSummaryCards({
    totalLabel: `${unit}总数`,
    total: countOf(total),
    totalDesc: totalDesc || `全部${unit}`,
    filtered: countOf(filtered),
    filteredDesc,
    cards: extraCards
  })
}

export async function loadListTotal(url, params = {}) {
  const result = await loadListTotalResult(url, params)
  return result.ok ? result.value : 0
}

export async function loadListTotalResult(url, params = {}) {
  try {
    const data = await request.get(url, {
      params: {
        ...params,
        page: 1,
        pageSize: 1,
        size: 1
      },
      silent: true
    })

    return { ok: true, value: totalFromListResponse(data), error: null }
  } catch (error) {
    return { ok: false, value: null, error }
  }
}

export async function loadListTotals(items = []) {
  const results = await loadListTotalsDetailed(items)

  return items.reduce((stats, item) => {
    stats[item.key] = results[item.key]?.ok ? results[item.key].value : 0
    return stats
  }, {})
}

export async function loadListTotalsDetailed(items = []) {
  const results = await Promise.all(
    items.map(item => loadListTotalResult(item.url, item.params || {}))
  )

  return items.reduce((stats, item, index) => {
    stats[item.key] = results[index]
    return stats
  }, {})
}
