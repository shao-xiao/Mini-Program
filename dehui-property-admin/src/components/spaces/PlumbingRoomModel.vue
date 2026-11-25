<template>
  <div class="stations">
    <div class="stage-column">
      <div v-if="isPit" class="modes" aria-label="集水坑视图">
        <button type="button" :aria-pressed="!sectionOpen" @click="sectionOpen=false">设备位置与流向</button>
        <button type="button" :aria-pressed="sectionOpen" :disabled="!pitRecord" @click="sectionOpen=true">坑体剖切</button>
      </div>
      <template v-if="sectionOpen && pitRecord">
        <ObjectInspection v-if="sectionAsset" :key="pitRecord.sha256" :demo-name="pitRecord.name" :object="sectionAsset.scene" :clips="sectionAsset.animations" :floor-offset="0" />
        <div v-else-if="geometryError" role="alert">{{ geometryError }} <button @click="retry++">重试</button></div>
        <p v-else role="status">正在读取坑体剖切模型…</p>
      </template>
      <FloorScene v-else ref="scene" :core="core" :floor-elevation="floorElevation" :plumbing="model" :rooms="rooms" :key="filter"
        :geometry="geometry" :stations="visible" :selection="selection" @select="selectStation($event.index)" />
    </div>
    <aside aria-label="设备间与流路详情">
      <h3>{{ station.name }}</h3>
      <label>显示范围<select :value="filter" @change="changeFilter($event.target.value)">
        <option v-for="key in ['全部','处理间','集水坑']" :key="key">{{ key }}</option>
      </select></label>
      <label>设备位置<select :value="index" @change="selectStation(Number($event.target.value))"><option v-for="(s,i) in visible" :key="s.name" :value="i">{{ s.name }}</option></select></label>
      <h4>设备规格</h4>
      <dl><dt>设备</dt><dd>{{ station.machine }} {{ station.model || '' }}</dd>
        <dt>图注流量 · 扬程 · 功率</dt><dd>{{ station.performance }}</dd>
        <template v-if="station.duty"><dt>泵组配置</dt><dd>{{ station.duty }}</dd></template>
        <template v-for="[field,value] in station.roomFields" :key="field"><dt>{{ field }} · {{ station.roomSheet }}</dt><dd>{{ value }}</dd></template>
      </dl>
      <template v-if="isPit">
        <h4>坑体剖切 · P301 / P202</h4>
        <dl v-if="pitRecord"><template v-for="[field,value] in pitRecord.fields" :key="field"><dt>{{ field }}</dt><dd>{{ value }}</dd></template></dl>
        <p>{{ pitRecord?.basis || '已核坑底的实体图包尚未关联。本页可查看平面和设备运行示意。' }}</p>
      </template>
      <h4>设备与流向</h4>
      <ul v-if="station.kind==='处理间'">
        <li><b>排水</b>{{ station.machine }} → {{ station.outlet }} 压力排水立管</li>
        <li><b>冲洗</b>CJ 冲洗给水 → 减压型倒流防止器 → 房间冲洗用水点</li>
        <li><b>通气</b>设备通气支管 ↔ {{ station.vent }} 通气立管</li>
      </ul>
      <ol v-else><li>{{ station.connection.inlet || '来水支路待核' }}</li><li>{{ station.name }} · 潜水泵</li>
        <li>{{ station.outlet }} · DN{{ station.connection.dn }}</li><li>图示出口 {{ station.connection.outlet }}</li></ol>
      <div v-if="!sectionOpen" class="legend"><span v-for="(color,key) in stationMedia" :key="key"><i :style="{background:color}"></i>{{ key }}</span></div>
      <p v-if="!sectionOpen">紫色标出所选设备的管路。箭头指向出水侧，通气支管以往复气泡显示。设备外形、安装高度及动画速度为示意。</p>
      <button v-if="station.kind==='集水坑' || station.footprint" :disabled="exporting || (isPit && !pitRecord)" @click="downloadModule">导出{{ station.kind==='集水坑' ? '集水坑' : '设备间' }}模块 · GLB</button>
      <p v-if="exportError" role="alert">{{ exportError }}</p>
      <button @click="$emit('source','P301')">核对平面 · P301</button>
      <button v-if="station.kind==='集水坑'" @click="$emit('source','P202')">排水原理 · P202</button>
      <button v-else @click="$emit('source','P203')">冲洗原理 · P203</button>
      <button v-if="station.name==='JSK1-2'" @click="$emit('source','P501')">消防泵房大样 · P501</button>
      <button v-if="station.name==='JSK1-3'" @click="$emit('source','P502')">生活泵房大样 · P502</button>
    </aside>
  </div>
</template>
<script setup>
import {computed,ref,shallowRef,watch} from 'vue'
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js'
import ObjectInspection from './ObjectInspection.vue'
import {checkedEntry} from './b2Disciplines'
import {buildStations,stationMedia} from './plumbingStations'
import FloorScene from './FloorScene.vue'
const props=defineProps({core:Object, floorElevation:Number, geometry:Object,model:Object,rooms:Array,initialName:String,objects:Array,objectArchive:Object})
defineEmits(['source'])
const scene=ref(null),exporting=ref(false),exportError=ref('')
async function downloadModule(){
  exporting.value=true
  exportError.value=''
  try {
    if (!isPit.value) { await scene.value.exportSelectedStation(); return }
    const record=pitRecord.value
    if (!record) throw Error('请先关联已核坑深的实体图包')
    const bytes=await checkedEntry(props.objectArchive,record.file,record.sha256,8*1024*1024)
    const url=URL.createObjectURL(new Blob([bytes],{type:'model/gltf-binary'}))
    const link=document.createElement('a')
    link.href=url
    link.download=record.name+'.glb'
    link.click()
    setTimeout(()=>URL.revokeObjectURL(url),10000)
  }
  catch(error){exportError.value=error.message || '模块导出失败'}
  finally{exporting.value=false}
}
const stations=computed(()=>buildStations(props.model,props.rooms))
const filter=ref('全部')
const visible=computed(()=>stations.value.filter(s=>filter.value==='全部'||s.kind===filter.value))
const index=ref(Math.max(0,stations.value.findIndex(s=>s.name===props.initialName)))
const station=computed(()=>visible.value[index.value])
const selection=computed(()=>({group:'station',index:index.value}))
const isPit=computed(()=>station.value?.kind==='集水坑')
const pitRecord=computed(()=>isPit.value && props.objects?.find(r=>
  r.kind==='room' && r.verticalExtentVerified===true && r.name===station.value.name))
const sectionOpen=ref(false), sectionAsset=shallowRef(null), geometryError=ref(''), retry=ref(0)
watch(pitRecord,record=>{sectionOpen.value=Boolean(record);exportError.value=''}, {immediate:true})
watch([pitRecord,sectionOpen,()=>props.objectArchive,retry], async ([record,open,archive],_,onCleanup)=>{
  let current=true
  const resources=new Set()
  onCleanup(()=>{current=false;resources.forEach(r=>r.dispose())})
  sectionAsset.value=null
  geometryError.value=''
  if (!record || !open || !archive) return
  try {
    const bytes=await checkedEntry(archive,record.file,record.sha256,8*1024*1024)
    if (!current) return
    const result=await new GLTFLoader().parseAsync(bytes,'')
    result.scene.traverse(node=>{
      if (node.geometry) resources.add(node.geometry)
      if (node.material) [].concat(node.material).forEach(m=>resources.add(m))
    })
    if (current) sectionAsset.value=result
    else resources.forEach(r=>r.dispose())
  } catch (error) { if(current) geometryError.value=error.message || '坑体模型读取失败' }
})
function selectStation(i){index.value=i;sectionOpen.value=Boolean(pitRecord.value)}
function changeFilter(value){filter.value=value;selectStation(0)}
</script>
<style scoped>
.stations{display:grid;grid-template-columns:minmax(0,1fr) clamp(225px,20%,305px);gap:16px}
.stage-column{min-width:0}.modes{display:flex;gap:4px;margin-bottom:6px}.modes button[aria-pressed=true]{background:#eef1f2;font-weight:600}
aside{min-width:0;border-left:1px solid var(--dhic-border-color-light);padding:4px 0 0 14px;font-size:13px}
h3{font-size:15px;color:var(--selection-color);margin:0 0 12px}h4{margin:15px 0 8px;padding-top:8px;border-top:1px solid var(--dhic-border-color-light)}
label{display:flex;align-items:center;justify-content:space-between;margin:6px 0;gap:6px}
select,button{max-width:74%;font:inherit;color:inherit;background:white;border:1px solid var(--dhic-border-color);border-radius:5px;padding:5px;cursor:pointer}
button{margin:3px 5px 0 0}dt,p{font-size:12px;color:var(--dhic-text-secondary)}dd{margin:3px 0 9px}
li{margin:5px 0;line-height:1.55}ul{list-style:none;padding:0}ol{padding-left:22px}b{display:inline-block;margin-right:6px}
.legend{display:flex;gap:12px;margin:12px 0 0}.legend i{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:4px}
@container(max-width:800px){.stations{grid-template-columns:minmax(0,1fr)}aside{border:0;padding:0}}
</style>
