import request from '../../../utils/request'
const archives = {
  "B1": [
    268,
    "3a1d72b590726562bb8da44c470418e28df0cacf89fdf7bba262641a42c76bd7"
  ],
  "4F": [
    207,
    "a68a3f1b4604cef0d79768e5f6901f97a8e97891cf4c4f4aa05133c17644f121"
  ],
  "1F": [
    214,
    "4b6751e9fb31cae21cee7f1f22e18719023503d22ca3313d9ba52eef4e8ea957"
  ],
  "2F": [
    219,
    "3d1361e975e59a12c0a406b2996b6712b00aa00739270eaaeb299e39a1783f2f"
  ],
  "3F": [
    227,
    "619e3108bac7e0df4aca603bc40bb6a63d718e77948df7516878e0371b37583d"
  ],
  "5F": [
    232,
    "b0fe0f9241c306ac00b17342cebb6e4f3dcd01e49332d65476e484afc8252e27"
  ],
  "6F": [
    237,
    "e71d3f16387939db187bdc3a75f4d1d30c75ffad0ec6e2818c6e4eeba7862f7e"
  ],
  "7F": [
    242,
    "17499dfb5e5244a08262985e09a8a8938aa45e038dc77ad120daadb20fd39e4d"
  ],
  "8F": [
    245,
    "7c0983df60b1ec0c7a07b624e8f356149fddb9d7ae59e6645da48f4d73b1c6cf"
  ],
  "9F": [
    252,
    "9d17f1f730bb6a1525e230bd2020d322ecd456a67f90c9ad31a50cec01074192"
  ],
  "\u5c4b\u9762": [
    261,
    "e54af2ce314da9c47a582c9b1aa61813027946cd6ee1403715d7fc74b8e3eb15"
  ]
}
const pending = new Map()
const memoryKey = Symbol.for('dehui.floor.packs.v2')
const diskName = 'dhic-floor-session-v1'

async function cryptKey(token) {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(token))
  return crypto.subtle.importKey('raw', bytes, 'AES-GCM', false, ['encrypt','decrypt'])
}
async function openCache() {
  try { return await caches.open(diskName) } catch { return null }
}
async function verified(bytes, sha) {
  const sum = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
  return Array.from(sum, x=>x.toString(16).padStart(2,'0')).join('') === sha
}
async function acquire(ref, token, progress) {
  if(window[memoryKey]?.token!==token)window[memoryKey]={token,files:new Map()}
  const files=window[memoryKey].files
  const url='/files/'+ref.id
  const options={binaryResponse:true,responseType:'arraybuffer',silent:true,timeout:120000}
  const disk=await openCache()
  const key=location.origin+'/__floor_cache__/'+ref.sha
  let bytes=files.get(ref.sha)
  let encoded
  if(!bytes && disk)try{encoded=await disk.match(key)}catch{}
  if(bytes || encoded){
    const proof=await request.get(url,{...options,headers:{Range:'bytes=0-0'}})
    if(proof.status===206 && proof.data.byteLength===1){
      if(!bytes)try{
        const ciphertext=new Uint8Array(await encoded.arrayBuffer())
        bytes=new Uint8Array(await crypto.subtle.decrypt({name:'AES-GCM',iv:ciphertext.slice(0,12)},await cryptKey(token),ciphertext.slice(12)))
        if(!await verified(bytes,ref.sha))bytes=null
      }catch{bytes=null}
    }else bytes=null
  }
  if(!bytes){
    const response=await request.get(url,{...options,onDownloadProgress:progress})
    bytes=new Uint8Array(response.data)
    if(!await verified(bytes,ref.sha))throw Error('楼层附件校验失败')
    if(disk)void (async()=>{
      const iv=crypto.getRandomValues(new Uint8Array(12))
      const data=await crypto.subtle.encrypt({name:'AES-GCM',iv},await cryptKey(token),bytes)
      await disk.put(key,new Response(new Blob([iv,data])))
      const keys=await disk.keys()
      for(const stale of keys.slice(0,Math.max(0,keys.length-12)))await disk.delete(stale)
    })().catch(()=>{})
  }
  if(token!==localStorage.getItem('token'))throw Error('登录状态已变化')
  if(!files.has(ref.sha)&&[...files.values()].reduce((n,b)=>n+b.length,bytes.length)>32*1024**2)files.clear()
  files.set(ref.sha,bytes)
  return bytes
}
export function readArchive(ref, progress) {
  const token=localStorage.getItem('token')
  if(!token)return Promise.reject(Error('请登录后查看'))
  const key=token+'|'+ref.sha
  if(!pending.has(key))pending.set(key,acquire(ref,token,progress).finally(()=>pending.delete(key)))
  return pending.get(key)
}
export async function warmFloor(name, signal) {
  const record=archives[name]
  if(!record || signal.aborted || navigator.connection?.saveData)return
  try { await readArchive({id:record[0],sha:record[1]}) } catch { /* The foreground viewer retries. */ }
}
