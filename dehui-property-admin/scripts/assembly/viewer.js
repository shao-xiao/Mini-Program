import * as T from 'three'
import {OrbitControls} from 'three/addons/controls/OrbitControls.js'
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js'

const $=s=>document.querySelector(s)
const stage=$('#stage'), scene=new T.Scene(), camera=new T.PerspectiveCamera(36,1,.01,800)
const renderer=new T.WebGLRenderer({antialias:true})
renderer.setClearColor(0xedf3f2);renderer.setPixelRatio(Math.min(devicePixelRatio,2))
stage.prepend(renderer.domElement)
scene.add(new T.HemisphereLight(0xffffff,0xb1c6ca,2.3))
const light=new T.DirectionalLight(0xfff6e2,2.1);light.position.set(-3,7,4);scene.add(light)
const controls=new OrbitControls(camera,renderer.domElement)
controls.addEventListener('change',render)
const data=JSON.parse($('#assets').textContent),loader=new GLTFLoader(),cache=new Map()
const names={building:'整栋装配',floor:'B2 楼层',structure:'建筑底图',room:'西侧污水处理间',equipment:'隔油提升机组',pump:'提升泵组件',route:'排水支路'}
const kinds={building:'整栋与场地',floor:'楼层',structure:'建筑与结构构件',room:'房间与功能空间',equipment:'设备机组',pump:'设备与末端',route:'管线与连接件'}
let sequence=0,current,root,box,top=false,highlight,mixer,playing=false,frame,previousTime
function stopFlow(){
  cancelAnimationFrame(frame);playing=false;mixer?.stopAllAction();mixer=null
  $('#flow').textContent='播放流向'
}
function animate(now){
  if(!playing)return
  mixer.update(Math.min(.1,(now-previousTime)/1000));previousTime=now;render();frame=requestAnimationFrame(animate)
}
$('#flow').onclick=()=>{
  playing=!playing;$('#flow').textContent=playing?'暂停流向':'播放流向'
  if(playing){previousTime=performance.now();frame=requestAnimationFrame(animate)}else cancelAnimationFrame(frame)
}
function render(){renderer.render(scene,camera)}
function fit(){
  if(!box)return
  const center=box.getCenter(new T.Vector3()),radius=Math.max(.35,box.getSize(new T.Vector3()).length()/2)
  const fov=camera.fov*Math.PI/180
  const distance=1.1*radius/Math.sin(Math.min(fov/2,Math.atan(Math.tan(fov/2)*camera.aspect)))
  const direction=top?new T.Vector3(0,1,.0001):new T.Vector3(1,1.1,1.25)
  controls.target.copy(center);camera.position.copy(center).addScaledVector(direction.normalize(),distance)
  camera.near=Math.max(.008,distance/1500);camera.far=distance+radius*12;camera.updateProjectionMatrix()
  controls.update();render()
}
function describe(node){
  const {key,label,kind,...fields}=node.userData.asset
  $('#kind').textContent=kind;$('#title').textContent=label
  const dl=$('dl');dl.replaceChildren()
  const station=node.userData.module
  if(station){
    Object.assign(fields,Object.fromEntries(station.fields||[]))
    if(station.equipment){fields['房内设备']=station.equipment.name+' '+station.equipment.model;fields['图注规格']=station.equipment.performance}
  }
  if(node.userData.connections)fields['跨层接口']='YWL-2 · 相邻楼层端点待核'
  for(const [key,value] of Object.entries(fields)){
    const dt=document.createElement('dt'),dd=document.createElement('dd')
    dt.textContent=key;dd.textContent=String(value);dl.append(dt,dd)
  }
  document.querySelectorAll('nav button').forEach(btn=>btn.setAttribute('aria-pressed',String(btn.dataset.view===current)))
}
function showSelection(node,outline=false){
  if(highlight){scene.remove(highlight);highlight.dispose();highlight=null}
  box=new T.Box3().setFromObject(node)
  if(outline){highlight=new T.Box3Helper(box.clone(),0x7150a4);scene.add(highlight)}
  describe(node);render()
}
async function open(view){
  const turn=++sequence, file=view==='building'?'floor':['floor','structure'].includes(view)?view:'room'
  $('#status').textContent='正在打开模型…'
  try {
    if(!cache.has(file)){
      const bytes=Uint8Array.from(atob(data[file]),c=>c.charCodeAt(0))
      cache.set(file,loader.parseAsync(bytes.buffer,''))
    }
    const loaded=await cache.get(file);if(turn!==sequence)return
    if(root)scene.remove(root)
    stopFlow()
    if(view==='building'){
      root=new T.Group();root.userData.asset=data.building
      const copy=loaded.scene.clone(true);copy.position.y=data.elevation;root.add(copy)
    }else root=loaded.scene
    scene.add(root);root.updateMatrixWorld(true)
    const clip=loaded.animations.find(c=>c.name==='压力排水流向示意')
    $('#flow').hidden=!clip
    if(clip){mixer=new T.AnimationMixer(root);mixer.clipAction(clip).play()}
    current=view
    let match
    root.traverse(n=>{if(!match && n.userData.asset?.kind===kinds[view])match=n})
    if(!match)throw Error('找不到模块')
    showSelection(match,!['room','floor','structure'].includes(view));fit()
    $('#status').textContent=''
  }catch(error){if(turn===sequence)$('#status').textContent=error.message}
}
for(const [key,name] of Object.entries(names)){
  const btn=document.createElement('button');btn.textContent=name;btn.dataset.view=key
  btn.onclick=()=>open(key);$('nav').append(btn)
}
let down
const canvas=renderer.domElement
canvas.addEventListener('pointerdown',e=>down=[e.clientX,e.clientY])
canvas.addEventListener('pointerup',e=>{
  if(!root||!down||Math.hypot(e.clientX-down[0],e.clientY-down[1])>4)return
  const rect=canvas.getBoundingClientRect(),ray=new T.Raycaster()
  ray.params.Line.threshold=.035
  ray.setFromCamera(new T.Vector2((e.clientX-rect.left)*2/rect.width-1,1-(e.clientY-rect.top)*2/rect.height),camera)
  let picked=ray.intersectObject(root,true)[0]?.object
  while(picked&&!picked.userData.asset)picked=picked.parent
  if(picked){current=Object.keys(kinds).find(k=>kinds[k]===picked.userData.asset.kind);showSelection(picked,true)}
})
$('#iso').onclick=()=>{top=false;fit()};$('#top').onclick=()=>{top=true;fit()}
new ResizeObserver(()=>{const {width,height}=stage.getBoundingClientRect();renderer.setSize(width,height,false);camera.aspect=width/height;camera.updateProjectionMatrix();fit()}).observe(stage)
open('room')
