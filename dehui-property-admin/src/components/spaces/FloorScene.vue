<template>
  <div ref="stage" class="model-stage" :class="{schematic: (waterItems || stations) && !inPlan}"
       :aria-label="inPlan ? '正交平面，拖动平移，滚轮或双指缩放' : '楼层模型，拖动旋转，滚轮或双指缩放'">
    <div ref="labelLayer" class="plan-labels" aria-hidden="true"></div>
    <div class="view-tools" aria-label="模型视角">
      <button v-if="!projected" type="button" @click="reset(false)">{{ waterItems || stations ? '运行示意 · 轴测' : '轴测' }}</button>
      <button type="button" :aria-pressed="constrainedPlan ? inPlan : undefined" @click="reset(true)">{{ constrainedPlan ? '专业平面' : '俯视' }}</button>
      <button v-if="overlay && !projected" type="button" @click="reset('below')">{{ waterItems || stations ? '运行示意 · 仰视' : '仰视' }}</button>
      <button v-if="waterItems || stations" type="button" @click="focusWater">定位构件</button>
      <button v-if="disciplineScene && (!projected || inPlan)" type="button" :disabled="selection?.index < 0" @click="focusLayer">定位图层</button>
      <button v-if="placedAsset" type="button" :aria-pressed="projected ? !inPlan : undefined" @click="showAsset">定位实体</button>
      <button v-if="placedAsset && locatedHeights" type="button" @click="showElevation">侧视标高</button>
      <button v-if="inPlan && planSegments?.some(s=>s.id===selectedPlanId)" type="button" @click="locateSegment">定位此图段</button>
      <button v-if="disciplineScene" type="button" :aria-pressed="showReference" @click="toggleReference">建筑参照</button>
      <button v-if="stations" type="button" @click="focusStation(true)">设备立面示意</button>
      <button type="button" aria-label="放大模型" @click="zoom(1.35)">＋</button>
      <button type="button" aria-label="缩小模型" @click="zoom(1 / 1.35)">−</button>
      <button v-if="hasMotion && !(stations && inPlan)" type="button" class="play-control" :aria-pressed="playing" @click="toggleMotion">{{ playing ? '暂停演示' : '播放演示' }}</button>
    </div>
    <div v-if="professionalMotion && !inPlan" class="sim-tools">
      <button type="button" :aria-pressed="simulationPlaying" @click="toggleSimulation">{{ simulationPlaying ? '暂停仿真' : '播放仿真' }}</button>
      <span>设备运行示意</span>
    </div>
    <p v-if="constrainedPlan" class="view-note" role="status">{{ viewNote }}</p>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as T from 'three'
import { addStations } from './stationScene'
import { createDisciplineInk } from './disciplineInk'
import { addPlumbing } from './plumbingScene'
import { createFloorMotion } from './floorMotion'
import { createFloorLabels } from './floorLabels'
import { defineRoomModule, createRoomInstance } from './roomModule'
import { createStationParts } from './stationEquipment'
import { buildStations } from './plumbingStations'
import { addGarageMarkings, selectionColor, garageColors, rampProfile, rampGeometry, roadLevel } from './garageMarkings'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { SVGRenderer } from 'three/addons/renderers/SVGRenderer.js'

const props = defineProps({ geometry: { type: Object, required: true }, selection: Object, core: Object, detail: Boolean, garage: Object, rooms: Array, traffic: Boolean, waterItems: Array, plumbing: Object, floorElevation: Number, stations: Array, disciplineScene: Object, placedAsset: Object, locatedHeights: Boolean, planSegments: Array, selectedPlanId: String, roomLabels: Array, startInElevation: Boolean })
const emit = defineEmits(['select','inspect-segment','inspect-instance'])
const stage = ref(null)
const labelLayer = ref(null)
const pickables = []
let labels, plumbingMotion, stationMotion, elevationView=false
const backdrop=[]
const showReference=ref(true)
const metadata = props.disciplineScene?.children[0]?.userData
const projected = metadata?.registered === true && Number.isInteger(metadata.sourceSegments)
const constrainedPlan = Boolean(projected || props.waterItems || props.stations)
const stationSchematic = new T.Group()
const stationPlan = new T.Group()
const inPlan = ref(constrainedPlan)
const placed = new T.Group()
const viewNote = computed(() => {
  if (props.stations) return inPlan.value ? 'P301 平面定位 · 圆点为设备位置，线段为图面流路' : '设备运行示意 · 管路高差、泵体外形与动画速度为显示参数'
  if (inPlan.value) return '平面定位 · 安装标高待核'
  return projected ? '实体视图 · 仅显示已定位模型与建筑参照' : '运行示意 · 管线高差与管径为显示参数'
})
const overlay = Boolean(props.disciplineScene || props.stations || props.waterItems)
const scene = new T.Scene()
const camera = new T.OrthographicCamera(-60, 60, 60, -60, 0.1, 1000)
const meshes = new Map()
const disciplineInk = new Map()
const planFootprints = new T.Group()
function locateSegment() {
  focusObject(planFootprints.children.find(node => node.userData.recordId === props.selectedPlanId))
}
function addPlanFootprints() {
  for (const record of props.planSegments || []) {
    const active = record.id === props.selectedPlanId
    if (record.planLine) {
      const path = retain(new T.BufferGeometry().setFromPoints(record.planLine.map(([x,z])=>new T.Vector3(x,0,z))))
      const line = new T.Line(path, retain(new T.LineBasicMaterial({color:active ? selectionColor : 0x287e81, depthTest:false})))
      line.userData.recordId=record.id
      line.renderOrder=3
      planFootprints.add(line)
      continue
    }
    const [[x1,z1],[x2,z2]] = record.placement.endpointsXZ
    const dx=x2-x1, dz=z2-z1
    const shape = retain(new T.PlaneGeometry(Math.hypot(dx,dz), record.placement.nominalSizeMm[0]/1000))
    shape.rotateX(-Math.PI/2)
    const material = retain(new T.MeshBasicMaterial({color: active ? selectionColor : 0xae7331,
      transparent:true, opacity:active ? .8 : .25, depthTest:false, depthWrite:false}))
    const mark = new T.Mesh(shape, material)
    // This overlay is pickable only in orthographic plan; its Y has no installation meaning.
    mark.position.set((x1+x2)/2,0,(z1+z2)/2)
    mark.rotation.y=-Math.atan2(dz,dx)
    mark.userData.recordId=record.id
    mark.renderOrder=3
    planFootprints.add(mark)
  }
  scene.add(planFootprints)
}
const roomInstances=new Map()
let roomDefinitions=[]
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
let mixers=[]
const professionalMotion=ref(false)
const simulationPlaying=ref(!motionPreference.matches)
function bindSimulation(group) {
  for (const mixer of mixers) {
    mixer.stopAllAction()
    mixer.uncacheRoot(mixer.getRoot())
  }
  mixers=[]
  if (props.disciplineScene) for (const instance of group?.children || []) {
    if (!instance.animations.length) continue
    const mixer=new T.AnimationMixer(instance)
    instance.animations.forEach(clip=>mixer.clipAction(clip).play())
    mixers.push(mixer)
  }
  professionalMotion.value=Boolean(mixers.length)
  previousTime=undefined
}
function toggleSimulation() {
  simulationPlaying.value=!simulationPlaying.value
  resetClock()
}
function render(now) {
  frame=undefined
  const simulate=professionalMotion.value && simulationPlaying.value && !inPlan.value
  const animate=inView && !document.hidden && (simulate || (playing.value && hasMotion.value && !(props.stations && inPlan.value)))
  // Render motion at 20 fps. Camera motion stays responsive, and labels only
  // need reprojection when the camera or selection changes.
  if (labelsDirty || (animate && (previousTime==null || now-previousTime>=50))) {
    if (animate && previousTime!=null) {
      const delta=(now-previousTime)/1000
      if (simulate) mixers.forEach(mixer=>mixer.update(Math.min(delta,.15)))
      if (playing.value && hasMotion.value) elapsed+=delta
    }
    previousTime=animate ? now : undefined
    stationMotion?.update(elapsed)
    plumbingMotion?.update(elapsed)
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
  if(group==='outline') {
    for (const room of roomDefinitions) {
      if (room.origin[1]>=0) continue
      const outline=room.points.map(([x,z])=>new T.Vector2(x+room.origin[0],-z-room.origin[2]))
      shape.holes.push(new T.Path(outline))
    }
  }
  const solid = ['walls','columns','coreWalls','coreColumns'].includes(group)
  const ramp = group === 'lanes' && rampProfile(props.garage.lanes[index])
  const geom = ramp ? retain(rampGeometry(ramp)) : retain(solid
    ? new T.ExtrudeGeometry(shape, { depth: cut, bevelEnabled: false })
    : new T.ShapeGeometry(shape))
  if (!ramp) geom.rotateX(-Math.PI / 2)
  const color = group === 'parking' ? garageColors[props.garage.parking[index].zone] : palette[group]
  const material = retain(new T.MeshLambertMaterial({ color, side: overlay && !solid ? T.FrontSide : T.DoubleSide, transparent: overlay, opacity: overlay ? (solid ? .20 : .09) : 1, depthWrite: !overlay }))
  const mesh = new T.Mesh(geom, material)
  mesh.position.y = { bays:.018, lanes:roadLevel, crossings:.05, parking:.04, rooms:.025 }[group] || 0
  mesh.userData = { group, index }
  pickables.push(mesh)
  const item = (['parking','lanes'].includes(group) && props.garage[group][index])
  if (item) labels.add(group,index,item.name,points,ramp ? ramp.height : () => .10)
  scene.add(mesh)
  meshes.set(group + ':' + index, mesh)
  if (solid || group === 'bays' || group === 'parking') {
    const outline = new T.LineSegments(retain(new T.EdgesGeometry(geom, 15)), edgeMaterial)
    outline.position.y = .008
    mesh.add(outline)
  }
}
const edgeMaterial = retain(new T.LineBasicMaterial({ color: props.disciplineScene ? 0xabbbbe : 0x62747a }))
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
  if (constrainedPlan) {
    inPlan.value = top === true
    controls.enableRotate = !inPlan.value
    controls.mouseButtons.LEFT = inPlan.value ? T.MOUSE.PAN : T.MOUSE.ROTATE
    controls.touches.ONE = inPlan.value ? T.TOUCH.PAN : T.TOUCH.ROTATE
    if (projected) {
      props.disciplineScene.visible = inPlan.value
      placed.visible = !inPlan.value
      planFootprints.visible = inPlan.value
    }
    if (props.waterItems) backdrop.forEach(object => { object.visible = inPlan.value })
  }
  setElevation(false)
  if (props.stations && !inPlan.value) { focusStation(false,top === 'below'); return }
  controls.target.copy(centre)
  camera.position.copy(centre).add(top === 'below' ? new T.Vector3(0,-145,.001) : top ? new T.Vector3(0,145,.001) : new T.Vector3(36,110,81))
  camera.zoom = 1
  camera.updateProjectionMatrix()
  controls.update()
  fit()
}
function setElevation(on) {
  if (!props.stations) return
  elevationView=on
  const schematic=!inPlan.value
  stationPlan.visible=!schematic
  stationSchematic.visible=schematic
  stationMotion?.isolate(schematic)
  backdrop.forEach(object=>{object.visible=!schematic})
  roomInstances.forEach(({root,assembly})=>{
    if (assembly) root.children.forEach(child=>{child.visible=!schematic || child===assembly.equipment})
  })
  labelLayer.value.style.visibility=schematic?'hidden':''
}
function focusStation(front, below=false) {
  const station=props.stations?.[props.selection?.index]
  if (!station || !controls) return
  if (front && inPlan.value) reset(false)
  setElevation(front)
  const [x,z]=station.anchor
  if (inPlan.value) {
    const marker=stationPlan.children.find(obj=>obj.isMesh && obj.userData.index===props.selection?.index)
    focusObject(marker)
    return
  }
  controls.target.set(x,1.25,z)
  camera.position.copy(controls.target).add(front?new T.Vector3(0,.01,100):below?new T.Vector3(0,-100,.001):new T.Vector3(28,45,50))
  controls.update()
  fit()
  camera.zoom=T.MathUtils.clamp(Math.min(camera.right-camera.left,camera.top-camera.bottom)/(front?4.8:9),1,36)
  camera.updateProjectionMatrix()
  schedule()
}
function toggleReference() {
  showReference.value = !showReference.value
  backdrop.forEach(object => { object.visible = showReference.value })
  labelLayer.value.hidden = !showReference.value
  schedule()
}
function focusLayer() {
  const mesh = meshes.get('discipline:' + props.selection?.index)
  if (mesh) focusObject(mesh)
}
function showAsset() {
  if (!props.placedAsset) return
  if (projected && inPlan.value) reset(false)
  focusObject(props.placedAsset)
}
function focusInstance(id) {
  const obj=props.placedAsset?.children.find(child=>child.userData.instanceId===id)
  if (!obj || !controls) return
  if (projected && inPlan.value) reset(false)
  const box=new T.Box3().setFromObject(obj)
  if (box.isEmpty()) return
  const centre=box.getCenter(new T.Vector3())
  box.expandByPoint(new T.Vector3(centre.x,0,centre.z))
  // Leave the surrounding room and floor visible when looking at a small terminal.
  box.expandByScalar(2)
  box.getCenter(centre)
  controls.target.copy(centre)
  camera.position.copy(centre).add(new T.Vector3(55,34,75))
  controls.update()
  const size=box.applyMatrix4(camera.matrixWorldInverse).getSize(new T.Vector3())
  camera.zoom=T.MathUtils.clamp(.85*Math.min((camera.right-camera.left)/size.x,(camera.top-camera.bottom)/size.y),.5,controls.maxZoom)
  camera.updateProjectionMatrix()
  schedule()
}
function showElevation() {
  if (!props.placedAsset || !controls) return
  if (projected && inPlan.value) reset(false)
  const box = new T.Box3().setFromObject(props.placedAsset)
  if (box.isEmpty()) return
  const middle = box.getCenter(new T.Vector3())
  box.expandByPoint(new T.Vector3(middle.x,0,middle.z))
  box.getCenter(middle)
  const size = box.getSize(new T.Vector3())
  const eye = size.x >= size.z ? new T.Vector3(0,.04,1) : new T.Vector3(1,.04,0)
  controls.target.copy(middle)
  camera.position.copy(middle).addScaledVector(eye,145)
  controls.update()
  const visible = box.applyMatrix4(camera.matrixWorldInverse).getSize(new T.Vector3())
  camera.zoom = T.MathUtils.clamp(.85*Math.min((camera.right-camera.left)/Math.max(2,visible.x),
    (camera.top-camera.bottom)/Math.max(2,visible.y)),.5,controls.maxZoom)
  camera.updateProjectionMatrix()
  schedule()
}
function focusObject(object) {
  if (!object || !controls) return
  const box = new T.Box3().setFromObject(object)
  if (box.isEmpty()) return
  const middle = box.getCenter(new T.Vector3())
  camera.position.add(middle.clone().sub(controls.target))
  controls.target.copy(middle)
  controls.update()
  const view = box.applyMatrix4(camera.matrixWorldInverse).getSize(new T.Vector3())
  camera.zoom = T.MathUtils.clamp(Math.min(
    (camera.right - camera.left) / Math.max(1, view.x),
    (camera.top - camera.bottom) / Math.max(1, view.y)
  ) * .9, .5, controls.maxZoom)
  camera.updateProjectionMatrix()
  schedule()
}
function focusWater() {
  if (props.stations) { focusStation(false); return }
  const points = props.waterItems?.[props.selection?.index]?.points
  if (!points || !controls) return
  const target = new T.Vector3()
  for (const [x,z] of points) target.add(new T.Vector3(x,1.6,z))
  target.divideScalar(points.length)
  camera.position.add(target.clone().sub(controls.target))
  controls.target.copy(target)
  const span = points.length===2 ? Math.hypot(points[1][0]-points[0][0], points[1][1]-points[0][1]) : 0
  camera.zoom=T.MathUtils.clamp(Math.min(camera.right-camera.left,camera.top-camera.bottom)/(span+5),1,15)
  camera.updateProjectionMatrix()
  controls.update()
  schedule()
}
function zoom(factor) {
  camera.zoom = T.MathUtils.clamp(camera.zoom * factor, .5, props.stations ? 36 : overlay ? 18 : 10)
  camera.updateProjectionMatrix()
  schedule()
}
function selectPoint(event) {
  if (props.stations && !inPlan.value) return
  if (!pointer || Math.hypot(event.clientX-pointer.x, event.clientY-pointer.y) > 5) return
  const caption = labels.hit(event.clientX,event.clientY)
  if (!props.disciplineScene && !elevationView && caption && (props.stations ? caption.group==='station' : !props.waterItems || caption.group==='water')) { emit('select',caption); return }
  const rect = renderer.domElement.getBoundingClientRect()
  const coords = new T.Vector2((event.clientX-rect.left)/rect.width*2-1, 1-(event.clientY-rect.top)/rect.height*2)
  const ray = new T.Raycaster()
  ray.setFromCamera(coords, camera)
  ray.params.Line.threshold = 2.5 * (camera.top - camera.bottom) / (camera.zoom * rect.height)
  if (placed.visible && props.placedAsset) {
    let object = ray.intersectObject(props.placedAsset, true)[0]?.object
    while (object && object!==placed && !object.userData.instanceId) object=object.parent
    if (object?.userData.instanceId) { emit('inspect-instance',object.userData.instanceId); return }
  }
  if (projected && !inPlan.value) return
  if (inPlan.value && planFootprints.children.length) {
    const footprint = ray.intersectObjects(planFootprints.children, false)[0]
    if (footprint) { emit('inspect-segment',footprint.object.userData.recordId); return }
  }
  const candidates=props.stations ? stationPlan.children : pickables
  if (props.stations) ray.params.Line.threshold = 3 * (camera.top-camera.bottom)/(camera.zoom*rect.height)
  let hit = ray.intersectObjects(candidates, false)[0]
  if (!hit && disciplineInk.size) {
    // Hit tolerance in screen pixels, independent of zoom and viewport size.
    let nearest = Infinity
    for (const candidate of ray.intersectObjects([...disciplineInk.values()], false)) {
      const offset = ray.ray.distanceSqToPoint(candidate.point)
      if (offset < nearest) { hit = candidate; nearest = offset }
    }
  }
  if (hit) {
    const data=hit.object.userData
    emit('select', data.indices ? {group:'water',index:data.indices[hit.instanceId]} : data)
  }
}
async function exportSelectedRoom() {
  const index=props.selection?.index
  const room=roomDefinitions[index]
  if (props.selection?.group!=='rooms' || !room) return
  const {exportRoom}=await import('./roomExport')
  await exportRoom(room)
}
async function exportSelectedStation() {
  const station=props.stations?.[props.selection?.index]
  if (!station) throw Error('请先选择设备位置')
  if (station.kind==='集水坑') throw Error('集水坑须从核验图包导出')
  const {exportRoom}=await import('./roomExport')
  const room=roomDefinitions.find(r=>r.name===station.name)
  if (!room) throw Error('需要关联房间轮廓后才能导出')
  return exportRoom(room)
}
defineExpose({exportSelectedRoom,exportSelectedStation,showAsset,focusInstance})
function startPoint(event) { pointer = { x:event.clientX, y:event.clientY } }
const selectedMaterial = retain(new T.MeshBasicMaterial({color:selectionColor,side:T.DoubleSide}))
let previousMaterial
function highlight() {
  if (stationMotion) {
    stationMotion.select(props.selection?.index)
    for(const item of stationPlan.children)
      item.material.color.set(item.userData.index===props.selection?.index ? selectionColor : 0x247a83)
    if(!inPlan.value) focusStation(elevationView)
    schedule()
    return
  }
  if (plumbingMotion) { plumbingMotion.select(props.selection?.index); schedule(); return }
  if (highlighted && previousMaterial) {
    highlighted.material = previousMaterial
    disciplineInk.get(highlighted)?.material.color.copy(previousMaterial.color)
  }
  previousMaterial = null
  highlighted = meshes.get(props.selection?.group + ':' + props.selection?.index)
  if (highlighted && highlighted.userData.group !== 'outline') {
    previousMaterial = highlighted.material
    highlighted.material = selectedMaterial
    disciplineInk.get(highlighted)?.material.color.set(selectionColor)
  }
  schedule()
}
watch(() => props.selection, highlight)
watch(() => props.placedAsset, (asset, previous) => {
  bindSimulation(asset)
  if (previous) placed.remove(previous)
  if (asset) placed.add(asset)
  else if (projected && !inPlan.value) reset(true)
  if (renderer) schedule()
})
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
  const rooms=props.rooms || []
  const lifts=new Map(buildStations(props.plumbing,rooms).filter(s=>s.kind==='处理间').map(s=>[s.name,s]))
  const coreStrokes=props.core ? [...geo.lines,...props.core.lines] : geo.lines
  roomDefinitions=rooms.map(room=>defineRoomModule(room,rooms,lifts.get(room.name),props.floorElevation,
    room.inCore ? coreStrokes : geo.lines))
  if (!props.detail) {
    surface(geo.outline, 'outline', 0)
    for (const group of (props.traffic || overlay ? ['walls','columns'] : ['bays','walls','columns'])) geo[group].forEach((points, i) => surface(points, group, i))
    linework(geo.lines)

    if (props.traffic) {
      props.garage.crossings?.forEach((points,i) => surface(points,'crossings',i))
      for (const group of ['lanes','parking']) props.garage[group].forEach((item,i) => surface(item.points,group,i))
    }
  }
  const professional=Boolean(props.stations || props.waterItems)
  const parts=createStationParts(retain)
  roomDefinitions.forEach((module,index)=>{
    const room=props.rooms[index]
    const instance=createRoomInstance(module,{retain,motion,parts})
    roomInstances.set(room.name,instance)
    scene.add(instance.root)
    if (!professional) {
      instance.root.traverse(child=>{
        if (child.isMesh) { child.userData={group:'rooms',index}; pickables.push(child) }
      })
      meshes.set('rooms:'+index,instance.slab)
    }
    if (!props.stations?.some(s=>s.name===room.name)) {
      labels.add(professional?'water-room':'rooms',index,room.name,room.points,
        ()=>module.origin[1]+(instance.root.userData.hasEquipment?2.65:1.25))
    }
  })
  if (props.core) {
    props.core.walls.forEach((points,i) => surface(points,'coreWalls',i))
    if (props.detail) {
      props.core.columns.forEach((points,i) => surface(points,'coreColumns',i))
      linework(props.core.lines)
    }
  }
  if (props.stations) {
    const activeRooms=new Set(props.stations.map(s=>roomInstances.get(s.name)?.root))
    backdrop.push(...scene.children.filter(object=>!activeRooms.has(object)))
    pickables.length=0
    // Keep the surveyed XY locations separate from the illustrative pumps,
    // offsets and riser heights used by the stand-alone operating diagram.
    const symbol=retain(new T.CircleGeometry(.45,20))
    symbol.rotateX(-Math.PI/2)
    props.stations.forEach((station,index)=>{
      const marker=new T.Mesh(symbol,retain(new T.MeshBasicMaterial({color:0x247a83,depthTest:false})))
      marker.position.set(station.anchor[0],0,station.anchor[1])
      marker.renderOrder=4
      marker.userData={group:'station',index}
      stationPlan.add(marker)
      const coordinates=station.runs.flatMap(run=>run.points.flatMap(([x,z])=>[x,0,z]))
      const lineGeometry=retain(new T.BufferGeometry())
      lineGeometry.setAttribute('position',new T.Float32BufferAttribute(coordinates,3))
      const lines=new T.LineSegments(lineGeometry,retain(new T.LineBasicMaterial({color:0x247a83,depthTest:false})))
      lines.renderOrder=3
      lines.userData={group:'station',index}
      stationPlan.add(lines)
    })
    stationMotion=addStations(stationSchematic,props.stations,retain,pickables,labels,roomInstances,parts)
    scene.add(stationPlan, stationSchematic)
  }
  if (props.waterItems) {
    backdrop.push(...scene.children)
    pickables.length=0
    plumbingMotion=addPlumbing(scene,props.waterItems,retain,pickables,labels,Boolean(renderer.isWebGLRenderer))
  }
  if (props.disciplineScene) {
    backdrop.push(...scene.children)
    pickables.length = 0
    for (const [index, room] of (props.roomLabels || []).entries())
      labels.add('water-room', index, room.name, room.points, () => 1.25)
    scene.add(props.disciplineScene)
    props.disciplineScene.traverse(mesh => {
      if (!mesh.isMesh) return
      const index = pickables.length
      mesh.userData.group = 'discipline'
      mesh.userData.index = index
      pickables.push(mesh)
      meshes.set('discipline:' + index, mesh)
    })
    // Add the readable stroke after traversal; keep source meshes pickable and
    // keep the original metric width at detail zoom.
    for (const mesh of pickables) {
      const ink = createDisciplineInk(mesh, retain)
      if (ink) {
        mesh.layers.set(1)
        mesh.add(ink)
        disciplineInk.set(mesh, ink)
      }
    }
    addPlanFootprints()
    scene.add(placed)
    if (props.placedAsset) { placed.add(props.placedAsset); bindSimulation(props.placedAsset) }
  }
  const fitPoints = props.detail ? [...props.core.columns.flat(), ...props.core.walls.flat(), ...props.core.lines.flat()] : geo.outline
  bounds = new T.Box3().setFromPoints(fitPoints.map(([x,z]) => new T.Vector3(x,0,z)))
  bounds.getCenter(centre)
  if (props.traffic) {
    addGarageMarkings(scene,props.garage,retain)
    motion.traffic(scene,props.garage)
  }
  hasMotion.value=motion.active || plumbingMotion?.active || stationMotion?.active
  readMotionPreference()
  motionPreference.addEventListener('change',readMotionPreference)
  document.addEventListener('visibilitychange',resetClock)
  visibilityObserver=new IntersectionObserver(([entry])=>{inView=entry.isIntersecting; resetClock()})
  visibilityObserver.observe(stage.value)
  scene.add(new T.AmbientLight(0xffffff, 1.75))
  const sun = new T.DirectionalLight(0xffffff, 1.7)
  sun.position.set(-25,70,30)
  scene.add(sun)
  if (props.waterItems || props.stations) {
    const reflected = new T.DirectionalLight(0xc9e3f4,1.1)
    reflected.position.set(30,-50,20)
    scene.add(reflected)
  }
  controls = new OrbitControls(camera, renderer.domElement)
  controls.maxPolarAngle = overlay ? Math.PI - .000001 : Math.PI / 2.3
  controls.minZoom = .5
  controls.maxZoom = props.stations ? 36 : overlay ? 18 : 10
  controls.addEventListener('change', schedule)
  renderer.domElement.addEventListener('pointerdown', startPoint)
  renderer.domElement.addEventListener('pointerup', selectPoint)
  sizeToViewport()
  window.addEventListener('resize', sizeToViewport)
  labels.prepare()
  reset(overlay)
  if (props.startInElevation) focusStation(true)
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
  for (const [mesh, ink] of disciplineInk) { mesh.layers.enable(0); mesh.layers.disable(1); ink.removeFromParent() }
  bindSimulation(null)
  if (props.placedAsset) placed.remove(props.placedAsset)
  if (projected) props.disciplineScene.visible = true
  if (highlighted && previousMaterial) highlighted.material = previousMaterial
  resources.forEach(resource => resource.dispose())
  renderer?.dispose?.()
})
</script>

<style scoped>
.sim-tools{position:absolute;bottom:10px;right:10px;padding:4px 7px;background:#fffffff0;border-radius:4px;display:flex;gap:7px;align-items:center;font-size:12px}.sim-tools button{font:inherit;cursor:pointer;padding:3px 7px;border:1px solid #cbd4d6;background:white;border-radius:4px}
.model-stage { position: relative; width:100%; height:clamp(360px, min(var(--available-model-height, 78svh), 96cqw), 1500px); overflow: hidden; border-radius: 6px; background: #f3f5f4; }
.model-stage :deep(canvas), .model-stage :deep(svg) { display: block; touch-action: none; }
.plan-labels { position:absolute; inset:0; pointer-events:none; overflow:hidden; }
.plan-labels :deep(.plan-label) { position:absolute; top:0; left:0; white-space:nowrap; font:600 11px/16px system-ui,sans-serif; color:#243744; text-shadow:0 0 3px white; }
.plan-labels :deep(.rooms), .plan-labels :deep(.lanes) { padding:0 4px; background:#fffc; border-radius:3px; }
.plan-labels :deep(.lanes) {font-weight:500;}
.plan-labels :deep(.active) {color:#fff; background:var(--selection-color); border-radius:3px; text-shadow:none; outline:2px solid white;}
.view-note {position:absolute;bottom:8px;left:10px;right:auto;max-width:calc(100% - 20px);margin:0;padding:3px 7px;background:#fffffff0;color:#405057;font:12px/1.5 system-ui,sans-serif;border-radius:3px;pointer-events:none;}
.schematic :deep(.water-room) {display:none;}
.view-tools { position: absolute; top: 10px; left: 10px; max-width:calc(100% - 20px); flex-wrap:wrap; display: flex; gap: 2px; padding: 3px; background: #fff; border: 1px solid #d8dcdc; border-radius: 6px; }
.view-tools button { min-width: 32px; min-height: 30px; border: 0; background: transparent; cursor: pointer; font: inherit; color: inherit; }
.view-tools .play-control {border-left:1px solid #d8dcdc; padding:0 8px;}
.view-tools button[aria-pressed=true] {background:#edf0ef;}
.view-tools button:disabled {opacity:.48;cursor:default;}
.view-tools button:hover { background: #edf0ef; }
@container (max-width:800px) { .model-stage {height:clamp(280px, min(var(--available-model-height, 75svh), 97cqw), 720px);} }
</style>
