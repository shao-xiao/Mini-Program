<template>
  <div class="installation" :class="{opened:detail}">
    <div ref="stage" class="stage">
      <div ref="labelLayer" class="room-labels" aria-label="房间名称"></div>
      <div class="bar"><button :aria-pressed="viewMode==='iso'" @click="angle('iso')">轴测</button><button :aria-pressed="viewMode==='top'" @click="angle('top')">俯视</button><button aria-label="放大" @click="zoom(1.35)">＋</button><button aria-label="缩小" @click="zoom(1/1.35)">−</button><label v-if="floorTitle">循环演示 <select aria-label="循环演示" v-model="demoId" @change="chooseDemo"><option value="">整层浏览</option><option value="floor">{{floorTitle}}</option></select></label><button @click="toggleMotion">{{playing?'暂停演示':'播放演示'}}</button><button v-if="demoId" @click="chooseDemo">从头播放</button></div>
      <div v-if="demoId==='floor'" class="legend" aria-label="整层演示图例"><span><i class="duct"></i>风管</span><span><i class="plume"></i>{{name==='普通通风'?'模拟尾气':'模拟烟团'}}</span><span><i class="extract"></i>{{name==='普通通风'?'排风':'排烟'}}</span><span v-if="name!=='普通通风'"><i class="fresh"></i>补风</span><small>原理示意</small></div>
      <p v-if="demoId" class="demo-state"><b>{{playing?'循环中':'已暂停'}}</b> · {{phaseText}}</p>
      <p v-if="message" class="hint">{{message}}</p>
      <p v-else class="hint">拖动旋转 · Shift＋拖动平移 · 滚轮缩放 · 点选查看参数</p>
    </div>
    <aside v-if="detail" aria-label="所选对象详情"><header><b>{{detail.title}}</b><button aria-label="关闭参数" @click="clear">×</button></header>
      <dl><template v-for="[key,value] in detail.rows" :key="key"><dt>{{key}}</dt><dd>{{value}}</dd></template></dl>
      <button @click="focusSelected">{{chosen?.userData.roomSurvey?'定位房间':'定位构件'}}</button> <button v-if="detail.route" @click="follow">{{following?'停止沿管查看':'沿管查看'}}</button>
      <p v-if="detail.note">{{detail.note}}</p><a v-for="s in detail.sources" :href="sheets[s].file" target="_blank" :key="s">核对 {{s}} 原图　</a>
    </aside>
  </div>
</template>
<script setup>
import {modelAssets,glb} from './assets.js'

import {onMounted,onBeforeUnmount,ref,nextTick} from 'vue'
import * as T from 'three'
import {OrbitControls} from 'three/addons/controls/OrbitControls.js'
import {createFloorCycle} from './floorCycle.js'
import {createFloorLabels} from './floorLabels.offset.js'
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js'
const props=defineProps({name:String}),stage=ref(null),detail=ref(null),message=ref('正在装配吊装风管…'),playing=ref(false),following=ref(false)
const labelLayer=ref(null),viewMode=ref('iso'),roomNodes=[],roomMeshes=[],roomOwners=new Map()
const floorTitle=ref(''),demoId=ref(''),phaseText=ref('')
let floorCycle,activeDemo,elapsed=0
const bakedMarkers=[]
function chooseDemo(){
 stopFollow()
 if(activeDemo)activeDemo.group.visible=false
 activeDemo=demoId.value==='floor'?floorCycle:null
 for(const [marker,visible] of bakedMarkers)marker.visible=!activeDemo&&visible
 for(const {dots} of flowPaths)for(const dot of dots)dot.visible=false
 elapsed=0
 if(activeDemo)roomMixer?.setTime(0)
 if(activeDemo){
  activeDemo.group.visible=true
  phaseText.value=activeDemo.update(0)
  angle('top')
  playing.value=true
 }else{angle('iso')}
 dirty=true
}
let labels,labelsDirty=true
const sheets=modelAssets.floorMeta.sources,scene=new T.Scene(),ray=new T.Raycaster(),center=new T.Vector3(),box=new T.Box3()
let roomMixer,renderer,camera,controls,mixer,observer,frame,last=0,disposed=false,root,chosen,nearIndex=-1,trace,time=0,progress=0,dirty=true
const equipmentMeshes=new Set(), ductMaterials=new Set()
function revealRoom(n){
 const survey=n?.userData.roomSurvey
 let planes=null
 if(survey && n.userData.hasEquipment){
  const xs=survey.points.map(p=>p[0]),zs=survey.points.map(p=>p[1])
  // Cut only the overhead services within the room when inspecting its equipment.
  planes=[new T.Plane(v(-1,0,0),Math.min(...xs)),new T.Plane(v(1,0,0),-Math.max(...xs)),
          new T.Plane(v(0,0,-1),Math.min(...zs)),new T.Plane(v(0,0,1),-Math.max(...zs))]
 }
 for(const mat of ductMaterials){mat.clippingPlanes=planes;mat.clipIntersection=true}
 dirty=true
}
const flowPaths=[];const routes=new Map(),targets=[],pickables=new Map(),saved=new Map(),owned=new Set()
const v=(x,y,z)=>new T.Vector3(x,y,z)
const label=n=>n.userData.name||n.name
function mark(n,owner){n.traverse(m=>{if(m.isMesh){targets.push(m);pickables.set(m,owner)}})}
function restore(){for(const [m,mat] of saved){m.material.dispose();m.material=mat}saved.clear();dirty=true}
function clear(){stopFollow();restore();revealRoom(null);chosen=null;detail.value=null;labelsDirty=true}
function stopFollow(){following.value=false;trace?.group.removeFromParent();trace=null;dirty=true}
function colorize(group,cut=false){restore();group?.traverse(m=>{if(!m.isMesh||equipmentMeshes.has(m)||Array.isArray(m.material))return;saved.set(m,m.material);m.material=m.material.clone();m.material.color?.set(0x087e87);if(cut){m.material.transparent=true;m.material.opacity=.31;m.material.depthWrite=false}});dirty=true}
function roomAt(x,z){
 return roomNodes.find(n=>{const pts=n.userData.roomSurvey.points;let inside=false;for(let i=0,j=pts.length-1;i<pts.length;j=i++){const [a,b]=pts[i],[c,d]=pts[j];if((b>z)!==(d>z)&&x<(c-a)*(z-b)/(d-b)+a)inside=!inside}return inside})
}
function context(n,point){
 const room=n.userData.roomSurvey
 if(room)return {title:room.name,rows:[['所在楼层','B2 地下二层'],...room.fields],sources:[room.source]}

 const data=n.userData,term=data.module,route=data.routeId,original=label(n)
 let layer=n;while(layer.parent&&!/风管.*M(207|306)/.test(label(layer)))layer=layer.parent
 const sys=label(layer).split(' · ')[0],source=data.source||label(layer).match(/M(?:207|306)/)?.[0]||(props.name==='普通通风'?'M306':'M207')
 const rows=[];let title=original,note='',sources=[source]
 if(term?.id?.includes('AHD')){
  title=original;rows.push(['构件编号',term.id],['开口宽 × 高','1600 × 1250 mm'],['底边 / 顶边距地','0.300 / 1.550 m'],['图注风量','25,000 m³/h']);sources=['M306','M207']
 }else if(data.role==='support'){
 rows.push(['连接对象','顶部安装面、吊杆、横担与补风管'],['安装依据','吊装构造示意，锚固点及板底待现场核对']);sources=['M207','M306']
 }else if(data.role==='drop'){
  rows.push(['连接对象',modelAssets.floorMeta.terminals.find(t=>t.id===route)?.name],['上接',sys==='补风风管'?sys+' · 管段 '+data.trunk:'补风风管 · 管段 '+data.trunk],['风口底边距地','0.300 m（图注）'],['转接形式','末端下翻、低位侧出（示意）']);note=data.basis;sources=['M306','M207']
 }else if(data.heightBasis){
  const body=n.children.find(x=>label(x).includes('管身'))||n;const b=new T.Box3().setFromObject(body)
  rows.push(['所属支路',sys],['图面截面依据',String(data.dimensionEvidence||'待核').replace(/^｜/,'')],['模型截面高度',data.height.toFixed(3)+' m'],['模型底 / 顶距地',b.min.y.toFixed(3)+' / '+b.max.y.toFixed(3)+' m'])
  note='平面位置取自竣工图。模型安装高度为示意，实际吊装标高待核。'
 }else{
  rows.push(['所属支路',sys],['构件类型',original.replace(/\s*\d+$/,'')]);note='按本处图符定位，设备编号及实物规格待核。'
 }
 if(point){const room=roomAt(point.x,point.z);if(room)rows.unshift(['平面对应空间',room.userData.roomSurvey.name])}
 return {title,rows,sources,note,route}
}
function select(n,point){stopFollow();revealRoom(null);chosen=n;colorize(n);labelsDirty=true;detail.value=context(n,point)}
function pose(target,dir,span){controls.target.copy(target);camera.position.copy(target).add(dir.clone().normalize().multiplyScalar(145));camera.up.set(0,1,0);const a=stage.value.clientWidth/stage.value.clientHeight;camera.zoom=1;camera.userData.span=span;const half=span/2/Math.min(1,a);Object.assign(camera,{left:-half*a,right:half*a,top:half,bottom:-half});camera.updateProjectionMatrix();controls.update();labelsDirty=true;dirty=true}
function presetDirection(kind){return kind==='top'?v(0,1,.0001):v(.68,.55,1)}
function direction(){return camera.position.clone().sub(controls.target).normalize()}
function cameraChanged(){
 const dir=direction()
 viewMode.value=['iso','top'].find(kind=>dir.angleTo(presetDirection(kind))<.002)||'orbit'
 dirty=true;labelsDirty=true
}
function angle(kind){if(!root)return;stopFollow();revealRoom(null);viewMode.value=kind;pose(center,presetDirection(kind),kind==='top'?88:77)}
function zoom(factor){if(!camera)return;camera.zoom=T.MathUtils.clamp(camera.zoom*factor,.4,50);camera.updateProjectionMatrix();dirty=true;labelsDirty=true}
function focusSelected(){if(!chosen)return;stopFollow();revealRoom(chosen);const b=new T.Box3().setFromObject(chosen);pose(b.getCenter(new T.Vector3()),direction(),Math.max(4,Math.min(55,b.getSize(new T.Vector3()).length()*1.15)))}
function follow(){if(activeDemo){demoId.value='';chooseDemo()}if(following.value){stopFollow();colorize(chosen);return}const item=routes.get(detail.value?.route);if(!item)return;const group=new T.Group();const pts=item.path;const path=new T.CurvePath()
 for(let i=1;i<pts.length;i++)path.add(new T.LineCurve3(pts[i-1],pts[i]))
 const dots=[];for(let i=0;i<6;i++){const sphere=new T.Mesh(markerGeo,markerMat);sphere.renderOrder=10;group.add(sphere);dots.push(sphere)}
 scene.add(group);trace={group,path,dots,item};progress=0;following.value=true;colorize(chosen,true);pose(pts[0],direction(),6.2)}
function toggleMotion(){playing.value=!playing.value;dirty=true}
function resize(){if(!renderer||!stage.value)return;let w=stage.value.clientWidth,h=stage.value.clientHeight;if(!w||!h)return;renderer.setSize(w,h,false);const a=w/h,half=(camera.userData.span||102)/2/Math.min(1,a)/1;Object.assign(camera,{left:-half*a,right:half*a,top:half,bottom:-half});camera.updateProjectionMatrix();labelsDirty=true;dirty=true}
const markerGeo=new T.SphereGeometry(.085,12,8),markerMat=new T.MeshBasicMaterial({color:0xffa72f,depthTest:false,depthWrite:false})
function tick(now){if(disposed)return;frame=requestAnimationFrame(tick);const dt=Math.min(.1,(now-last)/1000);last=now;if(playing.value)roomMixer?.update(dt);if(playing.value&&activeDemo){
 elapsed=(elapsed+dt)%activeDemo.duration
 phaseText.value=activeDemo.update(elapsed)
 dirty=true
 }else if(playing.value){time+=dt;mixer?.update(dt);for(const {curve,dots} of flowPaths)dots.forEach((dot,i)=>{dot.visible=!following.value;dot.position.copy(curve.getPointAt((time*.17+i*.07)%1))});dirty=true}
 if(following.value&&trace){progress=Math.min(1,progress+dt/7);const {path,dots,item}=trace;const p=path.getPointAt(progress);const delta=p.clone().sub(controls.target);controls.target.add(delta);camera.position.add(delta);controls.update();dots.forEach((dot,i)=>dot.position.copy(path.getPointAt(Math.max(0,progress-i*.045))));dirty=true;if(progress>=1){stopFollow();colorize(chosen)}}
 if(labelsDirty&&labels){camera.updateMatrixWorld();const index=roomNodes.indexOf(chosen);labels.update(camera,stage.value.clientWidth,stage.value.clientHeight,index<0?null:{group:'rooms',index});labelsDirty=false}
 if(dirty){renderer.render(scene,camera);dirty=false}}
let down
function pick(e){if(e.button!==0||!down||Math.hypot(e.clientX-down.x,e.clientY-down.y)>5)return;const roomHit=labels?.hit(e.clientX,e.clientY);if(roomHit){select(roomNodes[roomHit.index]);return}const r=renderer.domElement.getBoundingClientRect();ray.setFromCamera({x:2*(e.clientX-r.left)/r.width-1,y:1-2*(e.clientY-r.top)/r.height},camera);const hit=ray.intersectObjects(targets,false)[0];if(hit)select(pickables.get(hit.object),hit.point);else {const rh=ray.intersectObjects(roomMeshes,false)[0];if(rh)select(roomOwners.get(rh.object));else clear()}}
onMounted(async()=>{
 try{renderer=new T.WebGLRenderer({antialias:true});renderer.localClippingEnabled=true;renderer.setPixelRatio(Math.min(devicePixelRatio,1.7));stage.value.prepend(renderer.domElement);scene.background=new T.Color(0xf1f4f4);scene.add(new T.HemisphereLight(0xffffff,0xc7d2d1,2.25));const sun=new T.DirectionalLight(0xfff6e6,2.4);sun.position.set(30,55,22);scene.add(sun);camera=new T.OrthographicCamera(-60,60,60,-60,.05,400);controls=new OrbitControls(camera,renderer.domElement);controls.minZoom=.4;controls.maxZoom=50;controls.enableRotate=true;controls.mouseButtons.LEFT=T.MOUSE.ROTATE;controls.mouseButtons.RIGHT=T.MOUSE.PAN;controls.touches.ONE=T.TOUCH.ROTATE;controls.touches.TWO=T.TOUCH.DOLLY_PAN;controls.addEventListener('change',cameraChanged);renderer.domElement.onpointerdown=e=>down={x:e.clientX,y:e.clientY};renderer.domElement.onpointerup=pick
 const gltf=await new GLTFLoader().parseAsync((await glb('B2-'+props.name+'.glb')).buffer,'');gltf.scene.traverse(n=>{if(n.geometry)owned.add(n.geometry);if(n.material)for(const m of [].concat(n.material))owned.add(m)});if(disposed){owned.forEach(x=>x.dispose());return}root=gltf.scene.children[0];scene.add(root);root.updateMatrixWorld(true);box.setFromObject(root);box.getCenter(center)
 const base=root.children[0]
 const roomRoots=base.children.filter(n=>n.userData.roomSurvey)
 const roomSet=new Set(roomRoots)
 // The room modules store the slab and walls as direct primitives, and machinery as subassemblies.
 // Preserve the original material objects on those assemblies, including blades and indicator lamps.
 const tint=(m,color,opacity)=>{
  if(!m.isMesh||!m.material.color)return
  const mat=m.material.clone()
  owned.add(mat);m.material=mat
  mat.transparent=true;mat.opacity=opacity;mat.depthWrite=false;mat.color.set(color);m.renderOrder=-2
 }
 for(const n of base.children)if(!roomSet.has(n))n.traverse(m=>tint(m,0xa4b2b4,.19))
 labels=createFloorLabels(labelLayer.value)
 for(const n of roomRoots){
  const room=n.userData.roomSurvey
  const hasEquipment=n.userData.hasEquipment
  const north=Math.min(...room.points.map(p=>p[1]))
  const labelPoints=hasEquipment?room.points.map(([x,z])=>[x,north+(z-north)*.55]):room.points
  labels.add('rooms',roomNodes.length,room.name,labelPoints,()=>hasEquipment?2.65:1.25,hasEquipment?-12:0)
  roomNodes.push(n)
  for(const part of n.children){
   const assembly=hasEquipment && part.children.length>0 && !/^mesh_/.test(label(part))
   part.traverse(m=>{
    if(!m.isMesh)return
    roomMeshes.push(m);roomOwners.set(m,n)
    if(assembly){equipmentMeshes.add(m);return}
    tint(m,room.name.includes('电梯')?0xb5cbd7:0x91b6a5,.52)
   })
  }
 }
 labels.prepare()
 // Keep architectural materials independent from the darker duct finish.
 const finishes=new Map()
 root.traverse(n=>{
  if(!n.userData.heightBasis)return
  n.traverse(part=>{
   if(!part.isMesh||!/(管身|法兰)/.test(label(part))||!part.material.color)return
   const key=part.material
   if(!finishes.has(key)){const mat=key.clone();mat.color.set(0x324854);mat.metalness=.18;mat.roughness=.78;finishes.set(key,mat);owned.add(mat)}
   part.material=finishes.get(key)
  })
 })
 for(const group of root.children.slice(1)){group.traverse(n=>{if(n.isMesh)for(const m of [].concat(n.material))ductMaterials.add(m);const data=n.userData
 if(data.role==='drop'){const path=data.path.map(x=>new T.Vector3(...x)),dir=path.at(-1).clone().sub(path.at(-2)).normalize();const direction=dir.clone().multiplyScalar(1.25).add(v(dir.z*.85,.38,-dir.x*.85));routes.set(data.routeId,{path,mid:path[2].clone().lerp(path[1],.58),direction});mark(n,n)}
 else if(data.role==='support'||data.heightBasis||data.module?.id?.includes('AHD')||/^(风口|阀件|风机)\s*\d+$/.test(label(n)))mark(n,n)
 })}
 for(const item of routes.values()){const curve=new T.CurvePath();for(let i=1;i<item.path.length;i++)curve.add(new T.LineCurve3(item.path[i-1],item.path[i]));const dots=[];for(let i=0;i<3;i++){const dot=new T.Mesh(markerGeo,markerMat);dot.renderOrder=9;dot.visible=false;scene.add(dot);dots.push(dot)}flowPaths.push({curve,dots})}
 root.traverse(n=>{if(n.name.startsWith('路径演示'))bakedMarkers.push([n,n.visible])})
 floorCycle=createFloorCycle(scene,routes,props.name==='普通通风')
 floorTitle.value=floorCycle.title
 roomMixer=new T.AnimationMixer(root)
 mixer=new T.AnimationMixer(root)
 for(const clip of gltf.animations){
  const target=clip.name.startsWith('机房 · ')&&clip.name.endsWith('设备循环示意')?roomMixer:mixer
  target.clipAction(clip).play()
 }
 demoId.value='floor';chooseDemo();playing.value=!matchMedia('(prefers-reduced-motion: reduce)').matches;message.value='';observer=new ResizeObserver(resize);observer.observe(stage.value);resize();frame=requestAnimationFrame(tick)
 }catch(e){message.value='暖通实体加载失败：'+e.message}})
onBeforeUnmount(()=>{disposed=true;cancelAnimationFrame(frame);restore();stopFollow();if(floorCycle){floorCycle.group.removeFromParent();floorCycle.resources.forEach(r=>r.dispose())}observer?.disconnect();controls?.dispose();mixer?.stopAllAction();roomMixer?.stopAllAction();owned.forEach(x=>x.dispose());markerGeo.dispose();markerMat.dispose();renderer?.dispose()})
</script>
<style scoped>
.installation{display:grid;grid-template-columns:minmax(0,1fr);gap:12px}.installation.opened{grid-template-columns:minmax(0,1fr) 232px}.stage{height:clamp(430px,74vh,960px);position:relative;overflow:hidden;border-radius:5px;background:#f1f4f4}.stage :deep(canvas){width:100%;height:100%;display:block;touch-action:none}.bar,.hint{position:absolute;left:10px;background:#fffffff2;padding:4px 6px;border:1px solid #d5dde0;border-radius:5px}.bar{top:10px;display:flex;flex-wrap:wrap;gap:2px}.hint{bottom:7px;margin:0;color:#4a6266;font-size:12px}button{cursor:pointer;border:1px solid #d9e0e2;background:white;border-radius:4px;padding:5px 9px;color:#203438}.bar button{border:0}button:hover{background:#e6efef}aside{border-left:1px solid #d8dfe2;padding:8px 14px;font-size:13px}header{display:flex;justify-content:space-between;align-items:center}dt,p{color:#596d77}dd{margin:3px 0 11px;border-bottom:1px solid #e5e9ea;padding-bottom:7px;color:#155f62}a{color:#346b73}@media(max-width:800px){.installation.opened{grid-template-columns:1fr}.stage{height:56vh;min-height:340px}aside{border-left:0;border-top:1px solid #d8dfe2}}
.room-labels{position:absolute;inset:0;pointer-events:none;overflow:hidden}.room-labels :deep(.plan-label){position:absolute;left:0;top:0;white-space:nowrap;font:500 11px/15px system-ui;color:#203b44;background:#f8fbfade;border-radius:2px;padding:0 2px}.room-labels :deep(.plan-label.active){color:white;background:#116c72}.bar button[aria-pressed=true]{background:#e6efef}.bar,.hint{z-index:2}.bar{max-width:calc(100% - 20px)}.bar label{display:flex;align-items:center;gap:5px;border-left:1px solid #d5dde0;padding:0 5px;font-size:12px;color:#425c61}.bar select{font:inherit;color:#203438;border:1px solid #cad6d6;border-radius:3px;background:white;padding:3px}.demo-state{position:absolute;right:10px;bottom:7px;margin:0;padding:4px 7px;background:#fffffff2;color:#27585f;border-radius:4px;font-size:12px;pointer-events:none}.demo-state b{font-weight:600}@media(max-width:850px){.demo-state{bottom:38px}}
.legend{position:absolute;left:10px;bottom:42px;display:flex;align-items:center;gap:10px;flex-wrap:wrap;background:#fffffff2;border:1px solid #cbd5d5;border-radius:5px;padding:5px 8px;color:#31414b;font-size:12px;pointer-events:none}.legend span{display:inline-flex;align-items:center;gap:4px}.legend i{display:inline-block;width:14px;height:6px;background:#415763}.legend .plume{width:12px;height:12px;border-radius:50%;background:radial-gradient(#552170,#ba94c8 70%,transparent)}.legend .extract,.legend .fresh{width:14px;height:10px;background:#ff5900;clip-path:polygon(0 25%,55% 25%,55% 0,100% 50%,55% 100%,55% 75%,0 75%)}.legend .fresh{background:#006af5}.legend small{font-size:11px;color:#60707a}@media(max-width:850px){.legend{bottom:66px}.demo-state{max-width:calc(100% - 20px)}}
</style>
