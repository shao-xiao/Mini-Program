import JSZip from 'jszip'
import request from '../../../utils/request'
import { normalizeCustomFieldFiles } from '../../../utils/customFieldValues'

export const modelAssets = {}
export async function loadAssets(key) {
  const fields = await request.get('/workspaces/floors/records/14/custom-fields', {silent:true})
  const file = normalizeCustomFieldFiles(fields.custom_b2_model_systems_v2)
    .find(item => item.name === 'B2-verified-'+key+'-v2.zip')
  if (!file) throw Error('当前楼层未关联此专业的验收包')
  const blob = await request.get('/files/'+file.fileId, {responseType:'blob',silent:true,timeout:90000})
  const archive = await JSZip.loadAsync(await blob.arrayBuffer(), {checkCRC32:true})
  const data = JSON.parse(await archive.file('model.json').async('string'))
  const meta = data.floorMeta || data.serviceData || data.electricData
  await Promise.all(Object.values(meta.sources).flatMap(source =>
    ['file','image','pickImage'].filter(key => source[key]).map(async field => {
      const bytes = await archive.file(source[field]).async('uint8array')
      source[field] = URL.createObjectURL(new Blob([bytes],{type:field==='file'?'application/pdf':'image/png'}))
    })
  ))
  const models = new Map()
  await Promise.all(archive.file(/\.glb$/).map(async entry => models.set(entry.name,await entry.async('uint8array'))))
  Object.assign(modelAssets,data,{floorBytes:name => models.get(name)})
  return meta
}
