import * as T from 'three'
import {OrbitControls} from 'three/addons/controls/OrbitControls.js'
import {createPlaza} from './plaza'
import {loadModel} from './model'
import {daylight, instanceTrees} from './daylight'

const lerp = T.MathUtils.lerp

const streetView = new T.Vector3(.32,.23,1).normalize()
const overviewDirection = new T.Vector3(.23,.155,1).normalize()
const hint = '悬停楼层查看平面 · 滚轮或拖动旋转'
function lights(scene) {
  scene.add(new T.HemisphereLight(0xf0f5f9,0x929d97,1.35))
  const sun=new T.DirectionalLight(0xfffaf3,1.55)
  sun.position.set(-3,6,4);scene.add(sun)
}
function release(object) {
  const geometries=new Set(),materials=new Set(),textures=new Set()
  object.traverse(node=>{
    if(node.geometry)geometries.add(node.geometry)
    for(const mat of [].concat(node.material||[]))materials.add(mat)
    if(node.isInstancedMesh)node.dispose()
  })
  for(const mat of materials){for(const value of Object.values(mat))if(value?.isTexture)textures.add(value);mat.dispose()}
  geometries.forEach(g=>g.dispose());textures.forEach(t=>t.dispose())
}
function frame(camera, center, direction, width, height, aspect) {
  const span=Math.max(height,width/aspect)*.5
  camera.left=-span*aspect;camera.right=span*aspect;camera.top=span;camera.bottom=-span
  camera.position.copy(center).addScaledVector(direction,320)
  camera.lookAt(center);camera.updateProjectionMatrix();camera.updateMatrixWorld()
}

export function createScene(panel, signal, message, select) {
  const global=panel.dataset.global==='true'
  const stage=panel.querySelector('.tower-stage'),caption=panel.querySelector('.tower-plan-label')
  const tag=panel.querySelector('header span')
  const renderer=new T.WebGLRenderer({antialias:true,alpha:false,powerPreference:'low-power'})
  renderer.setPixelRatio(Math.min(devicePixelRatio,1.6))
  renderer.toneMapping=T.ACESFilmicToneMapping;renderer.toneMappingExposure=.97
  stage.replaceChildren(renderer.domElement)
  const world=new T.Scene(),plan=new T.Scene(),cam=new T.OrthographicCamera(-1,1,1,-1,.1,900),top=cam.clone()
  world.background=new T.Color(0xeff4f4);plan.background=new T.Color(0xf6f8f7)
  const sky=daylight(renderer);world.environment=sky.texture;lights(world);lights(plan)
  const center=new T.Vector3(),size=new T.Vector3(),planCenter=new T.Vector3(),planSize=new T.Vector3()
  const ray=new T.Raycaster(),pointer=new T.Vector2(),parts=[],cached=new Map()
  const controls=global?new OrbitControls(cam,renderer.domElement):null
  controls?.addEventListener('change',queue)
  let lastAspect=0, plaza, visible=true
  const framingPoint=new T.Vector3()
  const reduced=matchMedia('(prefers-reduced-motion: reduce)')
  const local=new AbortController()
  let desired=null,shown=null,body=null,activePlan=null,epoch=0,planRequest,timer,raf=0,last=0,amount=0,dead=false
  let w=0,h=0,turn=0,tilt=.001,split=0,velocity=0
  const wash=new T.Scene(),screenCam=new T.OrthographicCamera(-1,1,1,-1,0,2)
  screenCam.position.z=1
  const shade=new T.MeshBasicMaterial({color:0xf4f6f5,transparent:true,depthTest:false,depthWrite:false})
  wash.add(new T.Mesh(new T.PlaneGeometry(2,2),shade))
  plan.background=null
  const labels=document.createElement('div');labels.className='dhic-room-names';stage.append(labels)
  let pins=[]
  function orbit(dx,dy){
    if(!desired)return
    turn+=dx;tilt=T.MathUtils.clamp(tilt+dy,.001,1.32)
    panel.dataset.bearing=turn.toFixed(3);queue()
  }
  const abort=()=>local.abort();signal.addEventListener('abort',abort,{once:true})

  function queue(){if(!dead&&!raf)raf=requestAnimationFrame(paint)}
  function renderPart(scene,camera,x,y,width,height){
    renderer.setViewport(x,y,width,height);renderer.setScissor(x,y,width,height);renderer.render(scene,camera)
  }
  function paint(now) {
    raf=0
    if(!w||!h||!body||dead)return
    const dt=Math.min(.075,Math.max(.008,(now-last)/1000))
    last=now
    const target=desired?1:0
    // An underdamped spring separates the neighbouring floors before the selected plate lifts.
    for(let step=0;step<8;step++){
      velocity+=((target-split)*165-velocity*13)*dt/8
      split+=velocity*dt/8
    }
    if(reduced.matches || (Math.abs(target-split)<.001 && Math.abs(velocity)<.02)){split=target;velocity=0}
    const goal=desired && shown===desired && split>.50 ? 1 : 0
    amount=lerp(amount,goal,reduced.matches?1:1-Math.exp(-dt*5.6))
    if(Math.abs(amount-goal)<.002)amount=goal
    const index=parts.findIndex(p=>p.name===(desired||shown))
    const cut=index<0?parts.findIndex(p=>p.name==='屋顶机房层')-.5:index
    let moving=split!==target || amount!==goal
    for(const [i,part] of parts.entries()){
      const distance=i-cut
      part.node.position.y=part.base.y+Math.sign(distance)*(15+Math.abs(distance)*2)*split
      part.node.visible=!(i===index && activePlan && amount>.015)
    }
    if(!global || lastAspect!==w/h){
      framingPoint.copy(center)
      if(global)framingPoint.y+=size.y*.065
      frame(cam,framingPoint,global?overviewDirection:streetView,size.x*(global?1.19:1.04),
        global?size.y*1.33+size.z*.14:size.y*1.05+size.z*.15,w/h)
      if(controls){controls.target.copy(framingPoint);controls.update()}
      lastAspect=w/h
    }
    renderer.setViewport(0,0,w,h);renderer.setScissorTest(false);renderer.autoClear=true
    if(plaza && visible && !document.hidden && !reduced.matches)plaza.update(dt)
    if(amount<1)renderer.render(world,cam)
    if(activePlan && amount>0){
      renderer.autoClear=false
      shade.opacity=T.MathUtils.smoothstep(amount,.12,.96)
      renderer.render(wash,screenCam);renderer.clearDepth()
      const elevation=lerp(Math.asin(streetView.y),Math.PI/2-tilt,amount)
      const yaw=lerp(Math.atan2(streetView.x,streetView.z),turn,amount)
      const direction=new T.Vector3(Math.cos(elevation)*Math.sin(yaw),Math.sin(elevation),Math.cos(elevation)*Math.cos(yaw))
      const pw=Math.abs(Math.cos(yaw))*planSize.x+Math.abs(Math.sin(yaw))*planSize.z
      const ph=(Math.abs(Math.sin(yaw))*planSize.x+Math.abs(Math.cos(yaw))*planSize.z)*Math.sin(elevation)+planSize.y*Math.cos(elevation)
      frame(top,planCenter,direction,lerp(size.x*1.05,pw*1.07,amount),lerp(size.y*1.25,ph*1.12,amount),w/h)
      const source=parts[index]
      if(source && amount<1){
        const pos=center.clone();pos.y=source.base.y;pos.project(cam)
        const shift=pos.y*(top.top-top.bottom)*.5*(1-amount)
        top.top-=shift;top.bottom-=shift;top.updateProjectionMatrix()
      }
      renderer.render(plan,top)
      if(amount>.85){
        labels.hidden=false
        const used=[]
        for(const pin of pins){
          const v=pin.point.clone().project(top),x=(v.x+1)*w/2,y=(1-v.y)*h/2
          const half=pin.label.textContent.length*6+4
          pin.label.hidden=x<half||x>w-half||y<58||y>h-24||used.some(a=>Math.abs(a.x-x)<a.half+half&&Math.abs(a.y-y)<17)
          if(!pin.label.hidden){pin.label.style.left=x+'px';pin.label.style.top=y+'px';used.push({x,y,half})}
        }
      }
    }
    if(amount<=.85)labels.hidden=true
    renderer.autoClear=true
    panel.dataset.phase=moving?'moving':desired?'expanded':'assembled'
    if(!moving&&!desired&&activePlan){plan.remove(activePlan);activePlan=null;shown=null}
    if(moving)queue()
    else if(plaza && visible && !document.hidden && !reduced.matches) scheduleAnimation()
  }
  let animationTimer
  function scheduleAnimation(){
    if(animationTimer || dead)return
    animationTimer=setTimeout(()=>{animationTimer=0;queue()},32)
  }
  const visibility=new IntersectionObserver(([entry])=>{visible=entry.isIntersecting;if(visible)queue()})
  visibility.observe(panel)
  document.addEventListener('visibilitychange',queue,{signal:local.signal})
  reduced.addEventListener('change',queue,{signal:local.signal})
  const pendingPlans=new Map()
  function prepare(name) {
    if(cached.has(name))return Promise.resolve(cached.get(name))
    if(!pendingPlans.has(name)){
      const request=loadModel(name,local.signal).then(object=>{
        if(dead){release(object);return null}
        cached.set(name,object);return object
      }).finally(()=>pendingPlans.delete(name))
      pendingPlans.set(name,request)
    }
    return pendingPlans.get(name)
  }
  async function warmPlans(){
    for(const name of ['1F','2F','3F','4F','5F','6F','7F','8F','9F','屋面','B2','B1']){
      if(dead)return
      await new Promise(resolve=>setTimeout(resolve,40))
      if(dead)return
      try{await prepare(name)}catch{/* Foreground selection can retry. */}
    }
  }
  async function showPlan(name,sequence) {
    if(dead||desired!==name||sequence!==epoch)return
    let object=cached.get(name)
    if(!object){
      message('正在读取 '+name+' 楼层模型…')
      try{object=await prepare(name)}
      catch(e){if(!dead&&epoch===sequence)message('楼层视图读取失败',()=>showPlan(name,sequence));return}
      if(dead||sequence!==epoch||!object)return
    }
    if(activePlan)plan.remove(activePlan)
    activePlan=object;shown=name;plan.add(object)
    const box=new T.Box3().setFromObject(object);box.getCenter(planCenter);box.getSize(planSize)
    labels.replaceChildren();pins=[]
    object.traverse(group=>{
      if(!group.name.includes('可定位房间'))return
      for(const room of group.children){
        const bounds=new T.Box3().setFromObject(room)
        if(bounds.isEmpty()||!room.name)continue
        const label=document.createElement('span');label.textContent=room.name.replaceAll('_',' ');labels.append(label)
        pins.push({point:bounds.getCenter(new T.Vector3()),label})
      }
    })
    panel.dataset.preview=shown;message(name==='8F'?'西侧办公室 · 总成标高待核':name==='9F'?'本层独立坐标 · 总成标高待核':'')
    queue()
  }
  function setFloor(name) {
    if(dead||name===desired)return
    if(name){amount=0;turn=0;tilt=.001;panel.dataset.bearing="0"}
    desired=name;epoch++;clearTimeout(timer);planRequest?.abort()
    tag.textContent=name||'';caption.hidden=!name
    caption.textContent=name?name+' · 滚轮旋转，拖动调整视角':''
    if(name&&shown!==name&&activePlan){plan.remove(activePlan);activePlan=null;shown=null}
    panel.dataset.floor=name||'';delete panel.dataset.preview
    if(body){
      message(name?'':hint)
      if(name){const sequence=epoch;showPlan(name,sequence)}
    }
    queue()
  }
  function hit(event){
    if(global||!body||event.pointerType==='touch')return
    const r=renderer.domElement.getBoundingClientRect(),y=event.clientY-r.top
    if(amount>.2)return
    pointer.set((event.clientX-r.left)/w*2-1,1-y/h*2)
    ray.setFromCamera(pointer,cam)
    const hits=ray.intersectObjects(parts.map(p=>p.node),true)
    const picked=hits.find(x=>x.object.visible&&x.object.isMesh)
    let node=picked?.object
    while(node&&!parts.some(p=>p.node===node))node=node.parent
    const key=parts.find(p=>p.node===node)?.name
    select(key==='屋顶机房层'?'屋面':key||null)
  }
  const events={signal:local.signal}
  renderer.domElement.addEventListener('pointermove',hit,events)
  renderer.domElement.addEventListener('pointerleave',()=>select(null),events)
  const resize=new ResizeObserver(()=>{
    w=stage.clientWidth;h=stage.clientHeight
    if(w&&h)renderer.setSize(w,h,false)
    queue()
  });resize.observe(stage)
  async function open(pending) {
    const scene=await loadModel('tower',local.signal,e=>{if(!dead&&e.total)message('正在读取建筑主体总成 '+Math.round(e.loaded/e.total*100)+'%')},pending)
    if(dead){release(scene);return}
    body=scene;world.add(body);instanceTrees(body)
    const root=body.children.find(o=>o.userData.floors)
    if(!root)throw Error('缺少建筑楼层装配节点')
    for(const node of root.children){
      if(!node.userData.floor||node.name.includes('室外'))continue
      parts.push({node,name:node.userData.floor,base:node.position.clone()})
    }
    parts.sort((a,b)=>a.base.y-b.base.y)
    const box=new T.Box3().setFromObject(body);box.getCenter(center);box.getSize(size)
    if(global){
      const firstFloor=parts.find(part=>part.name==='1F')
      if(firstFloor)plaza=createPlaza(firstFloor.node)
    }
    panel.dataset.model='7-02-1A2FB40';message(global?'拖动旋转 · 滚轮缩放 · 人流模拟':desired?'':hint)
    if(desired)showPlan(desired,epoch)
    if(!global)warmPlans()
    queue()
  }
  function dispose() {
    if(dead)return
    clearTimeout(animationTimer);visibility.disconnect();dead=true;epoch++;clearTimeout(timer);cancelAnimationFrame(raf);resize.disconnect()
    signal.removeEventListener('abort',abort);local.abort();planRequest?.abort()
    controls?.dispose()
    if(body)release(body)
    cached.forEach(release);cached.clear();sky.dispose()
    release(wash);labels.remove();renderer.dispose();renderer.forceContextLoss();renderer.domElement.remove()
  }
  return {open,setFloor,orbit,dispose}
}
