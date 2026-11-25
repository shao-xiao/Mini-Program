// Use the existing authenticated attachment endpoint for both the index and the drawings.
import {Reader, ZipReader, Uint8ArrayWriter} from '@zip.js/zip.js/index-native.js'
import JSZip from 'jszip'
import request from '../../utils/request'

const maxBytes = 20 * 1024 * 1024
const windowSize = 4096
// ZIP readers search the final EOCD record and up to 65535 bytes of comment.
const tailSize = 65557
const cache = new Map()
let session
const options = {responseType:'arraybuffer', binaryResponse:true, silent:true, timeout:60000}

function validSize(size) {
  if (!Number.isSafeInteger(size) || size < 1 || size > maxBytes) throw Error('图模附件长度异常')
}

async function range(url, value) {
  const response = await request.get(url, {...options, headers:{Range:'bytes='+value}})
  const bytes = new Uint8Array(response.data)
  if (response.status === 200) {
    validSize(bytes.length)
    return {bytes, start:0, size:bytes.length, complete:true}
  }
  const match = /^bytes (\d+)-(\d+)\/(\d+)$/.exec(response.headers['content-range'])
  if (response.status !== 206 || !match) throw Error('服务器返回了无效的图模分段')
  const [,start,end,size] = match.map(Number)
  validSize(size)
  if (end >= size || end-start+1 !== bytes.length) throw Error('图模附件分段不完整')
  return {bytes,start,size}
}

class AttachmentReader extends Reader {
  constructor(url,tail) {
    super()
    this.url = url
    this.size = tail.size
    this.tail = tail
    this.recent = tail
  }
  async readUint8Array(offset,length) {
    const end = offset+length
    if (length === 0) return new Uint8Array()
    if (offset < 0 || end > this.size) throw Error('图模索引超出附件范围')
    let part = [this.tail,this.recent].find(p => offset >= p.start && end <= p.start+p.bytes.length)
    if (!part) {
      const last = Math.min(this.size,offset+Math.max(length,windowSize))-1
      part = await range(this.url,offset+'-'+last)
      if (part.size !== this.size || offset < part.start || end > part.start+part.bytes.length)
        throw Error('图模附件已变化，请重新读取')
      this.recent = part
    }
    return part.bytes.subarray(offset-part.start,end-part.start)
  }
}

function supportsRawDeflate() {
  try { new DecompressionStream('deflate-raw'); return true }
  catch { return false }
}

async function openArchive(url) {
  const tail = await range(url,'-'+tailSize)
  if (tail.complete || !supportsRawDeflate()) {
    const data = tail.complete ? tail.bytes : (await request.get(url,options)).data
    return {size:tail.size, archive:await JSZip.loadAsync(data)}
  }
  const reader = new ZipReader(new AttachmentReader(url,tail),{useWebWorkers:false})
  const entries = new Map((await reader.getEntries()).map(e=>[e.filename,e]))
  const archive = {file(name) {
    const entry = entries.get(name)
    if (!entry || entry.directory) return null
    return {_data:{uncompressedSize:entry.uncompressedSize}, async async(type) {
      const bytes = await entry.getData(new Uint8ArrayWriter(),{checkSignature:true})
      if (type === 'string') return new TextDecoder().decode(bytes)
      if (type === 'arraybuffer') return bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength)
      return bytes
    }}
  }}
  return {size:tail.size,archive}
}

// IDs identify immutable uploads. Reauthorize cached files before every reuse.
export async function readModelAttachment(file, baseDigest, parse) {
  const id = Number(file.fileId || file.id)
  if (!Number.isSafeInteger(id) || id < 1) throw Error('模型附件编号无效')
  const token = localStorage.getItem('token')
  if (token !== session) { cache.clear(); session = token }
  const url = '/files/'+id
  const key = id+'/'+baseDigest
  const hit = cache.get(key)
  if (hit) {
    try {
      const check = await request.head(url,options)
      if (Number(check.headers['content-length']) === hit.size && token === localStorage.getItem('token')) return hit.result
    } catch (error) {
      if (![405,501].includes(error.response?.status)) { cache.delete(key); throw error }
    }
    cache.delete(key)
  }
  const remote = await openArchive(url)
  const result = await parse(remote)
  if (session === token && token === localStorage.getItem('token')) {
    if (cache.size >= 8) cache.delete(cache.keys().next().value)
    cache.set(key,{size:remote.size,result})
  }
  return result
}
