<template>
  <div class="system-layout">
    <ObjectInspection v-if="closeup && asset" :key="selectedAsset.id" :object="asset.scene" :clips="asset.animations" :demo-name="item.name!=='承重结构' && (selectedAsset.kind==='family' || selectedAsset.verticalExtentVerified) ? selectedAsset.name : undefined"
      :floor-offset="selectedAsset.kind==='installed' || selectedAsset.verticalExtentVerified ? -selectedAsset.origin[1] : undefined" />
    <FloorScene v-else-if="scene" ref="floorScene" :key="item.id" :geometry="geometry" :discipline-scene="scene" :placed-asset="running ? null : combined || placed"
      :system-only="item.name!=='承重结构'"
      :room-labels="roomLabels" :plan-segments="running ? [] : planSegments" :selected-plan-id="assetId" :selection="selection"
      @select="!running && (selection=$event)" @inspect-segment="assetId=$event;closeup=true" @inspect-instance="inspectInstance" />
    <div v-else class="pending" role="status">{{ error || '正在读取专业模型…' }} <button v-if="error" @click="retry++">重试</button></div>
    <aside aria-label="专业模型详情">
      <h3>{{ title }}</h3>
      <p class="purpose">{{ purposes[item.name] }}</p>
      <button v-if="steps.length && !running" @click="startRun">系统工况仿真</button>
      <SystemRun v-if="running && steps.length" :steps="steps" @phase="selectedIndex=$event" @object="id=>{stopRun();assetId=id}" @close="stopRun"/>
      <section v-if="available.length && !running" class="inspect">
        <button v-if="installations.length || simRooms.length" @click="showInstallations" :disabled="combining">{{ installations.length ? "查看已核标高 · "+installations.length+" 件" : "查看设备间" }}</button>
        <div v-if="combined && nearbyObjects.length" class="local-views">
          <button v-for="r in nearbyObjects" :key="r.id" @click="inspectInstance(r.id)">{{ r.name }}</button>
        </div>
        <section v-if="combined && focusedRecord" aria-label="楼层中选中的构件" class="focused">
          <h4>{{ focusedRecord.name }}</h4>
          <dl><template v-for="[name,value] in focusedRecord.fields" :key="name">
            <dt>{{ name }}</dt><dd>{{ value }}</dd>
          </template><dt>来源图号</dt><dd>{{ focusedRecord.drawings }}</dd></dl>
          <button @click="assetId=focusedRecord.id">单独查看构件</button>
        </section>
        <p v-if="combining" role="status">正在装配本层已核标高的构件…</p>
        <p v-if="combinedError" role="alert">{{ combinedError }}</p>
        <label>查看对象<select v-model="assetId" aria-label="查看实体对象">
          <option value="">楼层专业图</option>
          <optgroup v-for="group in objectGroups" :key="group.kind" :label="group.label">
            <option v-for="record in group.items" :key="record.id" :value="record.id">{{ record.name }}</option>
          </optgroup>
        </select></label>
        <p v-if="assetError" role="alert">{{ assetError }} <button @click="assetRetry++">重试</button></p>
        <p v-else-if="assetId && !asset" role="status">正在读取实体 GLB…</p>
        <template v-if="asset && selectedAsset">
          <div><button :aria-pressed="closeup" @click="closeup=true">实体近看</button>
            <button :aria-pressed="!closeup" @click="closeup=false">返回楼层</button>
          </div>
          <p>{{ placementNote }}</p>
          <dl><dt>实体 GLB</dt><dd>{{ selectedAsset.id }} · {{ selectedAsset.name }}</dd>
            <template v-if="selectedAsset.kind==='segment' && selectedAsset.placement">
              <dt>截面（宽 × 高）</dt><dd>{{ selectedAsset.placement.nominalSizeMm.join(' × ') }} mm</dd>
              <dt>图面直段长</dt><dd>约 {{ selectedAsset.placement.lengthM.toFixed(3) }} m</dd>
              <dt>图注安装要求</dt><dd>{{ selectedAsset.placement.relativeMount }}</dd>
              <dt>安装标高</dt><dd>待核 · 支承梁底标高及测量基点待确定</dd>
            </template>
            <template v-for="[label,value] in selectedAsset.fields" :key="label"><dt>{{ label }}</dt><dd>{{ value }}</dd></template>
            <dt>图纸依据</dt><dd>{{ selectedAsset.drawings }}</dd><dt>模型表达</dt><dd>{{ selectedAsset.basis }}</dd>
          </dl>
        </template>
      </section>
      <label v-if="layers.length && !closeup && !running">专业图层
        <select v-model.number="selectedIndex" aria-label="专业图层">
          <option :value="-1">全部图层</option>
          <option v-for="(name, i) in layers" :key="i" :value="i">{{ name }}</option>
        </select>
      </label>
      <dl v-if="!closeup && !(combined && focusedRecord)" aria-live="polite">
        <template v-if="selectedIndex >= 0"><dt>选中图层</dt><dd class="picked">{{ layers[selectedIndex] }}</dd></template>
        <dt>模型 ID</dt><dd>{{ item.id }}</dd><dt>专业图定位</dt><dd>已按 B2 底图配准</dd>
        <dt>来源图号</dt><dd>{{ sheetFilter || item.drawings }}</dd>
        <dt>平面图依据</dt><dd>{{ item.basis }}</dd>
        <template v-if="notes"><dt>模型范围</dt><dd>{{ notes }}</dd></template>
      </dl>
      <button v-for="code in sourceCodes" :key="code" @click="$emit('source',code)">核对原图 · {{ code }}</button>
    </aside>
  </div>
</template>
<script setup>
import { computed, nextTick, ref, shallowRef, watch } from 'vue'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { BufferGeometry, Group, Line, LineBasicMaterial, Vector3 } from 'three'
import JSZip from 'jszip'
import FloorScene from './FloorScene.vue'
import SystemRun from './SystemRun.vue'
import {systemRuns} from './systemRuns'
import ObjectInspection from './ObjectInspection.vue'
import {checkedEntry, purposes} from './b2Disciplines'
const props = defineProps({title:String, sheetFilter:String, item:Object, objects:Array, installationPacks:Object, objectArchive:Object, archive:Object, geometry:Object, sheets:Array, roomLabels:Array})
defineEmits(['source'])
const scene=shallowRef(null), layers=ref([]), notes=ref(''), error=ref(''), retry=ref(0)
const assetId=ref(''), closeup=ref(false), assetRetry=ref(0), assetError=ref('')
const asset=shallowRef(null), placed=shallowRef(null)
const floorScene=ref(null)
const available=computed(()=>props.objects.filter(r=>r.views.includes(props.item.name) &&
  (!props.sheetFilter || !r.sheetCodes || r.sheetCodes.includes(props.sheetFilter))))
const planSegments=computed(()=>available.value.filter(r=>r.placement?.endpointsXZ?.length===2 || r.planLine?.length>=2))
const objectGroups=computed(()=>[['installed','图纸已核安装位置'],['segment','图注尺寸管段'],['room','楼层已定位空间'],['family','构件参考样件']].map(([kind,label])=>({
  kind,label,items:available.value.filter(r=>r.kind===kind)
})).filter(g=>g.items.length))
const running=ref(false)
const steps=computed(()=>{
  const profile=systemRuns[props.sheetFilter==='E301'?'应急照明':props.item.name] || []
  return profile.flatMap(([title,name,objectName])=>{
    const index=layers.value.findIndex(layer=>layer===name || layer.endsWith(' · '+name))
    const record=available.value.find(r=>r.name===objectName)
    return index<0 ? [] : [{title,index,layer:layers.value[index],objectName,object:record?.id}]
  })
})
async function startRun(){
  running.value=true;closeup.value=false;assetId.value=''
  await nextTick()
  floorScene.value?.showPlan()
}
function stopRun(){running.value=false;selectedIndex.value=-1}
watch([()=>props.item.id,()=>props.sheetFilter],()=>{running.value=false})
const selectedAsset=computed(()=>available.value.find(r=>r.id===assetId.value))
const installations=computed(()=>available.value.filter(r=>r.kind==='installed' || r.verticalExtentVerified))
const combineRequest=ref(0),combined=shallowRef(null),combining=ref(false),combinedError=ref('')
const contextRooms=computed(()=>available.value.filter(r=>r.kind==='room' && r.origin && !r.verticalExtentVerified))
const nearbyObjects=computed(()=>contextRooms.value.filter(r=>/机房|强电间/.test(r.name)))
const simRooms=computed(()=>props.item.name==='承重结构' ? [] : contextRooms.value)
watch([scene,()=>props.objectArchive,()=>installations.value.length || simRooms.value.length],([drawing,archive,count])=>{
  if (drawing && archive && count && !assetId.value) showInstallations()
},{flush:'post'})
const focusedId=ref('')
const focusedRecord=computed(()=>available.value.find(r=>r.id===focusedId.value))
function inspectInstance(id) {
  if (!combined.value) {assetId.value=id;return}
  focusedId.value=id
  floorScene.value?.focusInstance(id)
}
async function showInstallations() {
  assetId.value=''
  closeup.value=false
  if (combined.value) { focusedId.value=''; floorScene.value?.showAsset(); return }
  combineRequest.value++
}
defineExpose({showInstallations})
watch([combineRequest,()=>props.item.id,assetId,()=>props.objectArchive], async ([request,id,selected,archive],old,onCleanup)=>{
  let current=true
  const resources=new Set()
  onCleanup(()=>{current=false;resources.forEach(r=>r.dispose())})
  combined.value=null; combining.value=false; combinedError.value=''
  if (!request || selected || !archive || request===old[0]) return
  combining.value=true
  try {
    const group=new Group()
    const bundle=props.installationPacks?.[props.item.name]
    const files=bundle ? await JSZip.loadAsync(await checkedEntry(archive,bundle.file,bundle.sha256,8*1024*1024)) : archive
    for (const record of [...installations.value,...contextRooms.value]) {
      if (!current) return
      const source=record.kind==='room' && !record.verticalExtentVerified ? archive : files
      const bytes=await checkedEntry(source,record.file,record.sha256,8*1024*1024)
      if (!current) return
      const {scene,animations}=await new GLTFLoader().parseAsync(bytes,'')
      scene.animations = props.item.name === '承重结构' ? [] : animations
      scene.traverse(node=>{
        if (node.geometry) resources.add(node.geometry)
        if (node.material) [].concat(node.material).forEach(m=>resources.add(m))
      })
      if (!current) {resources.forEach(r=>r.dispose());return}
      scene.userData.instanceId=record.id
      scene.position.fromArray(record.origin)
      if (record.planLine) {
        // Keep the documented lower edge pickable on a whole-floor display.
        const geometry=new BufferGeometry().setFromPoints(record.planLine.map(([x,z])=>
          new Vector3(x-record.origin[0],0,z-record.origin[2])))
        const material=new LineBasicMaterial({color:0x536e79})
        resources.add(geometry);resources.add(material)
        scene.add(new Line(geometry,material))
      }
      group.add(scene)
    }
    combined.value=group
    await nextTick()
    if (current && !running.value) {
      floorScene.value?.showAsset()
      const animated=group.children.find(child=>child.animations.length)
      const first=available.value.find(record=>animated && record.id===animated.userData.instanceId) || installations.value[0]
      if (first) inspectInstance(first.id)
    }
  } catch (e) {if (current) combinedError.value=e.message || '已核标高模型装配失败'}
  finally {if (current) combining.value=false}
})
const sourceCodes=computed(()=>[...new Set([...((selectedAsset.value || (combined.value && focusedRecord.value))?.sourceCodes || []),...props.sheets.map(s=>s.code)])])
const placementNote=computed(()=>{
  if (selectedAsset.value?.kind==='installed') return '可在楼层中查看本件的安装位置。标高的基准和图注见下方。'
  if (selectedAsset.value?.kind==='family') return '可旋转查看目录原型。对应的现场实例和安装位置待核。'
  if (selectedAsset.value?.kind==='segment') return '可近看已核的管段几何。楼层图保留平面走向，立体安装位置待核。'
  if (selectedAsset.value?.verticalExtentVerified) return '坑口放在本处完成地面，坑体向下至图注坑底。控制水位线可与 P202 对照。'
  return '按楼层索引定位房间。房内设备外形及安装依据请见各模块说明。'
})
watch(()=>props.item.id,()=>{assetId.value='';closeup.value=false})
watch([selectedAsset,()=>props.objectArchive,assetRetry],async ([record,archive],_,onCleanup)=>{
  let current=true
  const resources=new Set()
  onCleanup(()=>{current=false;resources.forEach(r=>r.dispose())})
  asset.value=null;placed.value=null;assetError.value='';closeup.value=false
  if(!record || !archive) return
  try {
    const bytes=await checkedEntry(archive,record.file,record.sha256,8*1024*1024)
    if(!current) return
    const result=await new GLTFLoader().parseAsync(bytes,'')
    result.scene.traverse(node=>{
      if (node.geometry) resources.add(node.geometry)
      if (node.material) [].concat(node.material).forEach(m=>resources.add(m))
    })
    if(!current) {resources.forEach(r=>r.dispose());return}
    if(record.origin) {
      const instance=result.scene.clone(true)
      instance.traverse(node=>{
        if (node.userData.asset?.['安装标高']?.includes('待核')) node.visible=false
      })
      instance.position.fromArray(record.origin)
      placed.value=instance
    }
    asset.value=result;closeup.value=true
  } catch(e){ if(current) assetError.value=e.message || '实体模型读取失败' }
},{immediate:true})
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
.focused{margin:9px 0;padding:10px;background:#f3f6f5;border-radius:5px}.focused h4{margin:0 0 8px}.focused dd{margin-bottom:7px}

.system-layout {display:grid;grid-template-columns:minmax(0,1fr) clamp(200px,18%,270px);gap:18px}
aside {min-width:0;border-left:1px solid var(--dhic-border-color-light);padding:6px 0 0 16px;font-size:13px}
h3 {margin:0 0 12px} label {display:flex;align-items:center;gap:8px}select {max-width:80%}
dt {color:var(--dhic-text-secondary);font-size:12px}dd {margin:4px 0 12px;overflow-wrap:anywhere;line-height:1.55}
button,select {font:inherit;background:white;color:inherit;border:1px solid var(--dhic-border-color);border-radius:5px;padding:5px 8px;margin:0 4px 4px 0;cursor:pointer}
.purpose,.inspect p{line-height:1.55;color:var(--dhic-text-secondary)}.inspect{padding-bottom:8px;margin-bottom:12px;border-bottom:1px solid var(--dhic-border-color-light)}.inspect label{margin-bottom:8px}.inspect button[aria-pressed=true]{font-weight:650;background:var(--dhic-row-selected)}
.picked {color:var(--selection-color);font-weight:650}.pending {padding:20px}
@container (max-width:800px) {.system-layout {grid-template-columns:1fr}aside {border:0;padding:0}}
</style>
