import request from '../../../utils/request'
import manifest from './floorPreviews.json'

const previews=new Map()
let session, batch
function current(){
  const token=localStorage.getItem('token')
  if(token!==session){
    for(const promise of previews.values())promise.then(URL.revokeObjectURL).catch(()=>{})
    previews.clear();batch=null;session=token
  }
  return token
}
const options={responseType:'arraybuffer',binaryResponse:true,silent:true,timeout:15000}
async function imageFor(key,token,data){
  const [offset,length,sha]=manifest.ranges[key]
  let bytes
  if(data)bytes=new Uint8Array(data,offset,length)
  else{
    const r=await request.get('/files/'+manifest.fileId,{...options,headers:{Range:'bytes='+offset+'-'+(offset+length-1)}})
    bytes=r.status===206?new Uint8Array(r.data):new Uint8Array(r.data,offset,length)
  }
  const hash=new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))
  if(Array.from(hash,b=>b.toString(16).padStart(2,'0')).join('')!==sha || token!==current())throw Error('预览校验失败')
  return URL.createObjectURL(new Blob([bytes],{type:'image/webp'}))
}
function get(key,data){
  const token=current()
  if(!token||!Object.hasOwn(manifest.ranges,key))return Promise.reject(Error('楼层预览不可用'))
  if(!previews.has(key)){
    const promise=imageFor(key,token,data)
    previews.set(key,promise)
    promise.catch(()=>{if(previews.get(key)===promise)previews.delete(key)})
  }
  return previews.get(key)
}
export function primePreviews(){
  const token=current()
  if(!token||batch||navigator.connection?.saveData)return
  batch=request.get('/files/'+manifest.fileId,options).then(r=>{
    if(token===current())return Promise.allSettled(Object.keys(manifest.ranges).map(key=>get(key,r.data)))
  }).catch(()=>{if(token===session)batch=null})
}
export function installFloorPreviews(){
  window[Symbol.for('dhic.floorPreview')]=get
  // Start reading a deep-linked floor while the directory is resolving its floor record.
  const route=new URL(location.href)
  if(route.pathname!=='/assets/spaces'||route.searchParams.get('buildingId')!=='1')return
  const floors=['b1','f1','f2','f3','f4','f5','f6','f7','f8','f9','roof']
  const key=floors[Number(route.searchParams.get('floorId'))-3]
  if(key)get(key==='roof'&&route.hash==='#model-machine'?'machine':key).catch(()=>{})
}
