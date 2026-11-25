import * as T from 'three'
import { componentSimulation } from './componentSimulation'
import { pipeLayers } from './plumbingCatalog'
import { selectionColor } from './garageMarkings'

// The PDF fixes the plan coordinates. Vertical separation, fittings and fluid
// markers are display geometry, not measured elevations or a hydraulic solver.
export function addPlumbing(scene, items, retain, pickables, labels, instanced, sections) {
  const tube = retain(new T.CylinderGeometry(1, 1, 1, 12))
  const shell = retain(new T.CylinderGeometry(1, 1, 1, 16, 1, true))
  const box = retain(new T.BoxGeometry())
  const ball = retain(new T.SphereGeometry(1, 8, 6))
  const head = retain(new T.CylinderGeometry(.45, 1, 1, 10))
  const up = new T.Vector3(0, 1, 0)
  const axis = new T.Quaternion()
  const groups = new Map(), parts = [], media = [], basins = []
  const pitActions=[], pitFrames=new Map()
  const colors = new Map()
  function material(color, opacity = 1) {
    const key = color + '/' + opacity
    if (!colors.has(key)) colors.set(key, retain(new T.MeshPhongMaterial({
      color, shininess:65, specular:0x9aabb3, opacity, transparent:opacity < 1, depthWrite:opacity === 1
    })))
    return colors.get(key)
  }
  function part(geo, mat, position, scale, rotation, index) {
    const matrix = new T.Matrix4().compose(position, rotation, scale)
    const key = geo.uuid + mat.uuid
    if (!groups.has(key)) groups.set(key, {geo, mat, entries:[]})
    groups.get(key).entries.push({matrix, index})
    return {geo, matrix}
  }
  const glass = material('#c62936', .86)
  const metal = material('#576974'), brass = material('#b98a4c')
  const vec = (x,y,z) => new T.Vector3(x,y,z)
  items.forEach((item, index) => {
    const [x,z] = item.points[0]
    const color = pipeLayers[item.layer][1]
    if (item.kind === '集水坑') {
      labels.add('water',index,item.name,[[x-.5,z],[x+.5,z],[x,z+.3]],()=>2.4)
      const section=sections?.get(item.name)
      if(section) {
        const instance=section.clone(true)
        scene.add(instance)
        const action=componentSimulation(instance,item.name)
        if(action) pitActions.push(action)
        instance.traverse(node=>{
          if(node.isMesh || node.isLine) {node.userData.group='water';node.userData.index=index;pickables.push(node)}
        })
        const frame=new T.BoxHelper(instance,selectionColor)
        frame.material.depthTest=false
        frame.renderOrder=4
        frame.visible=false
        scene.add(frame)
        pitFrames.set(index,frame)
        return
      }
      parts[index] = part(box, metal, vec(x,.12,z), vec(1.9,.24,1.9), axis, index)
      const water = new T.Mesh(box, material('#49aec0',.62))
      water.position.set(x,.4,z)
      water.scale.set(1.62,.32,1.62)
      water.userData = {group:'water',index}
      scene.add(water); pickables.push(water); basins.push(water)
      for (const dx of [-.43,.43]) {
        part(tube, metal, vec(x+dx,.92,z), vec(.23,1.36,.23), axis, index)
        part(tube, brass, vec(x+dx,1.55,z), vec(.26,.13,.26), axis, index)
      }

      return
    }
    const y = item.system === 'sprinkler' ? 2.75 : 1.65
    if (item.kind === '喷头') {
      parts[index] = part(head, brass, vec(x,y-.09,z), vec(.14,.18,.14), axis, index)
      for (const side of [-1,1]) {
        part(box, brass, vec(x+side*.10,y-.25,z), vec(.035,.25,.05), axis, index)
      }
      part(ball, glass, vec(x,y-.25,z), vec(.05,.13,.05), axis, index)
      part(tube, metal, vec(x,y-.39,z), vec(.18,.04,.18), axis, index)
      return
    }
    const start = vec(x,item.kind === '立管' ? .1 : y,z)
    const end = item.kind === '立管' ? vec(x,3.15,z) : vec(item.points[1][0],y,item.points[1][1])
    const delta = end.clone().sub(start)
    const length = delta.length()
    const rotation = new T.Quaternion().setFromUnitVectors(up,delta.clone().normalize())
    const radius = item.kind === '立管' ? .19 : .13
    parts[index] = part(shell, material(color,.50), start.clone().lerp(end,.5), vec(radius,length,radius), rotation, index)
    if (length > .65) {
      for (const t of [.08,.92]) part(tube, metal, start.clone().lerp(end,t),vec(radius*1.16,.085,radius*1.16),rotation,index)
    }
    const gas = item.layer === '通气'
    if (length > .35) media.push({start,delta,rotation,gas,length,direction:item.direction})
  })
  for (const {geo,mat,entries} of groups.values()) {
    if (instanced) {
      const mesh = retain(new T.InstancedMesh(geo,mat,entries.length))
      entries.forEach(({matrix},i)=>mesh.setMatrixAt(i,matrix))
      mesh.userData = {group:'water', indices:entries.map(entry=>entry.index)}
      scene.add(mesh); pickables.push(mesh)
    } else {
      for (const {matrix,index} of entries) {
        const mesh = new T.Mesh(geo,mat)
        mesh.applyMatrix4(matrix); mesh.userData={group:'water',index}
        scene.add(mesh); pickables.push(mesh)
      }
    }
  }
  const marker = new T.Object3D()
  const streams = [false,true].map(gas=>{
    const runs = media.filter(run=>run.gas===gas)
    if (!runs.length) return null
    const mat = material(gas ? '#e7e1a8' : '#46cadd')
    const geo = gas ? ball : tube
    const batch = instanced ? retain(new T.InstancedMesh(geo,mat,runs.length)) : null
    const singles = batch ? [] : runs.map(()=>new T.Mesh(geo,mat))
    if (batch) { batch.frustumCulled=false; batch.instanceMatrix.setUsage(T.DynamicDrawUsage); scene.add(batch) }
    else singles.forEach(mesh=>scene.add(mesh))
    return {runs,batch,singles}
  }).filter(Boolean)
  const selected = new T.Mesh(box,material(selectionColor,.78))
  selected.visible=false; scene.add(selected)
  const routes=new Map()
  items.forEach((item,i)=>{
    if(!item.stationName || !parts[i] || !['立管','管段'].includes(item.kind)) return
    if(!routes.has(item.stationName)) routes.set(item.stationName,[])
    routes.get(item.stationName).push(parts[i])
  })
  const routeMarks=[]
  const routeMaterial=material(selectionColor,.42)
  const longest=Math.max(0,...Array.from(routes.values(),route=>route.length))
  for(let i=0;i<longest;i++){
    const mark=new T.Mesh(tube,routeMaterial)
    mark.visible=false
    scene.add(mark)
    routeMarks.push(mark)
  }
  function select(index) {
    const route=routes.get(items[index]?.stationName) || []
    routeMarks.forEach((mark,i)=>{
      mark.visible=i<route.length
      if(!mark.visible) return
      route[i].matrix.decompose(mark.position,mark.quaternion,mark.scale)
      mark.scale.x*=1.08
      mark.scale.z*=1.08
    })
    for (const [i,frame] of pitFrames) frame.visible=i===index
    const p = parts[index]
    selected.visible=Boolean(p)
    if (!p) return
    selected.geometry=p.geo
    p.matrix.decompose(selected.position,selected.quaternion,selected.scale)
    selected.scale.multiplyScalar(1.07)
  }
  return {
    select, active:streams.length>0 || basins.length>0 || pitActions.length>0,
    dispose(){pitActions.forEach(action=>action.dispose());pitFrames.forEach(frame=>frame.dispose())},
    update(time) {
      pitActions.forEach(action=>action.update(time))
      for (const {runs,batch,singles} of streams) {
        runs.forEach(({start,delta,rotation,gas,length,direction},i)=>{
          // Reversible motion on un-oriented CAD runs does not assign a design flow direction.
          const progress=(time/Math.max(1.2,length/2)+i*.13)%1
          const fraction=direction ? (direction>0 ? progress : 1-progress)
            : .5-.42*Math.cos(time*Math.min(1.8,3.5/length)+i)
          marker.position.copy(start).addScaledVector(delta,fraction)
          marker.quaternion.copy(rotation)
          marker.scale.set(.09,gas ? .105 : Math.min(.85,length*.14),.09)
          marker.updateMatrix()
          if (batch) batch.setMatrixAt(i,marker.matrix)
          else { singles[i].matrixAutoUpdate=false; singles[i].matrix.copy(marker.matrix); singles[i].matrixWorldNeedsUpdate=true }
        })
        if (batch) batch.instanceMatrix.needsUpdate=true
      }
      const phase=time%8/8
      for (const basin of basins) {
        basin.scale.y=.15+.95*(phase<.62 ? phase/.62 : (1-phase)/.38)
        basin.position.y=.24+basin.scale.y/2
      }
    }
  }
}
