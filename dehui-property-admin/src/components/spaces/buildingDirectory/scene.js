import * as T from 'three'
import {loadModel} from './model'
import {daylight, instanceTrees} from './daylight'

const lerp = T.MathUtils.lerp
const upView = new T.Vector3(0,1,.065).normalize()
const streetView = new T.Vector3(.32,.23,1).normalize()
const hint = '悬停楼层可查看俯视模型'
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
  const reduced=matchMedia('(prefers-reduced-motion: reduce)')
  const local=new AbortController()
  let desired=null,shown=null,body=null,activePlan=null,epoch=0,planRequest,timer,raf=0,last=0,amount=0,dead=false
  let w=0,h=0,lower=0
  const abort=()=>local.abort();signal.addEventListener('abort',abort,{once:true})

  function queue(){if(!dead&&!raf)raf=requestAnimationFrame(paint)}
  function renderPart(scene,camera,x,y,width,height){
    renderer.setViewport(x,y,width,height);renderer.setScissor(x,y,width,height);renderer.render(scene,camera)
  }
  function paint(now) {
    raf=0
    if(!w||!h||!body||dead)return
    const weight=reduced.matches?1:1-Math.exp(-Math.min(.06,Math.max(.016,(now-last)/1000))*8)
    last=now
    const target=desired?1:0
    amount=lerp(amount,target,weight)
    if(Math.abs(amount-target)<.001)amount=target
    const index=parts.findIndex(p=>p.name===desired)
    const cut=index<0?parts.findIndex(p=>p.name==='屋顶机房层')-.5:index
    let moving=amount!==target
    parts.forEach((part,i)=>{
      const sign=i===cut?0:i>cut?1:-1
      const y=desired?((i-(parts.length-1)/2)*1.25+sign*7.8):0
      const z=desired&&i===index?9:0
      for(const [axis,shift] of [['y',y],['z',z]]){
        const goal=part.base[axis]+shift
        const next=lerp(part.node.position[axis],goal,weight)
        part.node.position[axis]=Math.abs(next-goal)<.014?goal:next
        moving ||= part.node.position[axis]!==goal
      }
    })
    lower=Math.round(h*.43*amount)
    const upper=h-lower
    const aim=center.clone();aim.y+=amount*1.5
    const direction=streetView.clone();direction.y+=amount*.12;direction.normalize()
    // The site is intentionally included at rest. The split view fits the displaced roof and basements.
    const width=(size.x*.98+size.z*.25)*1.09
    frame(cam,aim,direction,width,size.y*1.19+size.z*.26+amount*29,w/upper)
    renderer.setScissorTest(true);renderPart(world,cam,0,lower,w,upper)
    if(lower>0){
      renderer.setViewport(0,0,w,lower);renderer.setScissor(0,0,w,lower)
      renderer.setClearColor(0xf6f8f7);renderer.clear()
      const noteHeight = ['8F','9F'].includes(shown) ? 26 : 8
      const available = lower - 30 - noteHeight
      if(activePlan && available > 4){
        const zoom=1+(1-amount)*.72
        frame(top,planCenter,upView,Math.max(planSize.x,2)*1.16*zoom,(Math.max(planSize.z,2)*1.15+planSize.y*.10)*zoom,w/available)
        renderPart(plan,top,0,noteHeight,w,available)
      }
    }
    panel.dataset.phase=moving?'moving':desired?'expanded':'assembled'
    if(!moving&&!desired&&activePlan){plan.remove(activePlan);activePlan=null;shown=null}
    if(moving)queue()
  }
  async function showPlan(name,sequence) {
    if(dead||desired!==name||sequence!==epoch)return
    let object=cached.get(name)
    if(!object){
      message('正在读取 '+name+' 楼层模型…')
      planRequest=new AbortController()
      try{object=await loadModel(name,planRequest.signal)}
      catch(e){if(!dead&&epoch===sequence)message('楼层视图读取失败',()=>showPlan(name,sequence));return}
      if(dead||sequence!==epoch){release(object);return}
      cached.set(name,object)
      if(cached.size>4){const [key,old]=cached.entries().next().value;cached.delete(key);release(old)}
    }
    if(activePlan)plan.remove(activePlan)
    activePlan=object;shown=name;plan.add(object)
    const box=new T.Box3().setFromObject(object);box.getCenter(planCenter);box.getSize(planSize)
    panel.dataset.preview=shown;message(name==='8F'?'西侧办公室 · 总成标高待核':name==='9F'?'本层独立坐标 · 总成标高待核':'')
    queue()
  }
  function setFloor(name) {
    if(dead||name===desired)return
    desired=name;epoch++;clearTimeout(timer);planRequest?.abort()
    tag.textContent=name||'';caption.hidden=!name
    caption.textContent=name?name+' · 三维俯视':''
    if(name&&shown!==name&&activePlan){plan.remove(activePlan);activePlan=null;shown=null}
    panel.dataset.floor=name||'';delete panel.dataset.preview
    if(body){
      message(name?'':hint)
      if(name){const sequence=epoch;timer=setTimeout(()=>showPlan(name,sequence),70)}
    }
    queue()
  }
  function hit(event){
    if(!body||event.pointerType==='touch')return
    const r=renderer.domElement.getBoundingClientRect(),y=event.clientY-r.top
    if(y>h-lower)return
    pointer.set((event.clientX-r.left)/w*2-1,1-y/(h-lower)*2)
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
  async function open() {
    const scene=await loadModel('tower',local.signal,e=>{if(!dead&&e.total)message('正在读取建筑主体总成 '+Math.round(e.loaded/e.total*100)+'%')})
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
    panel.dataset.model='7-02-1A2FB40';message(desired?'':hint)
    if(desired)showPlan(desired,epoch)
    queue()
  }
  function dispose() {
    if(dead)return
    dead=true;epoch++;clearTimeout(timer);cancelAnimationFrame(raf);resize.disconnect()
    signal.removeEventListener('abort',abort);local.abort();planRequest?.abort()
    if(body)release(body)
    cached.forEach(release);cached.clear();sky.dispose()
    renderer.dispose();renderer.forceContextLoss();renderer.domElement.remove()
  }
  return {open,setFloor,dispose}
}
