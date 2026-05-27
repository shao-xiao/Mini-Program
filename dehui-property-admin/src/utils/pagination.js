export function createPagination(pageSize = 20) {
  return {
    currentPage: 1,
    pageSize,
    total: 0
  }
}

export function pageParams(pagination) {
  return {
    page: pagination.currentPage,
    pageSize: pagination.pageSize
  }
}

export function readPage(data) {
  if (Array.isArray(data)) {
    return {
      records: data,
      total: data.length
    }
  }

  return {
    records: data?.records || data?.content || [],
    total: data?.total ?? data?.totalElements ?? 0
  }
}

export function resetToFirstPage(pagination) {
  pagination.currentPage = 1
}
