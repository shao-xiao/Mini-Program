// @vitest-environment node
import {beforeAll,expect,it,vi} from 'vitest'
import JSZip from 'jszip'
import request from '../../src/utils/request'
import {readModelAttachment} from '../../src/components/spaces/floorArchiveReader'

vi.mock('../../src/utils/request',()=>({default:{get:vi.fn(),head:vi.fn()}}))
let bytes
beforeAll(async()=>{
  vi.stubGlobal('localStorage',{getItem:()=> 'test session'})
  const zip=new JSZip()
  zip.file('drawing.pdf',new Uint8Array(400000))
  zip.file('model.json','{"floor":-2}')
  bytes=await zip.generateAsync({type:'uint8array',compression:'STORE'})
})

it('reads the manifest without the full PDF and checks attachment access again on reopen',async()=>{
  let transferred=0
  request.get.mockImplementation(async(_,options)=>{
    const [from,to]=options.headers.Range.slice(6).split('-')
    const start=from?Number(from):Math.max(0,bytes.length-Number(to))
    const end=from?Number(to):bytes.length-1
    const part=bytes.slice(start,end+1)
    transferred+=part.length
    return {status:206,headers:{'content-range':`bytes ${start}-${end}/${bytes.length}`},data:part.buffer}
  })
  const parse=vi.fn(async({archive})=>JSON.parse(await archive.file('model.json').async('string')))
  expect(await readModelAttachment({fileId:1},'base',parse)).toEqual({floor:-2})
  expect(transferred).toBeLessThan(80000)
  request.head.mockResolvedValueOnce({headers:{'content-length':String(bytes.length)}})
  expect(await readModelAttachment({fileId:1},'base',parse)).toEqual({floor:-2})
  expect(parse).toHaveBeenCalledTimes(1)
  request.head.mockRejectedValueOnce({response:{status:403}})
  await expect(readModelAttachment({fileId:1},'base',parse)).rejects.toMatchObject({response:{status:403}})
})

it('uses a full 200 response directly when Range is unavailable',async()=>{
  request.get.mockResolvedValueOnce({status:200,data:bytes.buffer})
  const parse=async({archive})=>archive.file('model.json').async('string')
  expect(await readModelAttachment({fileId:2},'base',parse)).toBe('{"floor":-2}')
  expect(request.get).toHaveBeenCalledTimes(1)
})
