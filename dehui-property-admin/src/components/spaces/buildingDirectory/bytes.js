import request from '../../../utils/request'
import { inflateSync } from 'three/addons/libs/fflate.module.js'

import {readArchive} from './warmFloor'
import exterior from './exterior.json'
import binding from './assembly.json'

export async function readBytes(key, signal, progress) {
  const [start, length, digest] = (key === 'tower' ? exterior : binding).ranges[key]
  const end = start + length - 1
  let bytes
  if (key === 'tower') {
    const pack = await readArchive({id:exterior.fileId,sha:exterior.archiveSha},progress)
    signal.throwIfAborted()
    bytes = pack.subarray(start,end+1)
  } else {
    const response = await request.get('/files/' + binding.fileId, {
      responseType: 'arraybuffer', binaryResponse: true, silent: true, timeout: 90000,
      headers: { Range: 'bytes=' + start + '-' + end }, signal, onDownloadProgress: progress
    })
    bytes = new Uint8Array(response.data)
    if (response.status === 200) bytes = bytes.subarray(start, end + 1)
    else if (!response.headers['content-range']?.startsWith('bytes ' + start + '-' + end + '/')) throw Error('模型版本不匹配')
  }
  if (bytes.length !== length) throw Error('模型读取不完整')
  const glb = inflateSync(bytes)
  const sha = new Uint8Array(await crypto.subtle.digest('SHA-256', glb))
  if (Array.from(sha, n => n.toString(16).padStart(2, '0')).join('') !== digest) throw Error('模型校验失败')
  signal.throwIfAborted()
  return glb.buffer
}
