export const parkingAreas = ['A', 'B', 'C', 'D']
export const parkingGroups = [
  { key: 'free', label: '空余车位' },
  { key: 'rented', label: '已出租车位' },
  { key: 'maintenance', label: '维护中' },
  { key: 'disabled', label: '已停用' },
  { key: 'other', label: '待核对状态' }
]

export function parkingGroup(space) {
  if (space.status === 'OCCUPIED' && space.assignmentId != null) return 'rented'
  if (space.assignmentId != null || space.status === 'OCCUPIED') return 'other'
  return { AVAILABLE: 'free', MAINTENANCE: 'maintenance', DISABLED: 'disabled' }[space.status] || 'other'
}

export function parkingArea(space) {
  const raw = String(space.area ?? '').trim()
  const match = /^([A-D])(?:\s*区)?$/i.exec(raw.normalize('NFKC'))
  return match ? match[1].toUpperCase() : raw || '未登记区域'
}

export function areaLabel(area) {
  return parkingAreas.includes(area) ? area + ' 区' : area
}

const compare = new Intl.Collator('zh-CN', { numeric: true })
export function buildParkingDirectory(rows, customSort = false) {
  const zones = new Map()
  function create(key) {
    const zone = { key, label: areaLabel(key), total: 0, groups: Object.fromEntries(parkingGroups.map(group => [group.key, []])) }
    zones.set(key, zone)
    return zone
  }
  parkingAreas.forEach(create)
  for (const row of rows) {
    const key = parkingArea(row)
    const zone = zones.get(key) || create(key)
    zone.groups[parkingGroup(row)].push(row)
    zone.total++
  }
  for (const zone of zones.values()) {
    if (!customSort) Object.values(zone.groups).forEach(items => items.sort((a, b) =>
      compare.compare(a.spaceNo ?? '', b.spaceNo ?? '') || compare.compare(String(a.id), String(b.id))))
  }
  return [...zones.values()]
}
