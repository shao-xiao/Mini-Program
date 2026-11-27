import * as T from 'three'
import lines from './floorPaths.json'

// Floor X/Z traces follow the M207 dual-use duct bodies. Only the source locations,
// capture paths in room air, timing, and handoff between adjacent fittings are staged.
export function createFloorCycle(scene, routes, ordinary) {
  const group = new T.Group(), resources = []
  const own = item => (resources.push(item), item)
  const material = color => own(new T.MeshBasicMaterial({color, depthTest:false, depthWrite:false, toneMapped:false}))
  const orange = material(0xff5900), blue = material(0x006af5), white = material(0xffffff)
  const violet = material(0x6826a5)
  const ringGeometry = own(new T.TorusGeometry(1.25,.13,8,48))
  const arrowGeometry = own(new T.ConeGeometry(.64,1.8,12))
  const axis = new T.Vector3(0,1,0)
  function mesh(geometry, mat, order) {
    const m = new T.Mesh(geometry,mat)
    m.renderOrder=order
    group.add(m)
    return m
  }
  function ring(at,mat) {
    const node=mesh(ringGeometry,mat,42)
    node.rotation.x=-Math.PI/2
    node.position.copy(at)
    return node
  }
  function arrow(mat) {
    const node=mesh(arrowGeometry,mat,48)
    const rim=new T.Mesh(arrowGeometry,white)
    rim.position.y=-.13
    rim.scale.set(1.21,1.08,1.21)
    rim.renderOrder=47
    node.add(rim)
    return node
  }
  function routeHighlight(path) {
    // Reveal a narrow strip between the first and last packet, following every bend.
    const steps=Math.ceil(path.getLength()*4), sides=6
    const geometry=own(new T.TubeGeometry(path,steps,.16,sides,false))
    const mat=material(0xffb000)
    const strip=mesh(geometry,mat,46)
    return (time,enabled)=>{
      const first=T.MathUtils.clamp((time-4.1)/7.1,0,1)
      const last=T.MathUtils.clamp((time-4.1-7*.98)/7.1,0,1)
      const begin=Math.floor(last*steps),end=Math.floor(first*steps)
      strip.visible=enabled&&end>begin
      geometry.setDrawRange(begin*sides*6,(end-begin)*sides*6)
    }
  }
  function curve(points) {
    const line=new T.CurvePath()
    for(let i=1;i<points.length;i++)line.add(new T.LineCurve3(points[i-1],points[i]))
    return line
  }
  // One textured billboard per puff stays legible from above and from the side.
  const canvas=document.createElement('canvas')
  canvas.width=canvas.height=96
  const ctx=canvas.getContext('2d')
  for(const [x,y,r] of [[48,48,40],[31,48,26],[66,54,23],[46,32,27],[42,65,22]]) {
    const grad=ctx.createRadialGradient(x,y,0,x,y,r)
    grad.addColorStop(0,'rgba(60,24,87,.86)')
    grad.addColorStop(.5,'rgba(104,57,132,.66)')
    grad.addColorStop(1,'rgba(144,105,159,0)')
    ctx.fillStyle=grad;ctx.fillRect(x-r,y-r,2*r,2*r)
  }
  const texture=own(new T.CanvasTexture(canvas))
  texture.colorSpace=T.SRGBColorSpace
  const vec=(x,y,z)=>new T.Vector3(x,y,z)
  const traces=[lines[0],lines[1],lines[2],[...lines[3],...lines[4]]]
  const offsets=[[2.8,3.4],[-3.3,2.5],[2.8,3.3],[-2.7,-3.2]]
  const banks=traces.map((points,index)=>{
    const intake=vec(points[0][0],3.09,points[0][1])
    const [dx,dz]=offsets[index]
    const origin=vec(intake.x+dx,.18,intake.z+dz)
    const path=curve(points.map(([x,z])=>vec(x,3.09,z)))
    const source=ring(origin,violet),sink=ring(path.getPoint(1),orange)
    const puffs=Array.from({length:13},()=>{
      const mat=own(new T.SpriteMaterial({map:texture,transparent:true,depthTest:false,depthWrite:false,toneMapped:false}))
      const puff=new T.Sprite(mat);puff.renderOrder=45;group.add(puff);return puff
    })
    const arrows=Array.from({length:8},()=>arrow(orange))
    return {origin,intake,path,source,sink,puffs,arrows,highlight:routeHighlight(path)}
  })
  const supply=ordinary?[]:[...routes.values()].map(({path})=>{
    const end=path.at(-1),direction=end.clone().sub(path.at(-2)).normalize()
    return {path:curve([...path,end.clone().addScaledVector(direction,4)]),arrows:[arrow(blue),arrow(blue)]}
  })
  function advance(node,path,u) {
    node.visible=u>=0 && u<=1
    if(!node.visible)return
    node.position.copy(path.getPointAt(u))
    node.quaternion.setFromUnitVectors(axis,path.getTangentAt(u))
  }
  function update(seconds) {
    const lap=Math.floor(seconds/20)%2,t=seconds%20
    for(const [index,b] of banks.entries()){
      const active=Math.floor(index/2)===lap
      b.highlight(t,active)
      b.source.visible=active&&t<16
      b.source.scale.setScalar(1+.18*Math.sin(t*3))
      b.sink.visible=active&&t>=10&&t<19
      b.sink.scale.setScalar(1+.2*Math.sin(t*4))
      b.puffs.forEach((p,i)=>{
        const u=(t-i*.66)/4.4
        p.visible=active&&u>=0&&u<=1
        if(!p.visible)return
        const drift=T.MathUtils.smoothstep(u,.18,1)
        p.position.lerpVectors(b.origin,b.intake,drift)
        p.position.y=.5+2.65*Math.min(1,u*1.55)
        p.position.x+=Math.sin(i*2.4+u*6)*Math.sin(Math.PI*u)*.65
        p.position.z+=Math.cos(i*1.9+u*7)*Math.sin(Math.PI*u)*.55
        p.scale.setScalar(2.4+3.6*Math.sin(Math.PI*u))
        p.material.opacity=.90*(1-.48*u)
      })
      b.arrows.forEach((a,i)=>advance(a,b.path,active?(t-4.1-i*.98)/7.1:-1))
    }
    for(const stream of supply)stream.arrows.forEach((a,i)=>{
      const u=(t-3.8+i*1.5)/3
      advance(a,stream.path,t>=3.8&&t<18 ? u%1 : -1)
    })
    const zone=lap?'西南、东北':'西北、东南'
    if(t<4.1)return zone+(ordinary?' · 模拟尾气升起':' · 模拟烟源起烟')
    if(t<10)return ordinary?'尾气进入共用排风支管':'烟气进入支管，低位补风送入车库'
    if(t<18.5)return ordinary?'沿排风干管到达机房侧':'沿排烟干管到达东西两侧机房'
    return '本轮排出完毕，即将切换烟源位置'
  }
  scene.add(group);group.visible=false
  return {id:'floor',title:ordinary?'整层 · 车库尾气排风':'整层 · 模拟烟源排烟',group,resources,duration:40,update}
}
