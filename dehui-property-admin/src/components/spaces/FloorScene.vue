<template>
  <div ref="stage" class="model-stage" aria-label="楼层模型，拖动旋转，滚轮或双指缩放">
    <div ref="labelLayer" class="plan-labels" aria-hidden="true"></div>
    <div class="view-tools" aria-label="模型视角">
      <button type="button" @click="reset(false)">轴测</button>
      <button type="button" @click="reset(true)">俯视</button>
      <button type="button" aria-label="放大模型" @click="zoom(1.35)">＋</button>
      <button type="button" aria-label="缩小模型" @click="zoom(1 / 1.35)">−</button>
      <button v-if="hasMotion" type="button" class="play-control" :aria-pressed="playing" @click="toggleMotion">{{ playing ? '暂停演示' : '播放演示' }}</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as T from 'three'
import { createFloorMotion } from './floorMotion'
import { createFloorLabels } from './floorLabels'
import { addSpaceCutaway } from './spaceCutaway'
import { addGarageMarkings, selectionColor, garageColors, rampProfile, rampGeometry, roadLevel } from './garageMarkings'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { SVGRenderer } from 'three/addons/renderers/SVGRenderer.js'

const props = defineProps({ geometry: { type: Object, required: true }, selection: Object, core: Object, detail: Boolean, garage: Object, rooms: Array, traffic: Boolean })
const emit = defineEmits(['select'])
const stage = ref(null)
const labelLayer = ref(null)
const pickables = []
let labels
const scene = new T.Scene()
const camera = new T.OrthographicCamera(-60, 60, 60, -60, 0.1, 1000)
const meshes = new Map()
const palette = { outline: 0xdde5e6, walls: 0x849a9f, columns: 0x536e79, bays: 0xb5ccbc, coreWalls: 0x9c8366, coreColumns: 0x54737b, ...garageColors, crossings:garageColors.lanes }
const resources = new Set()
const cut = 1.2
let renderer, controls, observer, highlighted, pointer, frame
const centre = new T.Vector3()
let bounds

function retain(resource) { resources.add(resource); return resource }
const motion = createFloorMotion(retain)
const hasMotion = ref(false)
const playing = ref(false)
let inView=true, labelsDirty=true, previousTime, elapsed=0, visibilityObserver
const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
function render(now) {
  frame=undefined
  const animate=playing.value && inView && !document.hidden && hasMotion.value
  // Render motion at 20 fps. Camera motion stays responsive, and labels only
  // need reprojection when the camera or selection changes.
  if (labelsDirty || (animate && (previousTime==null || now-previousTime>=50))) {
    if (animate && previousTime!=null) elapsed+=(now-previousTime)/1000
    previousTime=animate ? now : undefined
    motion.update(elapsed)
    renderer.render(scene,camera)
    if (labelsDirty) labels.update(camera,stage.value.clientWidth,stage.value.clientHeight,props.selection)
    labelsDirty=false
  }
  if (animate) frame=requestAnimationFrame(render)
}
function schedule() {
  labelsDirty=true
  if (frame==null) frame=requestAnimationFrame(render)
}
function resetClock() { previousTime=undefined; schedule() }
function readMotionPreference() {
  const stored=sessionStorage.getItem('b2-model-motion')
  playing.value=stored===null ? !motionPreference.matches : stored==='on'
  resetClock()
}
function toggleMotion() {
  playing.value=!playing.value
  sessionStorage.setItem('b2-model-motion',playing.value ? 'on' : 'off')
  resetClock()
}

function surface(points, group, index) {
  const shape = new T.Shape()
  points.forEach(([x,z], i) => i ? shape.lineTo(x, -z) : shape.moveTo(x, -z))
  shape.closePath()
  const solid = ['walls','columns','coreWalls','coreColumns'].includes(group)
  const ramp = group === 'lanes' && rampProfile(props.garage.lanes[index])
  const geom = ramp ? retain(rampGeometry(ramp)) : retain(solid
    ? new T.ExtrudeGeometry(shape, { depth: cut, bevelEnabled: false })
    : new T.ShapeGeometry(shape))
  if (!ramp) geom.rotateX(-Math.PI / 2)
  const color = group === 'parking' ? garageColors[props.garage.parking[index].zone] : palette[group]
  const room = group === 'rooms' && props.rooms[index]
  const floorColor = room?.name === '电梯厅' ? 0xe6d4aa : room?.name?.includes('电梯井') ? 0x92a7b7 : color
  const material = retain(new T.MeshLambertMaterial({ color:floorColor, side: T.DoubleSide }))
  const mesh = new T.Mesh(geom, material)
  mesh.position.y = { bays:.018, lanes:roadLevel, crossings:.05, parking:.04, rooms:.025 }[group] || 0
  mesh.userData = { group, index }
  pickables.push(mesh)
  if (room) {
    addSpaceCutaway(mesh,room,props.rooms,retain,pickables)
    motion.equipment(mesh,room,pickables)
  }
  const item = room || (['parking','lanes'].includes(group) && props.garage[group][index])
  if (item) labels.add(group,index,item.name,points,ramp ? ramp.height : () => mesh.userData.hasEquipment ? 2.65 : room ? 1.25 : .10)
  scene.add(mesh)
  meshes.set(group + ':' + index, mesh)
  if (solid || group === 'bays' || group === 'parking') {
    const outline = new T.LineSegments(retain(new T.EdgesGeometry(geom, 15)), edgeMaterial)
    outline.position.y = .008
    mesh.add(outline)
  }
}
const edgeMaterial = retain(new T.LineBasicMaterial({ color: 0x62747a }))
function linework(paths) {
  const vertices = []
  for (const points of paths) {
    for (let i = 1; i < points.length; i++) {
      for (const [x,z] of [points[i-1], points[i]]) vertices.push(x, .035, z)
    }
  }
  const geom = retain(new T.BufferGeometry())
  geom.setAttribute('position', new T.Float32BufferAttribute(vertices, 3))
  scene.add(new T.LineSegments(geom, edgeMaterial))
}
function sizeToViewport() {
  const element = stage.value
  if (!element || document.fullscreenElement) return
  const main = element.closest('#dhic-main')
  const top = element.getBoundingClientRect().top + (main?.scrollTop ?? window.scrollY)
  const bottom = Math.min(window.innerHeight, main?.getBoundingClientRect().bottom ?? window.innerHeight)
  element.style.setProperty('--available-model-height', Math.floor(bottom - top - 12) + 'px')
}
function fit() {
  const w = stage.value.clientWidth, h = stage.value.clientHeight
  if (!w || !h) return
  const aspect = w / h
  const size = bounds.getSize(new T.Vector3())
  const direction = camera.position.clone().sub(controls.target).normalize()
  const right = new T.Vector3().crossVectors(camera.up, direction).normalize()
  const up = new T.Vector3().crossVectors(direction, right)
  const extent = v => Math.abs(v.x)*size.x + Math.abs(v.y)*cut + Math.abs(v.z)*size.z
  const half = Math.max(extent(up), extent(right) / aspect) * .516
  Object.assign(camera, { left: -half * aspect, right: half * aspect, top: half, bottom: -half })
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
  schedule()
}
function reset(top) {
  if (!controls) return
  controls.target.copy(centre)
  camera.position.copy(centre).add(top ? new T.Vector3(0,145,.001) : new T.Vector3(36,110,81))
  camera.zoom = 1
  camera.updateProjectionMatrix()
  controls.update()
  fit()
}
function zoom(factor) {
  camera.zoom = T.MathUtils.clamp(camera.zoom * factor, .5, 10)
  camera.updateProjectionMatrix()
  schedule()
}
function selectPoint(event) {
  if (!pointer || Math.hypot(event.clientX-pointer.x, event.clientY-pointer.y) > 5) return
  const caption = labels.hit(event.clientX,event.clientY)
  if (caption) { emit('select',caption); return }
  const rect = renderer.domElement.getBoundingClientRect()
  const coords = new T.Vector2((event.clientX-rect.left)/rect.width*2-1, 1-(event.clientY-rect.top)/rect.height*2)
  const ray = new T.Raycaster()
  ray.setFromCamera(coords, camera)
  const hit = ray.intersectObjects(pickables, false)[0]
  if (hit) emit('select', hit.object.userData)
}
function startPoint(event) { pointer = { x:event.clientX, y:event.clientY } }
const selectedMaterial = retain(new T.MeshBasicMaterial({color:selectionColor,side:T.DoubleSide}))
let previousMaterial
function highlight() {
  if (highlighted && previousMaterial) highlighted.material = previousMaterial
  previousMaterial = null
  highlighted = meshes.get(props.selection?.group + ':' + props.selection?.index)
  if (highlighted && highlighted.userData.group !== 'outline') {
    previousMaterial = highlighted.material
    highlighted.material = selectedMaterial
  }
  schedule()
}
watch(() => props.selection, highlight)
onMounted(() => {
  try {
    renderer = new T.WebGLRenderer({ antialias: true, alpha: false })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  } catch {
    renderer = new SVGRenderer()
  }
  renderer.setClearColor(new T.Color(0xf3f5f4), 1)
  stage.value.prepend(renderer.domElement)
  labels = createFloorLabels(labelLayer.value)
  const geo = props.geometry
  if (!props.detail) {
    surface(geo.outline, 'outline', 0)
    for (const group of (props.traffic ? ['walls','columns'] : ['bays','walls','columns'])) geo[group].forEach((points, i) => surface(points, group, i))
    linework(geo.lines)

    if (props.traffic) {
      props.garage.crossings?.forEach((points,i) => surface(points,'crossings',i))
      for (const group of ['lanes','parking']) props.garage[group].forEach((item,i) => surface(item.points,group,i))
    }
  }
  props.rooms?.forEach((item,i) => surface(item.points,'rooms',i))
  if (props.core) {
    props.core.walls.forEach((points,i) => surface(points,'coreWalls',i))
    if (props.detail) {
      props.core.columns.forEach((points,i) => surface(points,'coreColumns',i))
      linework(props.core.lines)
    }
  }
  const fitPoints = props.detail ? [...props.core.columns.flat(), ...props.core.walls.flat(), ...props.core.lines.flat()] : geo.outline
  bounds = new T.Box3().setFromPoints(fitPoints.map(([x,z]) => new T.Vector3(x,0,z)))
  bounds.getCenter(centre)
  if (props.traffic) {
    addGarageMarkings(scene,props.garage,retain)
    motion.traffic(scene,props.garage)
  }
  hasMotion.value=motion.active
  readMotionPreference()
  motionPreference.addEventListener('change',readMotionPreference)
  document.addEventListener('visibilitychange',resetClock)
  visibilityObserver=new IntersectionObserver(([entry])=>{inView=entry.isIntersecting; resetClock()})
  visibilityObserver.observe(stage.value)
  scene.add(new T.AmbientLight(0xffffff, 1.75))
  const sun = new T.DirectionalLight(0xffffff, 1.7)
  sun.position.set(-25,70,30)
  scene.add(sun)
  controls = new OrbitControls(camera, renderer.domElement)
  controls.maxPolarAngle = Math.PI / 2.3
  controls.minZoom = .5
  controls.maxZoom = 10
  controls.addEventListener('change', schedule)
  renderer.domElement.addEventListener('pointerdown', startPoint)
  renderer.domElement.addEventListener('pointerup', selectPoint)
  sizeToViewport()
  window.addEventListener('resize', sizeToViewport)
  labels.prepare()
  reset(false)
  highlight()
  observer = new ResizeObserver(() => { sizeToViewport(); fit() })
  observer.observe(stage.value)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', sizeToViewport)
  motionPreference.removeEventListener('change',readMotionPreference)
  document.removeEventListener('visibilitychange',resetClock)
  visibilityObserver?.disconnect()
  observer?.disconnect()
  controls?.dispose()
  renderer?.domElement.removeEventListener('pointerdown', startPoint)
  renderer?.domElement.removeEventListener('pointerup', selectPoint)
  cancelAnimationFrame(frame)
  resources.forEach(resource => resource.dispose())
  renderer?.dispose?.()
})
</script>

<style scoped>
.model-stage { position: relative; width:100%; height:clamp(360px, min(var(--available-model-height, 78svh), 96cqw), 1500px); overflow: hidden; border-radius: 6px; background: #f3f5f4; }
.model-stage :deep(canvas), .model-stage :deep(svg) { display: block; touch-action: none; }
.plan-labels { position:absolute; inset:0; pointer-events:none; overflow:hidden; }
.plan-labels :deep(.plan-label) { position:absolute; top:0; left:0; white-space:nowrap; font:600 11px/16px system-ui,sans-serif; color:#243744; text-shadow:0 0 3px white; }
.plan-labels :deep(.rooms), .plan-labels :deep(.lanes) { padding:0 4px; background:#fffc; border-radius:3px; }
.plan-labels :deep(.lanes) {font-weight:500;}
.plan-labels :deep(.active) {color:#fff; background:var(--selection-color); border-radius:3px; text-shadow:none; outline:2px solid white;}
.view-tools { position: absolute; top: 10px; left: 10px; display: flex; gap: 2px; padding: 3px; background: #fff; border: 1px solid #d8dcdc; border-radius: 6px; }
.view-tools button { min-width: 32px; min-height: 30px; border: 0; background: transparent; cursor: pointer; font: inherit; color: inherit; }
.view-tools .play-control {border-left:1px solid #d8dcdc; padding:0 8px;}
.view-tools button:hover { background: #edf0ef; }
@container (max-width:800px) { .model-stage {height:clamp(280px, min(var(--available-model-height, 75svh), 97cqw), 720px);} }
</style>
