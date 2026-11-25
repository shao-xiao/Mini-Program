const reviewedIndex = '1fa67ae16b7723724717d069032da2a5bbdf373b5701d6c39cf85d0c4c79f30b'

export const disciplineModels = {
  平面图: ['承重结构'],
  给排水: ['给排水'],
  暖通: ['普通通风'],
  强电: ['动力配电', '室内照明与应急'],
  消防: ['防烟与排烟', '消防报警与联动', '自动喷淋', '消火栓', '消防物联网', '室内照明与应急'],
  弱电: ['综合弱电', '综合安防']
}

export async function checkedEntry(archive, file, digest, limit) {
  const entry = archive.file(file)
  if (!entry || entry._data.uncompressedSize > limit) throw Error('模型图包条目缺失或超过大小限制')
  const bytes = await entry.async('arraybuffer')
  const hash = await crypto.subtle.digest('SHA-256', bytes)
  const hex = Array.from(new Uint8Array(hash), x => x.toString(16).padStart(2, '0')).join('')
  if (hex !== digest) throw Error('模型图包文件校验不一致')
  return bytes
}

export async function parseDisciplines(archive, model) {
  const bytes = await checkedEntry(archive, 'disciplines.json', reviewedIndex, 65536)
  const data = JSON.parse(new TextDecoder().decode(bytes))
  if (data.floor !== model.floor || data.reference !== model.sources.find(s => s.code === 'A101')?.sha256)
    throw Error('专业模型与当前建筑底图不匹配')
  return { kind: 'disciplines', data, archive }
}
