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

export async function readFloorArchive(id) {
  const files = normalizeCustomFieldFiles((await values(id))[field])
  if (!files.length) return null
  const fileId = Number(files[0].fileId || files[0].id)
  if (!Number.isSafeInteger(fileId) || fileId < 1) throw Error('模型附件编号无效')
  const blob = await request.get('/files/' + fileId, { responseType: 'blob', silent: true, timeout: 60000 })
  return parseArchive(blob)
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
