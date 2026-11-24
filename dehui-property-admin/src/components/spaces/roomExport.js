import {GLTFExporter} from 'three/addons/exporters/GLTFExporter.js'
import {createStationParts} from './stationEquipment'
import {createFloorMotion} from './floorMotion'
import {createRoomInstance} from './roomModule'

// Build a clean instance so current selection, camera and animated pose never
// leak into the library asset. Restore extras.module.floorOrigin to reassemble.
export async function exportRoom(definition) {
  const resources=new Set()
  const retain=resource=>{resources.add(resource);return resource}
  try {
    const motion=createFloorMotion(retain,true)
    const {root}=createRoomInstance(definition,{retain,motion,parts:createStationParts(retain)})
    root.position.set(0,0,0)
    const bytes=await new GLTFExporter().parseAsync(root,{binary:true,animations:motion.animationClips()})
    const url=URL.createObjectURL(new Blob([bytes],{type:'model/gltf-binary'}))
    const link=document.createElement('a')
    link.href=url
    link.download=definition.name.replace(/[\\/:*?"<>|]/g,'_')+'.glb'
    link.click()
    setTimeout(()=>URL.revokeObjectURL(url),10000)
  } finally {
    resources.forEach(resource=>resource.dispose())
  }
}
