export const objectPackVersion = 3
export const objectIndexDigests = [
  '08f0b8eb3ee431aaac87823401f8a0eacb96831755404d6a4723f3f7c70cf0df',
  'ee87d8c54ac3c9eaabecf8c2d9025e1bf820805a4c1f479cbfc46b2cfdf9594e',
  'd69d79e66afc28092902f9af07bf104d15496f109cf20ad08166d4d14b5cb247',
  '49b0eb136fc84eac72606aa08c12190c8cf5c960894ee24dd6d9f3dcd01f270f'
]
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
  if (!(Array.isArray(digest) ? digest.includes(hex) : hex === digest)) throw Error('模型图包文件校验不一致')
  return bytes
}

export async function parseDisciplines(archive, model) {
  const bytes = await checkedEntry(archive, 'disciplines.json', reviewedIndex, 65536)
  const data = JSON.parse(new TextDecoder().decode(bytes))
  if (data.floor !== model.floor || data.reference !== model.sources.find(s => s.code === 'A101')?.sha256)
    throw Error('专业模型与当前建筑底图不匹配')
  return { kind: 'disciplines', data, archive }
}

export const purposes = {
  承重结构: '查看 B2 柱位与楼板，并与建筑空间叠合核对。',
  动力配电: '查看配电箱、动力线路和桥架的图示路径。桥架承托电缆，截面和安装要求以引线标注为准。',
  室内照明与应急: '按原图区分照明设备、线路和应急照明回路。',
  普通通风: '用风机房作参照，查看送排风管和风口在平面上的位置。',
  给排水: '对照管线、立管和集水坑的位置。设备排水关系可在「设备间与流向」核对。',
  消火栓: '对照消火栓、环管及立管，按平面图和系统图核查走向。',
  自动喷淋: '核对喷头分布和配水管走向。',
  防烟与排烟: '对照机房、风管和分区图核查防排烟设施位置。',
  消防报警与联动: '对照报警设备和各类线路，按图纸核对回路归属。',
  消防物联网: '对照图示设备和监测线路，查阅专业原图。',
  综合安防: '区分设备图符和连接线路，查看桥架位置。',
  综合弱电: '区分布线末端、线路和桥架，核对弱电间位置。'
}
