// Local demonstrations use the actual archive mesh. Timings and local flow
// direction describe the example only; they do not specify a site connection.
import {Box3,Vector3,Mesh,SphereGeometry,MeshBasicMaterial,BufferGeometry,Float32BufferAttribute,DoubleSide} from 'three'


function sumpCycle(root) {
  const lower=root.getObjectByName('停泵水位'),upper=root.getObjectByName('启泵水位')
  const outline=lower?.geometry?.attributes.position
  if (!outline || outline.count<4 || !upper?.geometry) return null
  const low=outline.getY(0),high=upper.geometry.attributes.position.getY(0)
  if (!(high>low)) return null
  const vertices=[]
  for (let i=0;i<4;i++) vertices.push(outline.getX(i),0,outline.getZ(i))
  const geometry=new BufferGeometry()
  geometry.setAttribute('position',new Float32BufferAttribute(vertices,3))
  geometry.setIndex([0,1,2,0,2,3])
  const material=new MeshBasicMaterial({color:0x248fa3,transparent:true,opacity:.68,side:DoubleSide,depthWrite:false})
  const surface=new Mesh(geometry,material)
  lower.parent.add(surface)
  surface.position.y=low
  return {caption:'P202 启停水位循环',update(t){
    const phase=(t%8)/8
    const filling=phase<.6
    const level=low+(high-low)*(filling?phase/.6:(1-phase)/.4)
    surface.position.y=level
    return (filling?'停泵蓄水':'启泵排水')+' · H '+level.toFixed(2)+' m'
  },dispose(){surface.removeFromParent();geometry.dispose();material.dispose()}}
}

export function componentSimulation(root,name) {
  if (/^JSK/.test(name)) return sumpCycle(root)
  const meshes=[]
  root.traverse(node=>{if(node.isMesh) meshes.push(node)})
  const bodies=meshes.filter(m=>/主管薄壁|风管围板水平板|桥架侧帮/.test(m.name))
  const spray=name==='喷淋喷头'
  const signal=meshes.filter(m=>/端口光导|指示透镜/.test(m.name))
  const ports=meshes.filter(m=>/屏蔽插口/.test(m.name))
  const target=signal.length ? signal : ports
  if (!bodies.length && !spray && !target.length) return null
  const restored=new Map(),owned=[]
  function change(mesh,edit){
    restored.set(mesh,mesh.material)
    const clone=m=>{const copy=m.clone();owned.push(copy);edit(copy);return copy}
    mesh.material=Array.isArray(mesh.material)?mesh.material.map(clone):clone(mesh.material)
  }
  let update,caption
  const markers=[]
  if (target.length) {
    caption=name==='感烟探测器'?'报警指示示意':'端口通信示意'
    target.forEach(mesh=>change(mesh,()=>{}))
    update=t=>target.forEach((mesh,i)=>{
      const on=(t*2+i*.31)%1<.55
      for (const m of [].concat(mesh.material)) {
        m.color.setHex(on ? (name==='感烟探测器'?0xfa422e:0x1bf78c) : 0x30403a)
        if(m.emissive)m.emissive.copy(m.color).multiplyScalar(on ? .45 : 0)
      }
    })
  } else {
    root.updateMatrixWorld(true)
    const bounds=new Box3()
    if (spray) bounds.setFromObject(root)
    else for (const part of bodies) bounds.expandByObject(part)
    const mid=bounds.getCenter(new Vector3()),size=bounds.getSize(new Vector3())
    const axis=['x','y','z'].reduce((a,b)=>size[a]>=size[b]?a:b)
    const length=size[axis], radial=Math.min(...['x','y','z'].filter(k=>k!==axis).map(k=>size[k]))
    const air=/风管/.test(name)
    const cable=/桥架|穿线管|弱电/.test(name)
    caption=spray?'末端放水示意':air?'风管内部气流示意':cable?'线缆传输路径示意':'管内水流示意'
    if(!spray) meshes.forEach(mesh=>change(mesh,m=>{m.transparent=true;m.opacity=.30;m.depthWrite=false}))
    const geom=new SphereGeometry(Math.max(.001,(spray?size.length()*.026:radial*.12)),10,8)
    const mat=new MeshBasicMaterial({color:air?0x14958c:cable?0xc68419:0x197dd5})
    owned.push(geom,mat)
    const count=spray?24:12
    for(let i=0;i<count;i++){
      const marker=new Mesh(geom,mat)
      root.add(marker);markers.push(marker)
    }
    const pos=new Vector3()
    update=t=>markers.forEach((marker,i)=>{
      const u=(t*.55+i/count)%1
      pos.copy(mid)
      if(spray){
        const angle=i*2.39996, spread=size.length()*u*.76
        pos.x+=Math.cos(angle)*spread;pos.z+=Math.sin(angle)*spread
        pos.y=bounds.min.y-u*u*size.length()*.95
      } else pos[axis]=bounds.min[axis]+(.07+.86*u)*length
      marker.position.copy(root.worldToLocal(pos))
    })
  }
  update(0)
  return {caption,update,dispose(){
    markers.forEach(marker=>marker.removeFromParent())
    restored.forEach((original,mesh)=>{mesh.material=original})
    owned.forEach(item=>item.dispose())
  }}
}
