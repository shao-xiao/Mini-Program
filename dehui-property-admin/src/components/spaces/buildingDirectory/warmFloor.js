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
let running
export async function warmFloor(name, signal) {
  running?.abort()
  const record=archives[name],token=localStorage.getItem('token')
  if(!record||!token||signal.aborted)return
  const key=Symbol.for('dehui.floor.packs.v2')
  if(window[key]?.token!==token)window[key]={token,files:new Map()}
  const files=window[key].files,[id,sha]=record
  if(files.has(sha))return
  const job=new AbortController();running=job
  const abort=()=>job.abort()
  signal.addEventListener('abort',abort,{once:true})
  try {
    const r=await request.get('/files/'+id,{binaryResponse:true,responseType:'arraybuffer',silent:true,signal:job.signal,timeout:45000})
    const bytes=new Uint8Array(r.data)
    const sum=new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))
    if(Array.from(sum,x=>x.toString(16).padStart(2,'0')).join('')!==sha || token!==localStorage.getItem('token'))return
    if([...files.values()].reduce((n,b)=>n+b.byteLength,bytes.byteLength)>32*1024**2)files.clear()
    files.set(sha,bytes)
  } catch { /* Opening the floor retries through the regular viewer. */ }
  finally {signal.removeEventListener('abort',abort)}
}
