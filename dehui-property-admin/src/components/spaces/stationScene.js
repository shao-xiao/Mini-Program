import * as T from 'three'
import {addSpaceCutaway} from './spaceCutaway'
import {stationMedia} from './plumbingStations'
import {selectionColor} from './garageMarkings'

export function addStations(scene,stations,retain,pickables,labels) {
  const cube=retain(new T.BoxGeometry()), tube=retain(new T.CylinderGeometry(1,1,1,16))
  const cone=retain(new T.ConeGeometry(1,1,12)), ring=retain(new T.TorusGeometry(.16,.035,8,18))
  const mats=new Map(), groups=[], selectedEdges=[], movers=[], up=new T.Vector3(0,1,0)
  const v=(x,y,z)=>new T.Vector3(x,y,z)
  const colors={steel:'#728b98', dark:'#394e58', pump:'#397989', valve:'#bc8653'}
  function part(parent,geometry,color,pos,scale,opacity=1){
    const key=color+'/'+opacity
    if(!mats.has(key)) mats.set(key,retain(new T.MeshPhongMaterial({color,shininess:65,
      transparent:opacity<1,opacity,depthWrite:opacity===1})))
    const mesh=new T.Mesh(geometry,mats.get(key))
    mesh.position.copy(pos);mesh.scale.copy(scale);parent.add(mesh)
    return mesh
  }
  function pipe(parent,a,b,r,color,opacity=1){
    const delta=b.clone().sub(a)
    const mesh=part(parent,tube,color,a.clone().lerp(b,.5),v(r,delta.length(),r),opacity)
    mesh.quaternion.setFromUnitVectors(up,delta.normalize())
    return mesh
  }
  stations.forEach((s,index)=>{
    const group=new T.Group();group.userData={group:'station',index};scene.add(group);groups.push(group)
    const [x,z]=s.anchor
    const lift=s.kind==='处理间'
    const equipment=new T.Group();equipment.position.set(x,0,z);group.add(equipment)
    const base=part(equipment,cube,colors.dark,v(0,.09,0),v(1.9,.18,1.44))
    const interceptor = lift && s.model === 'BF-DZ-20'
    if (interceptor) {
      part(equipment,cube,colors.steel,v(0,.62,0),v(1.76,.98,1.1))
      for (const x of [-.58,0,.58]) {
        part(equipment,cube,'#b9c8cb',v(x,1.15,0),v(.53,.07,1.02))
        part(equipment,cube,colors.dark,v(x,1.21,0),v(.17,.055,.07))
      }
    } else if (lift) {
      part(equipment,tube,colors.steel,v(0,.66,0),v(.65,1.04,.65))
      part(equipment,tube,colors.dark,v(0,1.19,0),v(.69,.09,.69))
      part(equipment,tube,'#b9c8cb',v(0,1.25,0),v(.45,.06,.45))
    } else {
      part(equipment,tube,'#64adb9',v(0,.53,0),v(.79,.88,.79),.30)
    }
    if(lift){
      // Elevation fittings are schematic. The room boundary uses the A101 survey.
      part(equipment,cube,'#cfdbdc',v(-.69,1.05,-.73),v(.88,2.10,.09))
      part(equipment,cube,'#e4ecec',v(-.69,1.72,-.55),v(.54,.68,.18))
      part(equipment,cube,'#415b69',v(-.69,1.8,-.44),v(.34,.17,.025))
      part(equipment,tube,'#70bda5',v(-.55,1.55,-.44),v(.051,.07,.051))
      part(equipment,cube,'#9cdbe2',v(.57,.61,.59),v(.08,.61,.04))
    }
    const offsets=lift?[.36]:[-.42,.42]
    const motorZ=lift ? .66 : .28
    for(const offset of offsets){
      part(equipment,tube,colors.pump,v(offset,.57,motorZ),v(.22,.83,.22))
      for(const y of [.37,.47,.57,.67]) part(equipment,tube,colors.dark,v(offset,y,motorZ),v(.245,.045,.245))
      pipe(equipment,v(offset,.87,motorZ),v(offset,1.61,motorZ),.105,colors.pump)
      pipe(equipment,v(offset,1.61,motorZ),v(0,1.61,0),.105,colors.pump)
      part(equipment,tube,colors.valve,v(offset,1.22,motorZ),v(.15,.13,.15))
      part(equipment,ring,'#a66038',v(offset,1.34,motorZ+.14),v(.85,.85,.85))
    }
    const edge=new T.LineSegments(retain(new T.EdgesGeometry(base.geometry)),
      retain(new T.LineBasicMaterial({color:selectionColor})))
    edge.position.copy(base.position);edge.scale.copy(base.scale).multiplyScalar(1.04)
    equipment.add(edge);selectedEdges.push(edge)
    if(s.footprint){
      const footprint = new T.Shape(s.footprint.map(([u,w])=>new T.Vector2(u,-w)))
      const slab = new T.Mesh(retain(new T.ShapeGeometry(footprint)),retain(new T.MeshLambertMaterial({
        color:'#c8d7d7',side:T.DoubleSide,transparent:true,opacity:.48,depthWrite:false
      })))
      slab.rotation.x=-Math.PI/2
      slab.position.y=.015
      group.add(slab)
      addSpaceCutaway(group,{name:s.name,points:s.footprint},[],retain,pickables)
      const pts=s.footprint.map(([a,b])=>v(a,.06,b));pts.push(pts[0])
      group.add(new T.Line(retain(new T.BufferGeometry().setFromPoints(pts)),retain(new T.LineBasicMaterial({color:'#549e9d'}))))
    }
    const routeParts=[]
    const levels={排水:1.61,冲洗:1.12,通气:2.14}
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
      part(group,tube,colors.valve,tap,v(.14,.25,.14))
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
