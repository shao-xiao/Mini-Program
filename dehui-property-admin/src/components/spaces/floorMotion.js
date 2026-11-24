import * as T from 'three'
import { parkingRoutes,vehiclePose } from './garageDrive'

// Schematic objects are transient scene children. They have no business records.
export function createFloorMotion(retain, exportMotion=false) {
  const cube=retain(new T.BoxGeometry(1,1,1))
  const round=retain(new T.CylinderGeometry(1,1,1,14))
  const materials=new Map(), ticks=[], clips=[]
  function part(parent,geometry,color,x,y,z,w,h,d) {
    if (!materials.has(color)) materials.set(color,retain(new T.MeshLambertMaterial({color})))
    const m=new T.Mesh(geometry,materials.get(color))
    m.position.set(x,y,z)
    m.scale.set(w,h,d)
    parent.add(m)
    return m
  }
  const box=(g,color,x,y,z,w,h,d)=>part(g,cube,color,x,y,z,w,h,d)
  const cylinder=(g,color,x,y,z,r,h)=>part(g,round,color,x,y,z,r,h,r)
  function lamp(g,x,y,z,offset) {
    const lamp=box(g,0x7af6af,x,y,z,.095,.08,.09)
    if (exportMotion) {
      const period=1.9
      const times=[0,1.2-offset,period-offset,period].filter((t,i,a)=>t>=0 && t<=period && a.indexOf(t)===i).sort((a,b)=>a-b)
      const values=times.flatMap(t=>(t+offset+.0001)%period<1.2 ? lamp.scale.toArray() : [0,0,0])
      clips.push(new T.VectorKeyframeTrack(lamp.uuid+'.scale',times,values,T.InterpolateDiscrete))
    }
    ticks.push(t=>{lamp.visible=(t+offset)%1.9<1.2})
  }
  function rack(g,electric) {
    box(g,0x364953,0,.86,0,.80,1.72,.64)
    box(g,0x879ba5,0,1.75,0,.84,.08,.68)
    if (electric) {
      box(g,0x607781,0,.90,.34,.69,1.49,.055)
      box(g,0xb5d5d3,0,1.27,.39,.38,.20,.04)
      lamp(g,.21,1.50,.39,0)
    } else {
      for (let i=0;i<4;i++) {
        const y=.54+i*.26
        box(g,0x7a8d95,0,y,.335,.68,.18,.06)
        box(g,0x253741,-.1,y,.372,.31,.07,.02)
        lamp(g,.20,y,.38,i*.42)
      }
    }
    lamp(g,.22,1.82,.19,.3)
  }
  function rotary(g,pump) {
    box(g,0x415965,0,.12,0,1.18,.24,1.18)
    cylinder(g,0x6e8991,0,.42,0,.54,.53)
    cylinder(g,0x253f4a,0,.70,0,.47,.05)
    const rotor=new T.Group()
    rotor.position.y=.75
    g.add(rotor)
    for (let i=0;i<4;i++) {
      const arm=new T.Group()
      arm.rotation.y=Math.PI*i/2
      rotor.add(arm)
      const blade=box(arm,pump ? 0x74bbce : 0xb5d5cd,.21,0,.045,.35,.055,.14)
      blade.rotation.y=.32
    }
    cylinder(g,0xc9d1d1,0,.80,0,.105,.08)
    if (pump) {
      box(g,0x4d889c,.59,.33,0,.56,.19,.19)
      cylinder(g,0x4d889c,.81,.50,0,.11,.49)
    }
    const speed=pump ? 2.7 : 4.1
    if (exportMotion) {
      const times=[0,1,2,3,4].map(i=>i*Math.PI/(2*speed))
      const up=new T.Vector3(0,1,0)
      const values=times.flatMap(t=>new T.Quaternion().setFromAxisAngle(up,t*speed).toArray())
      clips.push(new T.QuaternionKeyframeTrack(rotor.uuid+'.quaternion',times,values))
    }
    ticks.push(t=>{rotor.rotation.y=t*speed})
  }
  function equipment(mesh,room,pickables) {
    let model
    if (room.name.includes('弱电间')) model=g=>rack(g,false)
    else if (room.name.includes('强电间')) model=g=>rack(g,true)
    else if (/送风机房|排烟机房/.test(room.name)) model=g=>rotary(g,false)
    else if (room.name.includes('污水处理间')) model=g=>rotary(g,true)
    else return
    // Fit inside the polygon, including re-entrant corners of technical rooms.
    const pts=room.points.map(([x,z])=>new T.Vector2(x,z))
    const bounds=new T.Box2().setFromPoints(pts), size=bounds.getSize(new T.Vector2())
    let best, clearance=0
    for (let ix=1;ix<10;ix++) for (let iz=1;iz<10;iz++) {
      const p=new T.Vector2(bounds.min.x+size.x*ix/10,bounds.min.y+size.y*iz/10)
      let inside=false, dist=Infinity
      for (let i=0;i<pts.length;i++) {
        const a=pts[i], b=pts[(i+1)%pts.length], ab=b.clone().sub(a)
        if ((a.y>p.y)!==(b.y>p.y) && p.x<(b.x-a.x)*(p.y-a.y)/(b.y-a.y)+a.x) inside=!inside
        const s=T.MathUtils.clamp(p.clone().sub(a).dot(ab)/(ab.lengthSq()||1),0,1)
        dist=Math.min(dist,p.distanceTo(a.clone().addScaledVector(ab,s)))
      }
      if (inside && dist>clearance) {best=p;clearance=dist}
    }
    if (!best) return
    const g=new T.Group()
    g.position.set(best.x,0,best.y)
    mesh.add(g)
    model(g)
    mesh.userData.hasEquipment=true
    g.traverse(child=>{if (child.isMesh) {child.userData=mesh.userData;pickables.push(child)}})
  }
  function traffic(scene,garage) {
    const colors=[0x42758c,0xb56746,0x557b68]
    const routes=parkingRoutes(garage)
    const period=Math.max(...routes.map(r=>r.driveTime+2*r.turnTime+4))
    routes.forEach((route,i)=>{
      const car=new T.Group()
      scene.add(car)
      box(car,colors[i],0,.50,0,1.48,.49,3.52)
      box(car,0x476071,0,.90,-.13,1.28,.40,1.88)
      box(car,colors[i],0,1.13,-.19,1.18,.09,1.34)
      for (const x of [-.68,.68]) {
        for (const z of [-1.02,1.04]) {
          const wheel=cylinder(car,0x273139,x,.31,z,.29,.19)
          wheel.rotation.z=Math.PI/2
        }
        box(car,0xffe6af,x*.72,.56,1.77,.24,.10,.04)
        box(car,0xb04c3b,x*.72,.56,-1.77,.24,.10,.04)
      }
      ticks.push(t=>{
        const duration=route.driveTime+2*route.turnTime+4
        const pose=vehiclePose(route,t*duration/period+route.driveTime+[1.1,10,21][i])
        car.position.set(pose.x,.06,pose.z)
        car.rotation.y=pose.heading
      })
    })
  }
  return {equipment,traffic,animationClips(){return clips.length ? [new T.AnimationClip('设备循环示意',-1,clips)] : []},get active(){return ticks.length>0},update(time){for(const tick of ticks) tick(time)}}
}
