import { readPage } from './pagination.js'

export async function loadAllWorkspaceRecords(fetchPage, options = {}) {
  if (typeof fetchPage !== 'function') throw new TypeError('fetchPage must be a function')
  const pageSize = positiveInteger(options.pageSize, 100)
  const maxRecords = positiveInteger(options.maxRecords, 10000)
  const records = []
  let pageNumber = 1
  let total = 0

  while (pageNumber === 1 || records.length < total) {
    const page = readPage(await fetchPage({ page: pageNumber, pageSize }))
    if (pageNumber === 1) {
      total = Number(page.total || 0)
      if (total > maxRecords) {
        throw new Error(`当前查询结果 ${total} 条，超过单次导出上限 ${maxRecords} 条`)
      }
    }
    records.push(...page.records)
    if (!page.records.length || page.records.length < pageSize) break
    pageNumber += 1
  }

  return records.slice(0, total || records.length)
}

function positiveInteger(value, fallback) {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : fallback
}
