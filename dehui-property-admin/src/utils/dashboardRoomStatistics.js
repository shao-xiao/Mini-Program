// Use the room ledger's server-side totals, not an AI report or a single page of rooms.
export async function loadDashboardRoomStatistics(get) {
  const results = await Promise.allSettled([
    get('/rooms', { params: { page: 1, pageSize: 1 }, silent: true }),
    get('/rooms', { params: { page: 1, pageSize: 1, status: 'RENTED' }, silent: true })
  ])
  const failure = results.find(result => result.status === 'rejected')
  if (failure) throw failure.reason

  const count = data => {
    const total = Array.isArray(data) ? data.length : Number(data?.total ?? data?.totalElements)
    if (!Number.isSafeInteger(total) || total < 0) throw new Error('房源统计返回的数据不完整')
    return total
  }
  const roomCount = count(results[0].value)
  const rentedRoomCount = count(results[1].value)
  if (rentedRoomCount > roomCount) throw new Error('房源统计数据已变化，请刷新重试')
  return {
    roomCount,
    rentedRoomCount,
    availableRoomCount: roomCount - rentedRoomCount,
    rentalRate: roomCount > 0 ? Math.round(rentedRoomCount / roomCount * 1000) / 10 : 0
  }
}
