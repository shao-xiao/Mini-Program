import JSZip from 'jszip'
import { normalizeCustomFieldFiles } from '../../utils/customFieldValues'
import request from '../../utils/request'
import { listCustomFieldValues, listCustomFieldDefinitions, createCustomFieldDefinition,
  saveCustomFieldValues, uploadCustomFieldAttachment } from '../../api/customFields'

const field = 'custom_floor_asbuilt'
const maxBytes = 20 * 1024 * 1024

async function values(id) {
  const byId = await listCustomFieldValues('floors', [id])
  return byId?.[id] || {}
}

export async function parseArchive(file) {
  if (file.size > maxBytes) throw Error('图模包超过 20 MB')
  const archive = await JSZip.loadAsync(await file.arrayBuffer())
  const manifest = archive.file('model.json')
  if (!manifest || manifest._data?.uncompressedSize > 2 * 1024 * 1024) throw Error('图模包缺少有效清单')
  const model = JSON.parse(await manifest.async('string'))
  if (model.version !== 1 || model.floor !== -2 || model.buildingCode !== 'BLD-3C5CD7268F55'
      || !Array.isArray(model.sources) || !model.sources.length) throw Error('图模与本楼层不匹配')
  for (const [group, limit] of Object.entries({outline: 1, walls: 2000, columns: 2000, bays: 2000, lines: 6000})) {
    const paths = group === 'outline' ? [model.geometry?.outline] : model.geometry?.[group]
    if (!Array.isArray(paths) || !paths.length || paths.length > limit || !paths.every(path =>
      Array.isArray(path) && path.length >= (group === 'lines' ? 2 : 3) && path.length <= 500 &&
      path.every(p => Array.isArray(p) && p.length === 2 && p.every(n => Number.isFinite(n) && Math.abs(n) < 500))))
      throw Error('模型坐标未通过校验')
  }
  return { archive, model }
}

async function download(file) {
  const fileId = Number(file.fileId || file.id)
  if (!Number.isSafeInteger(fileId) || fileId < 1) throw Error('模型附件编号无效')
  return request.get('/files/' + fileId, { responseType: 'blob', silent: true, timeout: 60000 })
}

async function parseCore(file, model) {
  if (file.size > maxBytes) throw Error('核心筒图包超过 20 MB')
  const archive = await JSZip.loadAsync(await file.arrayBuffer())
  const entry = archive.file('core.json')
  if (!entry || entry._data.uncompressedSize > 1024 * 1024) throw Error('缺少有效核心筒清单')
  const core = JSON.parse(await entry.async('string'))
  const reference = model.sources.find(s => s.code === 'A101')?.sha256
  if (core.version !== 1 || core.floor !== model.floor || core.buildingCode !== model.buildingCode ||
      !reference || core.reference !== reference || core.source?.code !== 'A401' || !archive.file('A401.pdf'))
    throw Error('核心筒详图与当前底图版本不符')
  for (const key of ['walls','columns','lines']) {
    if (!Array.isArray(core[key]) || !core[key].length || core[key].length > 2000 ||
        !core[key].every(path => Array.isArray(path) && (key === 'lines' ? path.length >= 2 && path.length <= 500 : path.length === 4) &&
          path.every(p => Array.isArray(p) && p.length === 2 && p.every(n => Number.isFinite(n) && Math.abs(n)<500))))
      throw Error('核心筒轮廓未通过校验')
  }
  if (!Array.isArray(core.columnIndices) || core.columnIndices.length !== core.columns.length ||
      new Set(core.columnIndices).size !== core.columns.length ||
      !core.columnIndices.every(i => Number.isInteger(i) && model.geometry.columns[i]) ||
      !Number.isFinite(core.maxErrorMm) || core.maxErrorMm < 0 || core.maxErrorMm > 3)
    throw Error('详图柱位配准记录异常')
  return { core, archive }
}

export async function readFloorArchive(id) {
  const files = normalizeCustomFieldFiles((await values(id))[field])
  if (!files.length) return null
  const base = await parseArchive(await download(files[0]))
  if (files.length > 1) {
    try {
      const detail = await parseCore(await download(files[1]), base.model)
      base.core = detail.core
      base.coreArchive = detail.archive
    } catch (error) {
      base.coreError = error.message || '核心筒附件读取失败'
    }
  }
  return base
}

export async function attachCoreArchive(id, file, model) {
  const { core, archive } = await parseCore(file, model)
  const pdf = await openSource(archive, core.source)
  URL.revokeObjectURL(pdf)
  const before = normalizeCustomFieldFiles((await values(id))[field])
  if (before.length !== 1) throw Error('楼层附件已变化，请刷新查看')
  const uploaded = await uploadCustomFieldAttachment(file, { workspaceKey:'floors', timeout:60000 })
  const current = await values(id)
  const files = normalizeCustomFieldFiles(current[field])
  if (files.length !== 1 || files[0].fileId !== before[0].fileId) throw Error('底图已变化，请刷新查看')
  await saveCustomFieldValues('floors', id, { ...current, [field]: [...files, { ...uploaded, sortOrder:1 }] })
}

// The floor workspace saves the entire custom-field record. Initial setup is only
// offered on an unbound floor and preserves independent field values.
export async function attachFloorArchive(id, file) {
  await parseArchive(file)
  const defs = await listCustomFieldDefinitions('floors', { includeDisabled: true })
  const existing = defs.find(d => d.fieldCode === field)
  if (existing && (existing.status !== 'ENABLED' || existing.fieldType !== 'ATTACHMENT')) throw Error('楼层图模字段类型或启用状态异常')
  if (!existing) await createCustomFieldDefinition('floors', { fieldCode: field, fieldName: '竣工图模', fieldType: 'ATTACHMENT' })
  const before = await values(id)
  if (normalizeCustomFieldFiles(before[field]).length) throw Error('该楼层已有图模，请刷新查看')
  const uploaded = await uploadCustomFieldAttachment(file, { workspaceKey: 'floors', fieldType: 'ATTACHMENT', timeout: 60000 })
  const current = await values(id)
  if (normalizeCustomFieldFiles(current[field]).length) throw Error('其他用户已关联图模，请刷新查看')
  await saveCustomFieldValues('floors', id, { ...current, [field]: [{ ...uploaded, sortOrder: 0 }] })
}

export async function openSource(archive, source) {
  const entry = archive.file(source.file)
  if (!entry || entry._data?.uncompressedSize > maxBytes) throw Error('找不到图纸文件')
  const bytes = await entry.async('arraybuffer')
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  const hex = Array.from(new Uint8Array(digest), n => n.toString(16).padStart(2, '0')).join('')
  if (hex !== source.sha256) throw Error('原图文件校验不一致')
  return URL.createObjectURL(new Blob([bytes], { type: 'application/pdf' }))
}
