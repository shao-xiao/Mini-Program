<template>
  <div class="object-stage" ref="stage" aria-label="GLB 实体近看">
    <div class="tools">
      <button @click="fit">适合窗口</button>
      <button @click="view('front')">正视</button><button @click="view('top')">俯视</button>
      <button v-if="clips.length || demoCaption" :aria-pressed="playing" @click="toggle">{{ playing ? '暂停演示' : '演示动作' }}</button>
    </div>
    <p v-if="demoCaption" class="demo-caption">{{ demoCaption }} · {{ demoState || '构件局部演示' }}</p>
    <p v-if="failure" role="alert">{{ failure }}</p>
    <p v-else-if="Number.isFinite(floorOffset)" class="datum">网格为本处完成地面 · 0.000 m</p>
  </div>
</template>
<script setup>
import {onBeforeUnmount,onMounted,ref} from 'vue'
import * as T from 'three'
import {componentSimulation} from './componentSimulation'
import {OrbitControls} from 'three/addons/controls/OrbitControls.js'
const props=defineProps({object:Object, clips:Array, floorOffset:Number, demoName:String})
const stage=ref(null), playing=ref(false), failure=ref('')
let renderer, camera, controls, observer, mixer, frame, last, datum
const world=new T.Scene(), box=new T.Box3(), middle=new T.Vector3()
const demoCaption=ref(''),demoState=ref('')
let demo,elapsed=0,distance=1
function fit() { view('iso') }
function view(angle) {
  if (!controls) return
  const dir=angle==='top' ? [0,1,.0001] : angle==='front' ? [0,.0001,1] : [1,.8,1.25]
  camera.position.copy(middle).addScaledVector(new T.Vector3(...dir).normalize(),distance*2.5)
  controls.target.copy(middle)
  camera.zoom=1
  camera.updateProjectionMatrix()
  controls.update()
  draw()
}
function resize() {
  const w=stage.value.clientWidth,h=stage.value.clientHeight
  if (!w || !h) return
  const aspect=w/h, half=distance*.55/Math.min(1,aspect)
  Object.assign(camera,{left:-half*aspect,right:half*aspect,top:half,bottom:-half})
  camera.updateProjectionMatrix()
  renderer.setSize(w,h)
  draw()
}
function draw(){renderer.render(world,camera)}
function tick(now) {
  const delta=last ? Math.min((now-last)/1000,.08) : 0
  elapsed+=delta
  demoState.value=demo?.update(elapsed) || ''
  mixer?.update(delta)
  last=now; draw()
  if (playing.value) frame=requestAnimationFrame(tick)
}
function toggle(){
  playing.value=!playing.value
  cancelAnimationFrame(frame)
  if (playing.value) { last=0;frame=requestAnimationFrame(tick) }
}
onMounted(()=>{
  try {renderer=new T.WebGLRenderer({antialias:true})}
  catch {failure.value='此设备未能打开实体三维视图。可返回楼层查看定位。';return}
  world.add(props.object)
  world.background=new T.Color(0xf0f3f3)
  world.add(new T.HemisphereLight(0xffffff,0xb3bec2,2))
  const light=new T.DirectionalLight(0xfffcf3,2);light.position.set(4,8,5);world.add(light)
  demo=props.demoName && componentSimulation(props.object,props.demoName)
  demoCaption.value=demo?.caption || ''
  box.setFromObject(props.object)
  if (Number.isFinite(props.floorOffset)) {
    const size = box.getSize(new T.Vector3())
    box.getCenter(middle)
    datum = new T.GridHelper(Math.max(size.x, size.z, size.y) * 1.35, 8, 0x809599, 0xb0c0c3)
    datum.position.set(middle.x, props.floorOffset, middle.z)
    world.add(datum)
    box.expandByPoint(datum.position)
  }
  box.getCenter(middle)
  distance=Math.max(.1,box.getSize(new T.Vector3()).length())
  camera=new T.OrthographicCamera(-1,1,1,-1,.001,distance*20)
  renderer.setPixelRatio(Math.min(devicePixelRatio||1,2))
  stage.value.prepend(renderer.domElement)
  controls=new OrbitControls(camera,renderer.domElement)
  controls.minZoom=.25;controls.maxZoom=30
  controls.addEventListener('change',draw)
  if (props.clips.length) {
    mixer=new T.AnimationMixer(props.object)
    props.clips.forEach(clip=>mixer.clipAction(clip).play())
  }
  resize();fit()
  if(demo && !matchMedia('(prefers-reduced-motion: reduce)').matches) toggle()
  observer=new ResizeObserver(resize);observer.observe(stage.value)
})
onBeforeUnmount(()=>{
  cancelAnimationFrame(frame);observer?.disconnect();controls?.dispose()
  mixer?.stopAllAction();mixer?.uncacheRoot(props.object)
  demo?.dispose();world.remove(props.object)
  datum?.dispose()
  renderer?.dispose()
})
</script>
<style scoped>
.object-stage{position:relative;min-width:0;height:clamp(330px,66vh,840px);overflow:hidden;border-radius:6px}
.tools{position:absolute;top:10px;left:10px;display:flex;flex-wrap:wrap;gap:2px;background:white;padding:3px;border:1px solid #d6dcdd;border-radius:5px}
.demo-caption{position:absolute;bottom:8px;right:10px;background:#fffffff0;padding:3px 7px;font-size:12px;margin:0}.datum{position:absolute;bottom:8px;left:10px;margin:0;padding:3px 7px;background:#fffffff0;color:#405057;font-size:12px;pointer-events:none}
button{font:inherit;font-size:13px;color:inherit;cursor:pointer;background:white;border:0;padding:6px}
.object-stage :deep(canvas){display:block;touch-action:none;width:100%}
@container(max-width:800px){.object-stage{height:clamp(280px,90cqw,500px)}}
</style>
