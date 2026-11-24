import * as T from 'three'

import {pipeLayers} from './plumbingCatalog'

export function addPlumbing(scene, items, retain, pickables, meshes, labels) {
  const materials = new Map()
  const basins = []
  function material(color) {
    if (!materials.has(color)) materials.set(color, retain(new T.MeshLambertMaterial({color})))
    return materials.get(color)
  }
  const tube = retain(new T.CylinderGeometry(1,1,1,10))
  const cube = retain(new T.BoxGeometry(1,1,1))
  const up = new T.Vector3(0,1,0)
  items.forEach((item,index) => {
    const [x,z] = item.points[0]
    const mesh = new T.Mesh(item.kind==='集水坑' ? cube : tube, material(pipeLayers[item.layer][1]))
    mesh.userData = {group:'water',index}
    if (item.kind==='管段') {
      const [endX,endZ] = item.points[1]
      const delta = new T.Vector3(endX-x,0,endZ-z)
      mesh.position.set((x+endX)/2,1.45,(z+endZ)/2)
      mesh.scale.set(.115,delta.length(),.115)
      mesh.quaternion.setFromUnitVectors(up,delta.normalize())
    } else if (item.kind==='立管') {
      mesh.position.set(x,1.2,z)
      mesh.scale.set(.23,2.4,.23)
    } else {
      mesh.position.set(x,.24,z)
      mesh.scale.set(1.9,.45,1.9)
      const water = new T.Mesh(cube,material('#65c9ce'))
      water.position.set(x,.65,z)
      water.scale.set(1.58,.6,1.58)
      water.userData=mesh.userData
      pickables.push(water)
      scene.add(water)
      for (const dx of [-.43,.43]) {
        const pump = new T.Mesh(tube,material('#384e5c'))
        pump.position.set(x+dx,1.05,z)
        pump.scale.set(.25,1.45,.25)
        pump.userData=mesh.userData
        pickables.push(pump)
        scene.add(pump)
      }
      basins.push(water)
      labels.add('water',index,item.name,[[x-.5,z],[x+.5,z],[x,z+.3]],()=>2.5)
    }
    meshes.set('water:'+index,mesh)
    pickables.push(mesh)
    scene.add(mesh)
  })
  return {
    active:basins.length>0,
    update(time) {
      // A teaching cycle only. Hydraulic velocity and elapsed seconds are illustrative.
      const phase = time % 8 / 8
      const level = phase < .62 ? phase/.62 : (1-phase)/.38
      for (const basin of basins) {
        basin.scale.y=.15+.95*level
        basin.position.y=.45+basin.scale.y/2
      }
    }
  }
}
