<template>
  <div ref="stage" class="model-stage" aria-label="楼层模型，拖动旋转，滚轮或双指缩放">
    <div class="view-tools" aria-label="模型视角">
      <button type="button" @click="reset(false)">轴测</button>
      <button type="button" @click="reset(true)">俯视</button>
      <button type="button" aria-label="放大模型" @click="zoom(1.35)">＋</button>
      <button type="button" aria-label="缩小模型" @click="zoom(1 / 1.35)">−</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as T from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { SVGRenderer } from 'three/addons/renderers/SVGRenderer.js'

const props = defineProps({ geometry: { type: Object, required: true }, selection: Object })
const emit = defineEmits(['select'])
const stage = ref(null)
const scene = new T.Scene()
const camera = new T.OrthographicCamera(-60, 60, 60, -60, 0.1, 1000)
const meshes = new Map()
const palette = { outline: 0xdde5e6, walls: 0x849a9f, columns: 0x536e79, bays: 0xb5ccbc }
const resources = new Set()
const cut = 1.2
let renderer, controls, observer, highlighted, pointer, frame
const centre = new T.Vector3()
let radius

function retain(resource) { resources.add(resource); return resource }
function render() { if (renderer) renderer.render(scene, camera) }
function schedule() { cancelAnimationFrame(frame); frame = requestAnimationFrame(render) }

function surface(points, group, index) {
  const shape = new T.Shape()
  points.forEach(([x,z], i) => i ? shape.lineTo(x, -z) : shape.moveTo(x, -z))
  shape.closePath()
  const solid = group === 'walls' || group === 'columns'
  const geom = retain(solid
    ? new T.ExtrudeGeometry(shape, { depth: cut, bevelEnabled: false })
    : new T.ShapeGeometry(shape))
  geom.rotateX(-Math.PI / 2)
  const material = retain(new T.MeshLambertMaterial({ color: palette[group], side: T.DoubleSide }))
  const mesh = new T.Mesh(geom, material)
  mesh.position.y = group === 'bays' ? .018 : 0
  mesh.userData = { group, index }
  scene.add(mesh)
  meshes.set(group + ':' + index, mesh)
  if (solid || group === 'bays') {
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
function fit() {
  const w = stage.value.clientWidth, h = stage.value.clientHeight
  if (!w || !h) return
  const aspect = w / h
  const half = radius / Math.min(1, aspect)
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
  schedule()
}
function zoom(factor) {
  camera.zoom = T.MathUtils.clamp(camera.zoom * factor, .5, 10)
  camera.updateProjectionMatrix()
  schedule()
}
function selectPoint(event) {
  if (!pointer || Math.hypot(event.clientX-pointer.x, event.clientY-pointer.y) > 5) return
  const rect = renderer.domElement.getBoundingClientRect()
  const coords = new T.Vector2((event.clientX-rect.left)/rect.width*2-1, 1-(event.clientY-rect.top)/rect.height*2)
  const ray = new T.Raycaster()
  ray.setFromCamera(coords, camera)
  const hit = ray.intersectObjects([...meshes.values()], false)[0]
  if (hit) emit('select', hit.object.userData)
}
function startPoint(event) { pointer = { x:event.clientX, y:event.clientY } }
function highlight() {
  if (highlighted) highlighted.material.color.setHex(palette[highlighted.userData.group])
  highlighted = meshes.get(props.selection?.group + ':' + props.selection?.index)
  if (highlighted && highlighted.userData.group !== 'outline') highlighted.material.color.setHex(0xe3a253)
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
  const geo = props.geometry
  surface(geo.outline, 'outline', 0)
  for (const group of ['bays', 'walls', 'columns']) geo[group].forEach((points, i) => surface(points, group, i))
  linework(geo.lines)
  const bounds = new T.Box3().setFromPoints(geo.outline.map(([x,z]) => new T.Vector3(x,0,z)))
  bounds.getCenter(centre)
  radius = bounds.getSize(new T.Vector3()).length() * .50
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
  reset(false)
  fit()
  highlight()
  observer = new ResizeObserver(fit)
  observer.observe(stage.value)
})
onBeforeUnmount(() => {
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
.model-stage { position: relative; height: clamp(320px, 56vh, 720px); overflow: hidden; border-radius: 6px; background: #f3f5f4; }
.model-stage :deep(canvas), .model-stage :deep(svg) { display: block; touch-action: none; }
.view-tools { position: absolute; top: 10px; left: 10px; display: flex; gap: 2px; padding: 3px; background: #fff; border: 1px solid #d8dcdc; border-radius: 6px; }
.view-tools button { min-width: 32px; min-height: 30px; border: 0; background: transparent; cursor: pointer; font: inherit; color: inherit; }
.view-tools button:hover { background: #edf0ef; }
</style>
