import request from '../../../utils/request'
import {inflateSync} from 'three/addons/libs/fflate.module.js'
import entries from './ranges.json'

const serviceKey = Symbol.for('dhic.b2.range-service.v1')
const options = {binaryResponse:true,responseType:'arraybuffer',silent:true,timeout:90000}
const token = () => localStorage.getItem('token')
function createService() {
  let owner
  const cache = new Map()
  function current(){
    const value = token()
    if(value!==owner){cache.clear();owner=value}
    if(!value)throw Error('请先登录')
    return value
  }
  async function read(name){
    const session=current()
    const [id,start,length,digest,compression]=entries[name]
    if(cache.has(name))return cache.get(name)
    const pending=(async()=>{
      const end=start+length-1
      const response=await request.get('/files/'+id,{...options,headers:{Range:'bytes='+start+'-'+end}})
      const bytes=new Uint8Array(response.data)
      if(response.status===206 && !response.headers['content-range']?.startsWith('bytes '+start+'-'+end+'/'))throw Error('附件分段不匹配')
      const part=response.status===200?bytes.subarray(start,end+1):bytes
      if(part.length!==length)throw Error('附件读取不完整')
      const data=compression===8?inflateSync(part):part
      const hash=new Uint8Array(await crypto.subtle.digest('SHA-256',data))
      if(Array.from(hash,n=>n.toString(16).padStart(2,'0')).join('')!==digest || current()!==session)throw Error('附件校验失败')
      return data
    })()
    if(cache.size>=32)cache.delete(cache.keys().next().value)
    cache.set(name,pending)
    try{return await pending}catch(err){if(cache.get(name)===pending)cache.delete(name);throw err}
  }
  return {read}
}
export function readPart(name) {
  if(!Object.hasOwn(entries,name))throw Error('图模条目缺失')
  const host=window.parent.location.origin===location.origin?window.parent:window
  return (host[serviceKey] ||= createService()).read(name)
}
// Revalidate each immutable attachment used on viewer entry, including memory hits.
export async function authorize(names) {
  const session=token()
  if(!session)throw Error('请先登录')
  const ids=new Set(names.map(name=>entries[name][0]))
  await Promise.all([...ids].map(id=>request.get('/files/'+id,{...options,headers:{Range:'bytes=0-0'}})))
  if(session!==token())throw Error('登录状态已变更')
}
export const activeNames={
  hvac:['B2-普通通风.glb'], electric:['B2-普通通风.glb','B2·动力配电'],
  fire:['消防报警与联动'], weak:['综合弱电']
}
export function openingParts(key){
  return [key+'/meta',...activeNames[key].map(name=>key+'/'+name),...(['fire','weak'].includes(key)?['shared/建筑']:[])]
}
