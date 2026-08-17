export function normalizeImportHeader(header) {
  return String(header ?? '')
    .replace(/^\ufeff/, '')
    .replace(/[\s\uFEFF/\\／()（）:：_\-－—–·•.,，。]+/g, '')
    .trim()
}

const legacyHeaderAliasGroups = [
  ['搴忓彿', '缂栧彿'],
  ['璁惧/鏉愭枡鍚嶇О', '璁惧鏉愭枡鍚嶇О', '璁惧鍚嶇О', '鏉愭枡鍚嶇О'],
  ['瑙勬牸鍨嬪彿', '瑙勬牸/鍨嬪彿', '鍨嬪彿', '瑙勬牸'],
  ['鏁伴噺', '鏁伴噺鍗曚綅'],
  ['瀹夎浣嶇疆', '瀹夎鍦扮偣', '鎵€鍦ㄤ綅缃?', '浣嶇疆'],
  ['鐢熶骇鍘傚', '鐢熶骇鍘傚晢', '鍒堕€犲晢', '鍘傚'],
  ['鍘傚鑱旂郴鏂瑰紡', '鍘傚鑱旂郴鏂瑰紡鐢佃瘽', '鑱旂郴鏂瑰紡', '鑱旂郴鐢佃瘽'],
  ['璐ㄤ繚鏈?', '璐ㄤ繚鏈熼檺', '淇濅慨鏈?', '淇濅慨鏈熼檺'],
  ['鎵€灞炵郴缁?', '绯荤粺鍚嶇О', '绯荤粺'],
  ['澶囨敞', '璇存槑'],
  ['鍥哄畾璧勪骇缂栫爜', '鍥哄畾璧勪骇缂栧彿', '璧勴骇缂栫爜', '璧勴骇缂栧彿']
]

const headerAliasGroups = [
  ...legacyHeaderAliasGroups,
  ['序号', '编号'],
  ['设备/材料名称', '设备材料名称', '设备名称', '材料名称'],
  ['规格型号', '规格/型号', '型号', '规格'],
  ['数量', '数量单位'],
  ['安装位置', '安装地点', '所在位置', '位置'],
  ['生产厂家', '生产厂商', '制造商', '厂家'],
  ['厂家联系方式', '厂家联系方式电话', '联系方式', '联系电话'],
  ['质保期', '质保期限', '保修期', '保修期限'],
  ['所属系统', '系统名称', '系统'],
  ['备注', '说明'],
  ['固定资产编码', '固定资产编号', '资产编码', '资产编号']
]

export function buildEquipmentImportRows(rows) {
  const readableRows = (Array.isArray(rows) ? rows : [])
    .filter(row => Array.isArray(row))
    .filter(row => row.some(cell => String(cell ?? '').trim()))

  const headerIndex = findEquipmentImportHeaderRow(readableRows)
  if (headerIndex < 0) return []

  const headers = readableRows[headerIndex].map(header => String(header || '').trim().replace(/^\ufeff/, ''))
  return readableRows
    .slice(headerIndex + 1)
    .map(cells => mapEquipmentImportRow(headers, cells))
    .filter(row => row.equipmentName || row.assetCode || row.equipmentCode)
}

export function findEquipmentImportHeaderRow(rows) {
  let bestIndex = -1
  let bestScore = 0
  ;(Array.isArray(rows) ? rows : []).forEach((row, index) => {
    const score = equipmentHeaderScore(row)
    if (score > bestScore) {
      bestScore = score
      bestIndex = index
    }
  })
  return bestScore >= 2 ? bestIndex : -1
}

export function mapEquipmentImportRow(headers, cells) {
  const row = {}
  const customColumns = {}
  headers.forEach((header, index) => {
    const normalizedHeader = normalizeImportHeader(header)
    const value = String(cells[index] ?? '').trim()
    row[normalizedHeader] = value
    if (!isKnownEquipmentHeader(normalizedHeader)) {
      const visibleHeader = String(header || '').trim().replace(/^\ufeff/, '')
      if (visibleHeader) customColumns[visibleHeader] = value
      if (normalizedHeader) customColumns[normalizedHeader] = value
    }
  })

  return {
    ...customColumns,
    sequence: rowValue(row, '序号', '编号'),
    equipmentName: rowValue(row, '设备/材料名称', '设备材料名称', '设备名称', '材料名称', '名称'),
    model: rowValue(row, '规格型号', '规格/型号', '型号', '规格'),
    quantity: rowValue(row, '数量', '数量单位'),
    location: rowValue(row, '安装位置', '安装地点', '所在位置', '位置'),
    manufacturer: rowValue(row, '生产厂家', '生产厂商', '制造商', '厂家'),
    manufacturerContact: rowValue(row, '厂家联系方式', '厂家联系方式电话', '联系方式', '联系电话', '联系人电话'),
    warrantyPeriod: rowValue(row, '质保期', '质保期限', '保修期', '保修期限'),
    systemName: rowValue(row, '所属系统', '系统名称', '系统'),
    remark: rowValue(row, '备注', '说明'),
    assetCode: rowValue(row, '固定资产编码', '固定资产编号', '资产编码', '资产编号'),
    equipmentCode: rowValue(row, '固定资产编码', '固定资产编号', '资产编码', '资产编号', '设备编号')
  }
}

function isKnownEquipmentHeader(header) {
  return headerAliasGroups.some(aliases => aliases.some(alias => isHeaderMatch(header, alias)))
}

function rowValue(row, ...keys) {
  for (const key of keys) {
    const value = row[normalizeImportHeader(key)]
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return String(value).trim()
    }
  }
  return ''
}

function equipmentHeaderScore(row) {
  const cells = (Array.isArray(row) ? row : []).map(normalizeImportHeader)
  return headerAliasGroups.reduce((score, aliases) => {
    return score + (aliases.some(alias => cells.some(cell => isHeaderMatch(cell, alias))) ? 1 : 0)
  }, 0)
}

function isHeaderMatch(cell, alias) {
  const normalizedAlias = normalizeImportHeader(alias)
  return cell === normalizedAlias || (normalizedAlias.length >= 4 && cell.includes(normalizedAlias))
}
