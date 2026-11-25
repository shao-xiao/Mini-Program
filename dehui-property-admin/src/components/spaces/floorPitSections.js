import { shallowRef, ref, watch } from 'vue'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { checkedEntry } from './b2Disciplines'

export function useFloorPitSections(props) {
  const sections=shallowRef(new Map())
  const failure=ref('')
  watch([()=>props.system,()=>props.objectArchive,()=>props.objects],async ([system,archive,objects],_,cleanup)=>{
    let active=true
    const owned=new Set()
    cleanup(()=>{active=false;owned.forEach(resource=>resource.dispose())})
    sections.value=new Map()
    failure.value=''
    if(system!=='drain' || !archive) return
    const records=(objects || []).filter(record=>record.verticalExtentVerified && record.origin)
    const results=await Promise.allSettled(records.map(async record=>{
      const bytes=await checkedEntry(archive,record.file,record.sha256,8*1024*1024)
      if(!active) return
      const {scene}=await new GLTFLoader().parseAsync(bytes,'')
      scene.traverse(node=>{
        for(const resource of [node.geometry,...[].concat(node.material || [])].filter(Boolean)) {
          if(active) owned.add(resource)
          else resource.dispose()
        }
      })
      scene.position.fromArray(record.origin)
      return [record.name,scene]
    }))
    if(!active) return
    sections.value=new Map(results.flatMap(result=>result.status==='fulfilled' && result.value ? [result.value] : []))
    if(results.some(result=>result.status==='rejected')) failure.value='部分坑体 GLB 读取失败，对应点位显示竣工图示意。'
  },{immediate:true})
  return {sections,failure}
}
