// The front and rear thresholds follow the r3.3 ground-floor colonnade. Door
// types and approximate bays follow the owner's annotated exterior views.
import * as T from 'three'

const doors = [
  {x:40, z:54.44, side:1, radius:1.58},
  {x:35.85, z:54.44, side:1},
  {x:43.35, z:54.44, side:1},
  {x:21.1, z:23.39, side:-1},
  {x:53.1, z:23.39, side:-1}
]
const ground = .075
const glassHeight = 2.85
const pick = list => list[Math.floor(Math.random()*list.length)]
const point = (x,z) => new T.Vector2(x,z)

// Cut only the five openings in the existing ground-floor facade. The depth
// span includes both the recessed face and the simplified curtain-wall skin.
function openFacade(floor) {
  const cuts=doors.map(d=>{
    const half=d.radius||1.36
    return '(abs(vEntry.x-'+d.x.toFixed(2)+')<'+half.toFixed(2)+' && abs(vEntry.z-'+d.z.toFixed(2)+')<1.52)'
  }).join(' || ')
  const materials=new Map()
  function cut(material){
    if(materials.has(material))return materials.get(material)
    const copy=material.clone()
    copy.onBeforeCompile=shader=>{
      shader.vertexShader='varying vec3 vEntry;\n'+shader.vertexShader.replace('#include <project_vertex>',
        '#include <project_vertex>\nvEntry=(modelMatrix*vec4(transformed,1.0)).xyz;')
      shader.fragmentShader='varying vec3 vEntry;\n'+shader.fragmentShader.replace('#include <clipping_planes_fragment>',
        '#include <clipping_planes_fragment>\nif(vEntry.y>0.03 && vEntry.y<2.98 && ('+cuts+'))discard;')
    }
    materials.set(material,copy)
    return copy
  }
  floor.traverse(node=>{
    if(node.isMesh)node.material=Array.isArray(node.material)?node.material.map(cut):cut(node.material)
  })
}
export function createPlaza(floor) {
  openFacade(floor)
  const plaza=new T.Group()
  plaza.name='首层玻璃门及模拟人流'
  floor.add(plaza)
  const surface=(color,options={})=>new T.MeshStandardMaterial({color,...options})
  const glass=surface(0x9ed9df,{transparent:true,opacity:.47,side:T.DoubleSide,depthWrite:false,roughness:.13})
  const metal=surface(0x83959b,{metalness:.55,roughness:.33})
  const rail=surface(0xc1d3d5), stripe=surface(0xcedfdf)
  const cube=new T.BoxGeometry(1,1,1),ball=new T.SphereGeometry(1,9,6)
  function shape(parent,geometry,material,x,y,z,sx=1,sy=1,sz=1){
    const mesh=new T.Mesh(geometry,material)
    mesh.position.set(x,y,z);mesh.scale.set(sx,sy,sz);parent.add(mesh)
    return mesh
  }
  const box=(parent,mat,x,y,z,w,h,d)=>shape(parent,cube,mat,x,y,z,w,h,d)
  function leaf(parent,width,height){
    const group=new T.Group();parent.add(group)
    box(group,glass,0,height/2,0,width,height,.047)
    for(const x of [-width/2,width/2])box(group,metal,x,height/2,0,.044,height,.055)
    for(const y of [.07,height-.045])box(group,metal,0,y,0,width,.075,.06)
    box(group,stripe,0,1.15,0,width,.055,.052)
    return group
  }
  function assemble(d,i){
    const frame=new T.Group();plaza.add(frame);frame.position.set(d.x,ground,d.z)
    frame.name=['正门中央玻璃旋转门','正门左侧双扇玻璃推拉门','正门右侧双扇玻璃推拉门','背面双扇玻璃推拉门一','背面双扇玻璃推拉门二'][i]
    if(d.radius){
      const rotor=new T.Group();frame.add(rotor)
      const r=d.radius
      for(let j=0;j<4;j++){
        const pivot=new T.Group();rotor.add(pivot);pivot.rotation.y=j*Math.PI/2
        leaf(pivot,r-.12,glassHeight-.09).position.x=r/2
      }
      for(const offset of [.29*Math.PI,1.29*Math.PI]){
        const arc=new T.CylinderGeometry(r,r,glassHeight,16,1,true,offset,.42*Math.PI)
        shape(frame,arc,glass,0,glassHeight/2,0)
      }
      shape(frame,new T.CylinderGeometry(r+.05,r+.05,.15,36),metal,0,glassHeight+.025,0)
      shape(frame,new T.CylinderGeometry(.07,.07,glassHeight,10),metal,0,glassHeight/2,0)
      rotor.rotation.y=Math.PI/4
      return {...d,rotor}
    }
    const leaves=[leaf(frame,1.31,glassHeight),leaf(frame,1.31,glassHeight)]
    for(const sign of [-1,1])box(frame,metal,sign*1.36,glassHeight/2,0,.095,glassHeight,.14)
    box(frame,metal,0,glassHeight+.055,0,2.87,.15,.19)
    box(frame,rail,0,.018,0,2.76,.035,.18)
    leaves.forEach((node,j)=>{node.position.x=(j?1:-1)*.66})
    return {...d,leaves,opening:0}
  }
  const gates=doors.map(assemble)
  const skins=[0xb77e59,0xd6a47f,0xebc2a1].map(c=>surface(c))
  const coats=[0x365670,0x944f45,0x497063,0xbc8a47,0x53617b,0xeee5ce].map(c=>surface(c))
  const hair=surface(0x332c2b),pants=surface(0x324455),shoes=surface(0x282c31)
  function walker(){
    const body=new T.Group();plaza.add(body);body.name='模拟行人'
    const skin=pick(skins),coat=pick(coats)
    shape(body,ball,skin,0,1.58,0,.14,.18,.14)
    shape(body,ball,hair,0,1.68,-.018,.143,.10,.139)
    shape(body,ball,coat,0,1.12,0,.235,.35,.157)
    const legs=[],arms=[]
    for(const side of [-1,1]){
      const leg=new T.Group();leg.position.set(side*.096,.86,0);body.add(leg)
      shape(leg,ball,pants,0,-.37,0,.094,.39,.094)
      shape(leg,ball,shoes,0,-.775,.037,.098,.075,.14);legs.push(leg)
      const arm=new T.Group();arm.position.set(side*.204,1.33,0);body.add(arm)
      shape(arm,ball,coat,side*.035,-.224,0,.067,.23,.07)
      shape(arm,ball,skin,side*.039,-.456,.008,.053,.081,.055);arms.push(arm)
    }
    body.scale.setScalar(.94+Math.random()*.13)
    return {body,legs,arms,speed:1.13+Math.random()*.46,stride:Math.random()*6.28}
  }
  function trip(gate,leaving){
    const {x,z,side,radius}=gate
    const outside=side===1?65+Math.random()*8:13+Math.random()*4
    const route=[point(pick([6,70]),outside),point(x+.55*(Math.random()-.5),outside),point(x,z+side*3)]
    if(radius){
      for(let j=0;j<=18;j++){
        const angle=j*Math.PI/18
        route.push(point(x+Math.sin(angle)*radius*.62,z+Math.cos(angle)*radius*.62))
      }
    }else route.push(point(x,z))
    route.push(point(x,z-side*3.2))
    return leaving?route.reverse():route
  }
  const walkers=Array.from({length:12},(_,i)=>{
    const actor=walker(),gate=i===0?gates[0]:gates[1+(i-1)%4]
    const path=trip(gate,i%2===1)
    const leaving=i%2===1
    const next=i<5?(leaving?1:3):(leaving?path.length-1:1)
    const position=i<5?path[next-1].clone():path[next-1].clone().lerp(path[next],Math.random())
    actor.body.position.set(position.x,ground,position.y)
    return {...actor,gate,path,next,inside:i%2===0,wait:0}
  })
  function continueTrip(w){
    if(w.inside){
      w.path=trip(w.gate,true)
    }else{
      const options=gates.filter(g=>g.side===w.gate.side && Boolean(g.rotor)===Boolean(w.gate.rotor))
      w.gate=pick(options)
      w.path=trip(w.gate,false)
      // The next outdoor start is joined on the same paved side of the building.
    }
    w.inside=!w.inside;w.next=0;w.wait=1+Math.random()*3
  }
  function update(dt){
    for(const w of walkers){
      if(w.wait>0){w.wait-=dt;continue}
      const p=w.body.position,goal=w.path[w.next],dx=goal.x-p.x,dz=goal.y-p.z
      const distance=Math.hypot(dx,dz),step=Math.min(distance,w.speed*dt)
      if(distance>.0001){
        p.x+=dx/distance*step;p.z+=dz/distance*step;w.body.rotation.y=Math.atan2(dx,dz)
        w.stride+=step*5.1
        w.legs.forEach((leg,i)=>{leg.rotation.x=Math.sin(w.stride+i*Math.PI)*.42;w.arms[i].rotation.x=-leg.rotation.x*.85})
      }
      if(step>=distance && ++w.next===w.path.length)continueTrip(w)
    }
    for(const g of gates){
      const nearby=walkers.filter(w=>w.gate===g && Math.abs(w.body.position.x-g.x)<2 && Math.abs(w.body.position.z-g.z)<3.3)
      if(g.rotor){
        const rider=nearby.find(w=>Math.abs(w.body.position.z-g.z)<g.radius*.62)
        if(rider){
          const p=rider.body.position
          g.rotor.rotation.y=Math.atan2(p.x-g.x,p.z-g.z)+Math.PI/4
        }
      }else{
        g.opening=T.MathUtils.damp(g.opening,nearby.length?1:0,4.5,dt)
        g.leaves.forEach((node,i)=>{node.position.x=(i?1:-1)*(.66+1.17*g.opening)})
      }
    }
  }
  return {update}
}
