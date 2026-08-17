const EQUIPMENT_UPDATE_FIELDS = new Set([
  'equipmentName',
  'equipmentCode',
  'equipmentType',
  'location',
  'manufacturer',
  'model',
  'installDate',
  'remark'
])

export function buildEquipmentUpdatePayload(row = {}, field, value) {
  if (!EQUIPMENT_UPDATE_FIELDS.has(field)) {
    throw new Error(`Unsupported equipment update field: ${field}`)
  }

  return {
    equipmentName: row.equipmentName,
    equipmentCode: row.equipmentCode || '',
    equipmentType: row.equipmentType || 'OTHER',
    status: row.status || 'NORMAL',
    location: row.location || '',
    manufacturer: row.manufacturer || '',
    model: row.model || '',
    installDate: row.installDate || '',
    remark: row.remark || '',
    [field]: value
  }
}

export function isEquipmentUpdateField(field) {
  return EQUIPMENT_UPDATE_FIELDS.has(field)
}
