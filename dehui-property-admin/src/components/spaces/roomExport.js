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
    await saveGlb(root,motion.animationClips())
  } finally {
    resources.forEach(resource=>resource.dispose())
  }
}

async function saveGlb(root,animations=[]) {
  const bytes=await new GLTFExporter().parseAsync(root,{binary:true,animations})
  const url=URL.createObjectURL(new Blob([bytes],{type:'model/gltf-binary'}))
  const link=document.createElement('a')
  link.href=url
  link.download=root.name.replace(/[\\/:*?"<>|]/g,'_')+'.glb'
  link.click()
  setTimeout(()=>URL.revokeObjectURL(url),10000)
}

export async function exportSump(station) {
  const resources=new Set()
  const retain=resource=>{resources.add(resource);return resource}
  try {
    const {equipment}=createStationParts(retain).createEquipment(station)
    equipment.name=station.name
    equipment.userData.module={
      version:1, room:station.name, kind:'集水坑', units:'m', up:'Y',
      floorOrigin:[station.anchor[0],0,station.anchor[1]], source:'P301 / P202',
      equipmentGeometry:'设备及坑体示意外形，安装深度待核',
      equipment:{name:station.machine,model:station.model,performance:station.performance,duty:station.duty},
      levels:station.levels, connection:station.connection
    }
    await saveGlb(equipment)
  } finally { resources.forEach(resource=>resource.dispose()) }
}
