import {authorize,openingParts,readPart} from './ranges'
export const modelAssets={}
let activeKey
export async function glb(name){
  const bytes=await readPart(name.startsWith('shared/')?name:activeKey+'/'+name)
  // GLTFLoader checks the viewer realm's ArrayBuffer constructor.
  return new Uint8Array(bytes)
}
export async function loadAssets(key) {
  const names=openingParts(key)
  const [,bytes]=await Promise.all([authorize(names),readPart(key+'/meta'),...names.slice(1).map(readPart)])
  activeKey=key
  const data=JSON.parse(new TextDecoder().decode(bytes))
  const meta=data.floorMeta||data.serviceData||data.electricData
  for(const source of Object.values(meta.sources)){
    source.file='?tab='+encodeURIComponent(new URLSearchParams(location.search).get('tab'))+'&drawing='+encodeURIComponent(source.file)
  }
  Object.assign(modelAssets,data)
  return meta
}
export async function imageFor(source,field){
  if(!source?.[field]||source[field].startsWith('blob:'))return
  const name=activeKey+'/'+source[field]
  const [,bytes]=await Promise.all([authorize([name]),readPart(name)])
  source[field]=URL.createObjectURL(new Blob([bytes],{type:'image/png'}))
}
export async function openDrawing(key,name){
  const part=key+'/'+name
  if(!name.endsWith('.pdf'))throw Error('原图编号无效')
  const [,bytes]=await Promise.all([authorize([part]),readPart(part)])
  location.replace(URL.createObjectURL(new Blob([bytes],{type:'application/pdf'})))
}
