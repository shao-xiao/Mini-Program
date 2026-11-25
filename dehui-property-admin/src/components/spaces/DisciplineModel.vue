<template>
  <div class="system-layout">
    <FloorScene v-if="scene" :key="item.id" :geometry="geometry" :discipline-scene="scene" :room-labels="roomLabels" :selection="selection" @select="selection=$event" />
    <div v-else class="pending" role="status">{{ error || '正在读取专业模型…' }} <button v-if="error" @click="retry++">重试</button></div>
    <aside aria-label="专业模型详情">
      <h3>{{ title }}</h3>
      <label v-if="layers.length">图层
        <select v-model.number="selectedIndex" aria-label="专业图层">
          <option :value="-1">全部图层</option>
          <option v-for="(name, i) in layers" :key="i" :value="i">{{ name }}</option>
        </select>
      </label>
      <dl aria-live="polite">
        <template v-if="selectedIndex >= 0"><dt>选中图层</dt><dd class="picked">{{ layers[selectedIndex] }}</dd></template>
        <dt>模型 ID</dt><dd>{{ item.id }}</dd><dt>定位状态</dt><dd>已按 B2 底图配准</dd>
        <dt>来源图号</dt><dd>{{ sheetFilter || item.drawings }}</dd>
        <dt>建模依据</dt><dd>{{ item.basis }}</dd>
        <template v-if="notes"><dt>模型范围</dt><dd>{{ notes }}</dd></template>
      </dl>
      <button v-for="s in sheets" :key="s.id" @click="$emit('source',s.code)">核对原图 · {{ s.code }}</button>
    </aside>
  </div>
</template>
<script setup>
import { computed, ref, shallowRef, watch } from 'vue'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import FloorScene from './FloorScene.vue'
import {checkedEntry} from './b2Disciplines'
const props = defineProps({title:String, sheetFilter:String, item:Object, archive:Object, geometry:Object, sheets:Array, roomLabels:Array})
defineEmits(['source'])
const scene=shallowRef(null), layers=ref([]), notes=ref(''), error=ref(''), retry=ref(0)
const selection=ref({group:'discipline',index:-1})
const selectedIndex=computed({
  get:()=>selection.value.index,
  set:index=>{ selection.value={group:'discipline',index} }
})
watch([()=>props.item,()=>props.archive,()=>props.sheetFilter,retry], async ([item,archive],_,onCleanup)=>{
  let active=true
  const resources=new Set()
  onCleanup(()=>{ active=false; resources.forEach(r=>r.dispose()) })
  scene.value=null; layers.value=[]; notes.value=''; error.value=''; selectedIndex.value=-1
  try {
    const bytes=await checkedEntry(archive,item.file,item.sha256,8*1024*1024)
    if (!active) return
    const gltf=await new GLTFLoader().parseAsync(bytes,'')
    const names=[]
    gltf.scene.traverse(mesh=>{
      if (!mesh.isMesh) return
      resources.add(mesh.geometry)
      for (const material of [].concat(mesh.material)) resources.add(material)

    })
    if (!active) {resources.forEach(r=>r.dispose()); return}
    const modelRoot=gltf.scene.children[0]
    // One lighting GLB contains both plans. The fire view uses only E301.
    for (const branch of [...modelRoot.children]) {
      const sheet=props.sheets.find(s=>branch.name.startsWith(s.code))
      if (props.sheetFilter && sheet?.code !== props.sheetFilter) {
        modelRoot.remove(branch)
        continue
      }
      branch.traverse(mesh=>{
        if (mesh.isMesh) names.push([sheet?.code, mesh.userData.sourceLayer || mesh.name].filter(Boolean).join(' · '))
      })
    }
    if (!names.length) throw Error('专业模型没有可显示的图层')
    const root=modelRoot.userData
    notes.value=root.height || root.basis || ''
    layers.value=names; scene.value=gltf.scene
  } catch(e) { if (active) error.value=e.message || '模型读取失败' }
},{immediate:true})
</script>
<style scoped>
.system-layout {display:grid;grid-template-columns:minmax(0,1fr) clamp(200px,18%,270px);gap:18px}
aside {min-width:0;border-left:1px solid var(--dhic-border-color-light);padding:6px 0 0 16px;font-size:13px}
h3 {margin:0 0 12px} label {display:flex;align-items:center;gap:8px}select {max-width:80%}
dt {color:var(--dhic-text-secondary);font-size:12px}dd {margin:4px 0 12px;overflow-wrap:anywhere;line-height:1.55}
button,select {font:inherit;background:white;color:inherit;border:1px solid var(--dhic-border-color);border-radius:5px;padding:5px 8px;margin:0 4px 4px 0;cursor:pointer}
.picked {color:var(--selection-color);font-weight:650}.pending {padding:20px}
@container (max-width:800px) {.system-layout {grid-template-columns:1fr}aside {border:0;padding:0}}
</style>
