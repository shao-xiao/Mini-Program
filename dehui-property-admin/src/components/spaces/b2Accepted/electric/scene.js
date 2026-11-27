import {modelAssets} from '../assets.js'
import * as T from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { createFloorLabels } from '../floorLabels.offset.js'

export function electricalScene(stage,labelLayer,pin,options) {
  const {groups,mode,onPick,onProjection,onRooms}=options
  const rooms=[]
  let selection=null,shapes=[]
  const scene=new T.Scene(),camera=new T.OrthographicCamera(-50,50,50,-50,.1,400)
  const renderer=new T.WebGLRenderer({antialias:true})
  renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));stage.prepend(renderer.domElement)
  scene.background=new T.Color(0xedf1f0)
  const controls=new OrbitControls(camera,renderer.domElement)
  controls.minZoom=.35;controls.maxZoom=55;controls.maxPolarAngle=Math.PI*.94
  scene.add(new T.HemisphereLight(0xffffff,0xa6b2bb,2.4))
  const sun=new T.DirectionalLight(0xfff4e0,2.3);sun.position.set(-28,60,34);scene.add(sun)
  const labels=createFloorLabels(labelLayer),center=new T.Vector3(),ray=new T.Raycaster()
  const disposable=new Set(),hot=[],basePick=[],roomPick=[],buildingPick=[],visuals=[],prepared=[],bound=new T.Box3()
  const service=options.service
  let roomMixer, roomRoot, routeY=1.8
  const equipmentBounds=new Map()
  function focusRoom(info){
    const bounds=equipmentBounds.get(info.roomIndex)
    if(!bounds)return
    const extent=bounds.getSize(new T.Vector3())
    pose(bounds.getCenter(new T.Vector3()),new T.Vector3(.55,.70,.82),Math.max(6,extent.length()*2.6))
  }
  const power=Boolean(service)||mode==='动力配电',em=mode==='应急照明',tone=service?(mode==='消防报警与联动'?0xe76b0b:mode==='综合弱电'||mode==='综合安防'||mode==='消防物联网'?0x008675:0x0079cf):power?0x0079cf:em?0x802bd4:0xca6a00
  let dead=false,needsFrame=true,cameraDirty=true,span=100,active=0,priorPhase=-1,down
  const own=x=>(disposable.add(x),x)
  const mat=(color,opacity=1,over=false)=>own(new T.MeshBasicMaterial({color,transparent:opacity<1,opacity,depthTest:!over,depthWrite:!over&&opacity===1}))
  const box=own(new T.BoxGeometry(1,1,1)),circle=own(new T.CircleGeometry(1,36))
  const bright=mat(tone,1,true),off=mat(0x48616c,.85,true),selectedLamp=mat(em?0x7424ce:0xffa900,1,true),halo=mat(tone,.19,true)
  const v=(p,y)=>new T.Vector3(p[0],y,p[1])
  function mesh(geo,material,pos,scale,info,order=3) {
    const m=new T.Mesh(geo,material);m.position.copy(pos);m.scale.set(...scale);m.renderOrder=order;scene.add(m)
    if(info){m.userData.info=info;hot.push(m)}return m
  }
  function segment(a,b,width,height,material,info){
    const line=mesh(box,material,a.clone().add(b).multiplyScalar(.5),[width,height,a.distanceTo(b)],info,6)
    line.rotation.y=Math.atan2(b.x-a.x,b.z-a.z);return line
  }
  function prepareGroup(g,index){
    const panel=mesh(box,off,v(g.anchor,1.55),[.7,1.05,.48],{kind:'panel',index},8)
    const lamps=g.points.map((p,pointIndex)=>{
      const body=mesh(box,off,v(p,power?1.8:3.08),power?[.6,.4,.6]:[1.2,.18,.38],{kind:g.logical?'panel':'load',index,pointIndex},8)
      const disk=mesh(circle,halo,v(p,.16),[power?.95:1.9,power?.95:1.9,1],{kind:g.logical?'panel':'load',index,pointIndex},4);disk.rotation.x=-Math.PI/2
      return {body,disk}
    })
    let path=[],length=0,dot,particles=[]
    if(service && ['消防报警与联动','自动喷淋'].includes(mode)){
      const smoke=mode==='消防报警与联动',geo=own(new T.SphereGeometry(1,10,8)),material=mat(smoke?0x836884:0x168acc,.55,true)
      for(let k=0;k<12;k++)particles.push(mesh(geo,material,v(smoke?g.anchor:g.points[0],.3),[.2,.2,.2],null,5))
    }
    if(g.path){
      for(let i=1;i<g.path.length;i++){
        const a=v(g.path[i-1],routeY),b=v(g.path[i],routeY),len=b.distanceTo(a),info={kind:'wire',index}
        const fixed=segment(a,b,.12,.1,off,info),trail=segment(a,b,.16,.15,bright,info)
        trail.renderOrder=7
        path.push({a,b,len,start:length,fixed,trail});length+=len
      }
      dot=mesh(own(new T.ConeGeometry(.23,.54,10)),mat(0xffb31a,1,true),v(g.path[0],1.8),[1,1,1],null,10)
    }
    visuals.push({panel,lamps,path,length,dot,particles,firstPanel:groups.findIndex(entry=>entry.panel===g.panel)===index})
  }
  function resize(){
    const w=stage.clientWidth,h=stage.clientHeight;if(!w||!h)return
    renderer.setSize(w,h,false);const aspect=w/h,hf=span*.5/Math.min(1,aspect)
    Object.assign(camera,{left:-hf*aspect,right:hf*aspect,top:hf,bottom:-hf})
    camera.updateProjectionMatrix();cameraDirty=needsFrame=true
  }
  function pose(c,dir,s){
    controls.target.copy(c);camera.position.copy(c).addScaledVector(dir.normalize(),140);camera.zoom=1;span=s;resize();controls.update()
  }
  function view(kind){
    const dir=kind==='top'?new T.Vector3(0,1,.00001):new T.Vector3(.44,.98,.65)
    if(kind==='top')pose(center,dir,Math.max(bound.max.x-bound.min.x,bound.max.z-bound.min.z)*1.10)
    else pose(center,dir,Math.max(bound.max.x-bound.min.x,bound.max.z-bound.min.z)*1.50)
  }
  function focus(){const g=groups[active];pose(v(g.anchor,1.6),camera.position.clone().sub(controls.target),power?13:24)}
  function zoom(k){camera.zoom=T.MathUtils.clamp(camera.zoom*k,.35,55);camera.updateProjectionMatrix();cameraDirty=needsFrame=true}
  controls.addEventListener('change',()=>{cameraDirty=needsFrame=true})
  function project(){
    camera.updateMatrixWorld()
    const w=stage.clientWidth,h=stage.clientHeight;labels.update(camera,w,h,selection?.room?{group:'rooms',index:selection.roomIndex}:null)
    onProjection(shapes.map(shape=>({type:shape.type,points:shape.points.map(([x,y,z])=>{
      const p=new T.Vector3(x,y,z).project(camera)
      return [(1+p.x)*w/2,(1-p.y)*h/2]
    })})))
    const p=v(groups[active].anchor,2.3).project(camera),x=(p.x+1)*w/2,y=(1-p.y)*h/2
    pin.style.left=x+'px';pin.style.top=y+'px';pin.style.visibility=x<0||x>w||y<0||y>h?'hidden':'visible'
  }
  function track(root){root.traverse(n=>{if(n.geometry)own(n.geometry);for(const m of [].concat(n.material||[]))own(m)})}
  async function load(){
    const bytes=Uint8Array.from(atob(service?modelAssets.serviceModels[mode]:modelAssets.electricModels[power?'B2·动力配电':'B2·室内照明与应急']),c=>c.charCodeAt(0))
    const loader=new GLTFLoader()
    const floorBuffer=service?Uint8Array.from(atob(modelAssets.serviceBuilding),c=>c.charCodeAt(0)).buffer:modelAssets.floorBytes('B2-普通通风.glb').buffer
    const [building,discipline]=await Promise.all([loader.parseAsync(floorBuffer,''),loader.parseAsync(bytes.buffer,'')])
    track(building.scene);track(discipline.scene);if(dead){disposeResources();return}
    const floor=service?building.scene.children[0]:building.scene.children[0].children[0];scene.add(floor)
    if(service){
      roomRoot=floor
      roomMixer=new T.AnimationMixer(floor)
      for(const clip of building.animations)roomMixer.clipAction(clip).play()
    }
    bound.setFromObject(floor);bound.getCenter(center)
    const walls=mat(0x91a6a8,.29),roomWall=mat(0x82a99d,.47)
    floor.children.forEach((n,index)=>{
      const room=service?modelAssets.serviceRoomSurvey[n.name]:n.userData.roomSurvey
      if(room){
        const roomInfo={kind:'room',room,roomIndex:index,asset:n.userData.asset,roomY:n.userData.module?.floorOrigin?.[1]||0}
        rooms.push(roomInfo)
        const equipped=service&&n.userData.hasEquipment
        const north=Math.min(...room.points.map(p=>p[1]))
        const anchors=equipped?room.points.map(([x,z])=>[x,north+(z-north)*.55]):room.points
        labels.add('rooms',index,room.name,anchors,()=>equipped?2.65:1.35,equipped?-14:0,()=>onPick(roomInfo))
        for(const child of n.children){
        const equipment=n.userData.hasEquipment && child.children.length>0 && !/^mesh_/.test(child.name)
        if(equipment && service){
          const extents=equipmentBounds.get(index)||new T.Box3()
          extents.union(new T.Box3().setFromObject(child))
          equipmentBounds.set(index,extents);roomInfo.hasEquipment=true
        }
        child.traverse(m=>{
          if(!m.isMesh)return
          m.userData.info=equipment?{...roomInfo,kind:'roomEquipment'}:roomInfo;roomPick.push(m)
        })
        if(!child.children.length || /^mesh_/.test(child.name))child.traverse(m=>{if(m.isMesh)m.material=roomWall})
      }}else n.traverse(m=>{if(m.isMesh){m.material=walls;m.renderOrder=-2;m.userData.info={kind:'building',asset:n.userData.asset};buildingPick.push(m)}})
    })
    onRooms(rooms);labels.prepare()
    const shell=own(new T.MeshStandardMaterial({color:0x687a81,roughness:.86})),equipment=own(new T.MeshStandardMaterial({color:0x536c74,roughness:.8}))
    const sheet=groups[0].sheets[0]
    discipline.scene.traverse(n=>{
      const data=n.userData
      if(n.name.includes('路径示意'))n.visible=false
      if(!data.sourceLayer)return
      n.visible=data.source===sheet || (mode==='消防报警与联动'&&data.source==='E701')
      if(!n.visible)return
      n.traverse(m=>{if(!m.isMesh||m.name.includes('路径示意'))return
        if(!service)m.material=data.sourceLayer.startsWith('EQUIP')?equipment:shell
        m.userData.info={kind:'layer',sheet:data.source,layer:data.sourceLayer};basePick.push(m)
      })
    })
    scene.add(discipline.scene)
    for(const o of (service?modelAssets.serviceData:modelAssets.electricData).survey.filter(o=>o.drawings===sheet)){
      const coords=o.planLine||o.placement?.endpointsXZ
      if(!coords || coords.length!==2)continue
      const y=o.origin?.[1]??2.9,width=o.placement?.nominalSizeMm?.[0]/1000||.05
      const m=segment(v(coords[0],y),v(coords[1],y),width,o.placement?.nominalSizeMm?.[1]/1000||.05,shell,{kind:'survey',survey:o})
      if(service){m.material=own(new T.MeshBasicMaterial({colorWrite:false,depthWrite:false}));m.renderOrder=-4}
      prepared.push(m)
    }
    if(service){
      // Place the illustrative tracer at the installed model's route level.
      const routed=basePick.find(m=>/管段|桥架|CABLETRAY|LWIRE|布线-L/.test(m.userData.info.layer))
      if(routed){const bounds=new T.Box3().setFromObject(routed);routeY=(bounds.max.y+bounds.min.y)/2}
    }
    groups.forEach(prepareGroup);view(service?'iso':'top')
  }
  function render(index,t,dt){
    if(dead||!visuals.length||!stage.clientWidth)return
    if(roomMixer && (dt>0 || t!==priorPhase))roomMixer.setTime(t)
    const changed=index!==active
    active=index
    if(changed)cameraDirty=true
    const q=T.MathUtils.clamp((t-1.4)/4.3,0,1),lit=t>=(power?5.7:em?4.5:2.2)
    for(const [i,s] of visuals.entries()){
      const current=i===index
      s.panel.visible=current || (s.firstPanel && groups[i].panel!==groups[index].panel)
      if(service && groups[i].logical)s.panel.visible=false
      s.panel.material=current?bright:off
      for(const l of s.lamps){
        l.body.material=current&&lit?selectedLamp:off;l.disk.visible=current&&lit
      }
      s.particles.forEach((particle,k)=>{
        const smoke=mode==='消防报警与联动',f=(t*.6+k/12)%1,a=k*2.4
        const source=smoke?groups[i].anchor:groups[i].points[0]
        particle.visible=current&&(smoke?t<3:t>5.7)
        const radius=smoke?.38+.4*f:.9*f
        particle.position.set(source[0]+radius*Math.cos(a),smoke?.15+f*1.9:1.75-f*1.6,source[1]+radius*Math.sin(a))
        const scale=smoke?.16+.4*f:.08;particle.scale.setScalar(scale)
      })
      s.dot&&(s.dot.visible=current&&q>0&&q<1)
      for(const p of s.path){
        const portion=T.MathUtils.clamp((s.length*q-p.start)/p.len,0,1)
        p.fixed.visible=!service;p.trail.visible=current&&portion>0
        if(p.trail.visible){
          p.trail.scale.z=portion*p.len;p.trail.position.lerpVectors(p.a,p.b,portion/2)
          if(portion<1){s.dot.position.lerpVectors(p.a,p.b,portion);s.dot.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),p.b.clone().sub(p.a).normalize())}
        }
      }
    }
    if(cameraDirty){project();cameraDirty=false;needsFrame=true}
    if(needsFrame||dt>0||changed||priorPhase!==t){renderer.render(scene,camera);needsFrame=false}
    priorPhase=t
  }
  function hitModel(setRay,offsets,allCircuits=false,sourceSheet=null){
    scene.updateMatrixWorld()
    const items=hot.filter(m=>m.visible || (allCircuits && ['panel','load'].includes(m.userData.info.kind) && m.geometry===box))
    const sameSheet=!sourceSheet||sourceSheet===groups[0].sheets[0]
    const baseTargets=sourceSheet?basePick.filter(m=>m.userData.info.sheet===sourceSheet):basePick
    const nearest=list=>ray.intersectObjects(list,false)[0]
    let roomHit,buildingHit
    for(const [dx,dy] of offsets){
      setRay(dx,dy)
      const hits=ray.intersectObjects(sameSheet?items:[],false)
      const hit=hits.find(h=>h.object.userData.info.kind!=='survey')||hits[0]||nearest(baseTargets)
      if(hit)return hit
      if(!dx&&!dy){roomHit=nearest(roomPick);buildingHit=nearest(buildingPick)}
    }
    return roomHit||buildingHit
  }
  // The same physical-pixel tolerance applies in the rotatable scene and in the registered drawing.
  const offsets=[[0,0]]
  for (const distance of [1,2.5,4,6])for(let i=0;i<8;i++){
    const a=i*Math.PI/4;offsets.push([distance*Math.cos(a),distance*Math.sin(a)])
  }
  function select(info){
    selection=info;shapes=[]
    const add=(type,points,y)=>shapes.push({type,points:points.map(([x,z])=>[x,y,z])})
    if(info){
      const g=groups[info.index]
      if(info.kind==='room')add(service&&info.hasEquipment?'line':'area',service&&info.hasEquipment?[...info.room.points,info.room.points[0]]:info.room.points,info.roomY+.10)
      else if(info.kind==='wire' && g.path)add('line',g.path,routeY)
      else if(info.kind==='panel')add('point',[g.anchor],1.85)
      else if((info.kind==='load'||info.kind==='wire')&&!g.logical){
        const points=info.pointIndex==null?g.points:[g.points[info.pointIndex]]
        points.forEach(p=>add('point',[p],power?1.8:3.08))
      }else if(info.kind==='survey'){
        const o=info.survey;add('line',o.planLine||o.placement.endpointsXZ,o.origin?.[1]??2.9)
      }else if(info.position)add('point',[info.position],info.hitY)
    }
    cameraDirty=needsFrame=true
    return shapes
  }
  function inspectHit(hit){
    if(hit)onPick({...hit.object.userData.info,position:[hit.point.x,hit.point.z],hitY:hit.point.y})
    return Boolean(hit)
  }
  function pickPlan(x,z,metresPerPixel,sourceSheet=null){
    const hit=hitModel((dx,dy)=>ray.set(new T.Vector3(x+dx*metresPerPixel,200,z+dy*metresPerPixel),new T.Vector3(0,-1,0)),offsets,true,sourceSheet)
    return inspectHit(hit)
  }
  const canvas=renderer.domElement
  canvas.onpointerdown=e=>{
    down=e.isPrimary && e.button===0?{x:e.clientX,y:e.clientY,id:e.pointerId}:null
  }
  canvas.onpointermove=e=>{
    if(down?.id===e.pointerId && Math.hypot(e.clientX-down.x,e.clientY-down.y)>6)down=null
  }
  canvas.onpointercancel=()=>{down=null}
  canvas.onpointerup=e=>{
    const press=down;down=null
    if(!press||press.id!==e.pointerId||e.button!==0||Math.hypot(e.clientX-press.x,e.clientY-press.y)>6)return
    const r=canvas.getBoundingClientRect()
    const hit=hitModel((dx,dy)=>ray.setFromCamera({x:(e.clientX+dx-r.left)/r.width*2-1,y:1-(e.clientY+dy-r.top)/r.height*2},camera),offsets)
    inspectHit(hit)
  }
  const observer=new ResizeObserver(resize);observer.observe(stage)
  function disposeResources(){disposable.forEach(x=>x.dispose());disposable.clear()}
  function dispose(){roomMixer?.stopAllAction();if(roomRoot)roomMixer.uncacheRoot(roomRoot);dead=true;observer.disconnect();controls.dispose();disposeResources();renderer.dispose();renderer.domElement.remove()}
  return {load,render,view,focus,focusRoom,zoom,pickPlan,select,dispose}
}
