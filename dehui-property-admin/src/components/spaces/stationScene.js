import * as T from 'three'
import {stationMedia} from './plumbingStations'
import {selectionColor} from './garageMarkings'

export function addStations(scene,stations,retain,pickables,labels,roomInstances,parts) {
  const {part,pipe,tube,cone,ring,createEquipment}=parts
  const groups=[], selectedEdges=[], movers=[], up=new T.Vector3(0,1,0)
  const v=(x,y,z)=>new T.Vector3(x,y,z)
  stations.forEach((s,index)=>{
    const group=new T.Group();group.userData={group:'station',index};scene.add(group);groups.push(group)
    const [x,z]=s.anchor
    const lift=s.kind==='处理间'
    const room=roomInstances.get(s.name)
    let assembly=room?.assembly
    if (assembly) {
      // The root already includes its metre-based placement in the floor.
      group.add(room.root)
    } else {
      assembly=createEquipment(s)
      assembly.equipment.position.set(x,0,z)
      group.add(assembly.equipment)
    }
    const {base,equipment}=assembly
    const edge=new T.LineSegments(retain(new T.EdgesGeometry(base.geometry)),
      retain(new T.LineBasicMaterial({color:selectionColor})))
    edge.position.copy(base.position);edge.scale.copy(base.scale).multiplyScalar(1.04)
    equipment.add(edge);selectedEdges.push(edge)
    const routeParts=[]
    const y0=room?.root.position.y ?? 0
    const levels={排水:1.61+y0,冲洗:1.12+y0,通气:2.14+y0}
    function leg(a,b,medium,directional=true) {
      const length=a.distanceTo(b)
      if(length<.01)return
      const dx=b.clone().sub(a).normalize()
      const color=stationMedia[medium]
      const shell=pipe(group,a,b,medium === '排水' ? .115 : .088,color,.56)
      routeParts.push(shell)
      const rot=new T.Quaternion().setFromUnitVectors(up,dx)
      if(directional && length>.55){
        const arrow=part(group,cone,color,a.clone().lerp(b,.60),v(.20,Math.min(.52,length*.42),.20))
        arrow.quaternion.copy(rot)
      }
      if(length>.30){
        const mark=part(group,directional?cone:tube,medium==='通气'?'#eed89e':'#87dee4',a.clone(),v(.085,.24,.085))
        mark.quaternion.copy(rot);movers.push({mark,a,b,length,directional,index})
      }
    }
    for(const run of s.runs){
      const [a,b]=run.points, y=levels[run.medium]
      leg(v(a[0],y,a[1]),v(b[0],y,b[1]),run.medium,run.medium!=='通气')
    }
    if (lift) {
      const rinse = s.runs.filter(run=>run.medium==='冲洗').at(-1).points[1]
      const tap = v(rinse[0],levels.冲洗,rinse[1])
      part(group,tube,'#bc8653',tap,v(.14,.25,.14))
      part(group,ring,'#a66038',tap.clone().add(v(0,.12,.12)),v(.78,.78,.78))
    }
    for(const outlet of s.outlets){
      const [ox,oz]=outlet.point
      leg(v(ox,levels[outlet.medium],oz),v(ox,3.3,oz),outlet.medium,outlet.medium!=='通气')
    }
    group.userData.routeParts=routeParts
    group.traverse(child=>{if(child.isMesh){child.userData={group:'station',index};pickables.push(child)}})
    labels.add('station',index,s.name,[[x-.6,z],[x+.6,z],[x,z+.3]],()=>2.4)
  })
  let chosen=0
  function select(index) {
    chosen=index
    selectedEdges.forEach((e,i)=>{e.visible=i===index})
    groups.forEach((g,i)=>g.userData.routeParts.forEach(p=>{
      if(!p.userData.original)p.userData.original=p.material
      if(i===index && !p.userData.activeMaterial){
        const mat=retain(p.userData.original.clone());mat.color.set(selectionColor)
        p.userData.activeMaterial=mat
      }
      p.material=i===index?p.userData.activeMaterial:p.userData.original
    }))
  }
  return {active:true,select,isolate(on){groups.forEach((g,i)=>{g.visible=!on||i===chosen})},update(time){
    movers.forEach(({mark,a,b,length,directional,index})=>{
      const t=time/Math.max(1.2,length/2)+index*.13
      const fraction=directional?t%1:.5-.45*Math.cos(t*2)
      mark.position.copy(a).lerp(b,fraction)
    })
  }}
}
