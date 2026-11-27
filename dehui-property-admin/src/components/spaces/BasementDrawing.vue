<template>
  <section ref="drawing" class="drawing" :style="{'--selection-color':selectionColor}" aria-label="B2 楼层图模">
    <nav aria-label="图纸专业">
      <button v-for="name in tabs" :key="name" type="button" :aria-pressed="tab === name" @click="changeTab(name)">{{ name }}</button>
    </nav>
    <iframe v-if="['暖通','强电','消防','弱电'].includes(tab)" :key="tab" :title="'B2 '+tab+'三维模型'" :src="'/b2-systems-v2/viewer.html?tab='+encodeURIComponent(tab)" allowfullscreen style="display:block;width:100%;height:84vh;min-height:540px;border:0" />
    <p v-else-if="busy" role="status">正在读取楼层图模…</p>
    <div v-else-if="!model" class="blank">
      <strong>{{ message || '尚未关联竣工图模' }}</strong>
      <button type="button" @click="load">重新读取</button>
      <label v-if="canAttach">关联竣工图包<input type="file" accept=".zip" @change="attach"></label>
    </div>
    <template v-else>
      <header><h3>{{ heading }}</h3>
        <div v-if="catalogChoices.length && !original" class="scope" aria-label="专业模型分类">
          <button v-for="item in catalogChoices" :key="item.id" :aria-pressed="activeModel?.id === item.id" @click="catalogName=item.name">{{ modelTitle(item) }}</button>
        </div>
        <div v-if="activeModel" class="views">
          <button v-if="!original && fullscreenAvailable" @click="toggleFull">{{ full ? '退出全屏' : '全屏' }}</button>
          <button v-if="catalogSheets.length" @click="original=!original">{{ original ? '返回专业模型' : '核对专业原图' }}</button>
        </div>
        <div v-if="tab === '给排水' || tab === '消防'" class="views">
          <template v-if="plumbing">
            <div v-if="!original" class="scope" aria-label="水系统仿真">
              <button v-if="tab==='给排水' && hasStationSurvey(plumbing)" :aria-pressed="!activeModel && waterSystem==='stations'" @click="openStation()">设备间与流向</button>
              <button v-for="(info,key) in tabWaterSystems" :key="key" :aria-pressed="!activeModel && waterSystem===key" @click="showWater(key)">{{ tab==='消防' ? info.name+'演示' : info.name }}</button>
            </div>
            <button v-if="waterView && !original && fullscreenAvailable" @click="toggleFull">{{ full ? '退出全屏' : '全屏' }}</button>
            <label v-if="tab==='给排水' && !loadingDetails && canAttach && plumbing.version < 2" class="upload">关联深化图包<input type="file" accept=".zip" @change="attachDetail($event,'plumbing')"></label>
            <button v-if="waterView" @click="toggleWaterSource">{{ original ? '返回' + tab + '模型' : '核对专业原图' }}</button>
          </template>
          <label v-else-if="tab==='给排水' && !loadingDetails && canAttach" class="upload">关联给排水图包<input type="file" accept=".zip" @change="attachDetail($event,'plumbing')"></label>
        </div>
        <div v-if="tab === '平面图'" class="views">
          <div v-if="(core || garage || disciplines) && !original" class="scope" aria-label="模型范围">
            <button v-if="garage" :aria-pressed="!activeModel && mode === 'traffic'" @click="scope('traffic')">车位与车道</button>
            <button :aria-pressed="!activeModel && mode === 'building'" @click="scope('building')">建筑</button>
            <button v-if="core" :aria-pressed="!activeModel && detail" @click="scope('core')">核心筒</button>
            <span v-if="disciplines" class="unbound">装修 · 待关联</span>
          </div>
          <label v-if="!loadingDetails && canAttach && !core && !coreError" class="upload">关联核心筒详图<input type="file" accept=".zip" @change="attachDetail($event,'core')"></label>
          <label v-if="!loadingDetails && canAttach && !garage && !coreError" class="upload">关联车库详图<input type="file" accept=".zip" @change="attachDetail($event,'garage')"></label>
          <label v-if="!loadingDetails && canAttach && core && !survey" class="upload">关联空间规格<input type="file" accept=".zip" @change="attachDetail($event,'survey')"></label>
          <button v-if="!activeModel && !original && fullscreenAvailable" @click="toggleFull">{{ full ? '退出全屏' : '全屏' }}</button>
          <button v-if="!activeModel && tab === '平面图'" type="button" @click="original = !original">{{ original ? '返回三维模型' : '核对建筑原图' }}</button>
        </div>
        <label v-if="!loadingDetails && !disciplines && canAttach" class="upload">关联 B2 专业模型<input type="file" accept=".zip" @change="attachDetail($event,'disciplines')"></label>
        <label v-if="disciplines && !loadingDetails && (!objects || objects.version < objectPackVersion) && canAttach" class="upload">{{ objects ? '更新 B2 实体图包' : '关联 B2 实体图包' }}<input type="file" accept=".zip" @change="attachDetail($event,'objects')"></label>
      </header>
      <small v-if="loadingDetails" role="status">正在载入专业详图…</small>
      <p v-if="message || coreError" role="alert">{{ message || coreError }}</p>
      <DisciplineModel :key="activeModel.id + tab" :profession="tab" :plumbing="plumbing" :core="core" :installation-packs="objects?.installationPacks" v-if="activeModel" v-show="!original" :objects="objects?.objects || []" :object-archive="objectArchive" :item="activeModel" :title="modelTitle(activeModel)" :sheet-filter="emergencyOnly ? 'E301' : ''" :archive="disciplineArchive" :geometry="model.geometry" :room-labels="survey?.rooms || garage?.rooms || []" :sheets="catalogSheets" @source="code=>{sourceCode=code;original=true}" />
      <template v-if="!activeModel && tab === '平面图' && !original">
        <div class="model-workbench">
        <FloorScene ref="floorScene" :floor-elevation="model.elevation" :plumbing="plumbing" :key="mode + (loadingDetails ? '-base' : '')" :rooms="visibleRooms" :garage="garage" :traffic="mode === 'traffic'" :core="core" :detail="detail" :geometry="model.geometry" :selection="selection" @select="selection = $event" />
        <aside class="properties" :class="{'picked':selection.group !== 'outline'}" aria-label="模型规格">
          <h3>{{ detail ? '核心筒规格' : '构件规格' }}</h3>
          <div class="pickers">
            <label>构件类型<select aria-label="构件类型" :value="selection.group" @change="selection = { group: $event.target.value, index: 0 }">
              <option v-for="(label,key) in labels" :key="key" :value="key">{{ label }}</option>
            </select></label>
            <label v-if="paths?.length && selection.group !== 'outline'">图元<select aria-label="图元" :value="selection.index" @change="selection = { ...selection, index: Number($event.target.value) }">
              <option v-for="(_,i) in paths" :key="i" :value="i">{{ itemName(selection.group,i) }}</option>
            </select></label>
          </div>
          <dl aria-live="polite"><div v-for="[label,val] in specs" :key="label" :class="{'selected-value':selection.group !== 'outline' && !commonSpecs.includes(label)}"><dt>{{ label }}</dt><dd>{{ val }}</dd></div></dl>
          <button v-if="selection.group==='rooms'" type="button" :disabled="exporting" @click="downloadRoom">导出房间模块 · GLB</button>
          <button v-if="selectedSheet" type="button" @click="sourceCode = selectedSheet; original = true">核对所选图纸 · {{ selectedSheet }}</button>
          <p v-if="['lanes','parking','crossings'].includes(selection.group) || (mode === 'traffic' && selection.group === 'outline')" class="note">编号及方向取自交通竣工图第 5 页，分区取自第 6 页。车位和车道按扫描图定位。</p>
          <p v-else-if="detail && selection.group !== 'rooms'" class="note">A401 · 1:50。{{ core.columns.length }} 个柱截面与 A101 逐角配准，最大偏差 {{ core.maxErrorMm }} mm。墙柱显示至地坪以上 1.20 m。</p>
          <p v-else-if="selection.group !== 'rooms'" class="note">图面按同一米制比例重建。墙柱在地坪以上 1.20 m 处剖切，楼梯与内隔墙未核实的高度仅显示平面线稿。图元序号仅用于本图定位。</p>
        </aside>
        </div>
      </template>
      <PlumbingRoomModel :objects="objects?.objects || []" :object-archive="objectArchive" :core="core" :floor-elevation="model.elevation" v-if="waterView && waterSystem === 'stations' && hasStationSurvey(plumbing)" v-show="!original" :initial-name="stationName" :rooms="survey?.rooms || garage?.rooms || []" :model="plumbing" :geometry="model.geometry" @source="code=>{sourceCode=code;original=true}" />
      <PlumbingModel :objects="objects?.objects" :object-archive="objectArchive" :core="core" :floor-elevation="model.elevation" @station="openStation" v-else-if="waterView" v-show="!original" :key="waterSystem" :rooms="survey?.rooms || garage?.rooms || []" :model="plumbing" :geometry="model.geometry" :system="waterSystem" @source="code=>{sourceCode=code;original=true}"/>
      <table v-else-if="!activeModel && !original && tab !== '平面图'" class="drawing-index">
        <thead><tr><th>图号</th><th>图名</th><th>核对状态</th></tr></thead>
        <tbody><tr v-for="s in sources" :key="s.code"><td>{{ s.code }}</td><td>{{ sheetTitle(s) }}</td><td>已登记图纸 · 待关联专业模型</td></tr>
          <tr v-if="!sources.length"><td colspan="3">待关联 B2 专业模型图包</td></tr></tbody>
      </table>
      <div v-if="original" class="sources">
        <label>图纸<select v-model="sourceCode"><option v-for="s in sources" :key="s.code" :value="s.code">{{ s.code }} · {{ sheetTitle(s) }}</option></select></label>
        <p v-if="sourceError" role="status">{{ sourceError }}</p>
        <template v-else-if="pdfUrl">
          <a :href="pdfUrl" target="_blank" rel="noopener">在新窗口核对原图 ↗</a>
          <object :key="pdfUrl" :data="pdfUrl" type="application/pdf" :aria-label="source?.name">请在新窗口查看 PDF。</object>
        </template>
        <p v-else role="status">{{ source?.file ? '正在校验原图…' : '已核对图号和文件名称，原图尚未关联。' }}</p>
        <small v-if="source">{{ source.name }}</small>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, ref, shallowRef, watch, onMounted, onBeforeUnmount, defineAsyncComponent } from 'vue'
import { objectSpecs } from './floorDrawingSpecs'
import { selectionColor } from './garageMarkings'
import { hasPermission } from '../../utils/permission'
import { readFloorArchive, attachFloorArchive, attachSupplement, openSource } from './floorDrawingArchive'
import { disciplineModels, objectPackVersion } from './b2Disciplines'
import { waterSystems } from './plumbingCatalog'
import { buildStations, hasStationSurvey } from './plumbingStations'
const PlumbingRoomModel = defineAsyncComponent(() => import('./PlumbingRoomModel.vue'))
const DisciplineModel = defineAsyncComponent(() => import('./DisciplineModel.vue'))
const stationName = ref('')
function openStation(name = '') {
  tab.value = '给排水'
  catalogName.value = ''
  stationName.value = name
  waterSystem.value = 'stations'
}
const PlumbingModel = defineAsyncComponent(() => import('./PlumbingModel.vue'))
const FloorScene = defineAsyncComponent(() => import('./FloorScene.vue'))
const props = defineProps({ floorId: { type: Number, required: true }, ledgerArea: [String, Number] })
const tabs = Object.keys(disciplineModels)
const floorScene=ref(null), exporting=ref(false)
async function downloadRoom() {
  exporting.value=true
  try { await floorScene.value.exportSelectedRoom() }
  catch (error) { message.value='房间模块导出失败：'+error.message }
  finally { exporting.value=false }
}
const baseLabels = { outline:'楼层轮廓', bays:'车位', columns:'柱', walls:'实填墙段' }
const core = shallowRef(null), coreArchive = shallowRef(null), mode = ref('building'), coreError = ref('')
const garage = shallowRef(null), garageArchive = shallowRef(null)
const plumbing = shallowRef(null), plumbingArchive = shallowRef(null), waterSystem = ref('drain')
const availableWaterSystems = computed(() => Object.fromEntries(Object.entries(waterSystems).filter(([key]) =>
  plumbing.value?.items.some(item => item.system === key))))
const objects = shallowRef(null), objectArchive = shallowRef(null)
const disciplines = shallowRef(null), disciplineArchive = shallowRef(null), catalogName = ref('')
const catalogChoices = computed(()=>disciplineModels[tab.value].map(name=>disciplines.value?.models.find(m=>m.name===name)).filter(Boolean))
const activeModel = computed(()=>catalogChoices.value.find(m=>m.name===catalogName.value))
function modelTitle(item) {
  if (tab.value === '消防' && item.name === '室内照明与应急') return '应急照明'
  return item.name === '给排水' ? '给排水综合图' : item.name
}
const emergencyOnly = computed(() => tab.value === '消防' && activeModel.value?.name === '室内照明与应急')
const catalogSheets = computed(() => {
  const item = activeModel.value
  if (!item) return []
  const sheets = item.sources.map(id => disciplines.value.sources.find(s => s.id === id)).filter(Boolean)
  if (emergencyOnly.value) return sheets.filter(s => s.code === 'E301')
  if (item.name === '消火栓') sheets.sort((a,b) => Number(b.code === 'P301') - Number(a.code === 'P301'))
  return sheets
})
const tabWaterSystems = computed(()=>Object.fromEntries(Object.entries(availableWaterSystems.value).filter(([key])=>
  ['sprinkler','hydrant'].includes(key) === (tab.value==='消防'))))
const waterView = computed(()=>!activeModel.value && plumbing.value && (tab.value==='给排水' || tab.value==='消防') &&
  (tabWaterSystems.value[waterSystem.value] || (tab.value==='给排水' && waterSystem.value==='stations')))
function showWater(key) { catalogName.value=''; waterSystem.value=key }
function toggleWaterSource() {
  if (!original.value) sourceCode.value = waterSystems[waterSystem.value]?.plan || 'P301'
  original.value = !original.value
}
const survey = shallowRef(null)
const visibleRooms = computed(() => {
  const all = survey.value?.rooms || garage.value?.rooms || []
  return detail.value ? all.filter(room => room.inCore) : all
})
const detail = computed(() => mode.value === 'core')
const commonSpecs = ['本层地坪标高','B2 至 B1 层高','尺寸依据','标注依据']
const drawing = ref(null), full = ref(false)
const fullscreenAvailable = typeof document !== 'undefined' && document.fullscreenEnabled
function syncFullscreen() { full.value = document.fullscreenElement === drawing.value }
async function toggleFull() {
  try { if (full.value) await document.exitFullscreen(); else await drawing.value.requestFullscreen() }
  catch { message.value = '浏览器未能进入全屏' }
}
onMounted(() => document.addEventListener('fullscreenchange',syncFullscreen))
onBeforeUnmount(() => document.removeEventListener('fullscreenchange',syncFullscreen))
function itemName(group,i) {
  const record = group === 'rooms' ? visibleRooms.value[i] : garage.value?.[group]?.[i]
  if (group === 'crossings') return '人行横道 '+(i+1)
  return record ? (group === 'parking' ? record.zone + '区 · ' + record.name : record.name) : labels.value[group]+' '+(i+1)
}
const labels = computed(() => detail.value ? {coreOverview:'核心筒总览', ...(visibleRooms.value.length ? {rooms:'建筑用房'} : {}), coreWalls:'核心筒墙段', coreColumns:'核心筒柱'} :
  {...(mode.value === 'traffic' ? {outline:'楼层概览',parking:'车位',lanes:'车道',...(garage.value?.crossings?.length ? {crossings:'人行横道'} : {}),columns:'柱',walls:'实填墙段'} : baseLabels), ...(visibleRooms.value.length ? {rooms:'建筑用房'} : {}), ...(core.value ? {coreWalls:'核心筒墙段'} : {})})
const paths = computed(() => {
  const group = selection.value.group
  if (group === 'coreOverview') return []
  if (group === 'crossings') return garage.value.crossings
  if (group === 'rooms') return visibleRooms.value.map(r => r.points)
  if (group === 'coreWalls') return core.value.walls
  if (group === 'coreColumns') return core.value.columns
  if (garage.value?.[group]) return garage.value[group].map(item => item.points)
  return model.value?.geometry[group]
})
function scope(value) {
  catalogName.value = ''
  mode.value = value
  selection.value = { group:value === 'core' ? 'coreOverview' : 'outline', index:0 }
}
const canAttach = hasPermission('floor:update')
const tab = ref(tabs[0]), original = ref(false), selection = ref({ group:'outline', index:0 })
const heading = computed(() => {
  if (tab.value === '平面图') return activeModel.value ? '地下二层 · 承重结构' : '地下二层 · 建筑模型'
  return activeModel.value || waterView.value ? '地下二层 · ' + tab.value + '模型' : tab.value + '竣工图'
})
const loadingDetails = ref(false)
const model = shallowRef(null), archive = shallowRef(null), busy = ref(false), message = ref('')
const sourceCode = ref(''), pdfUrl = ref(''), sourceError = ref('')
let epoch = 0, pdfEpoch = 0
const buildingSources = computed(() => [...(model.value?.sources || []), ...(core.value ? [core.value.source] : []), ...(garage.value ? [garage.value.source] : [])])
const modelSheets = computed(() => {
  const sheets = [...catalogSheets.value]
  const available = [...(disciplines.value?.sources || []), ...(objects.value?.sources || [])]
  for (const record of objects.value?.objects || []) {
    if (!record.views.includes(activeModel.value?.name) ||
        (emergencyOnly.value && record.sheetCodes && !record.sheetCodes.includes('E301'))) continue
    for (const code of record.sourceCodes || []) {
      const sheet = available.find(s => s.code === code)
      if (sheet && !sheets.some(s => s.code === code)) sheets.push(sheet)
    }
  }
  return sheets
})
const sources = computed(() => activeModel.value ? modelSheets.value : waterView.value ? plumbing.value.sources : buildingSources.value.filter(s => tab.value === '平面图' ? ['建筑','剖面'].includes(s.group) : s.group === tab.value) || [])
const source = computed(() => sources.value.find(s => s.code === sourceCode.value))
function changeTab(name) {
  tab.value = name
  original.value = false
  catalogName.value = ['平面图','给排水'].includes(name) ? '' : disciplineModels[name][0]
  if (name==='消防') waterSystem.value=availableWaterSystems.value.sprinkler ? 'sprinkler' : 'hydrant'
  if (name==='给排水') {
    waterSystem.value='drain'
    if (!plumbing.value) catalogName.value='给排水'
  }
}
function sheetTitle(s) { if (s.code === 'P302') return '地下一层给排水消防平面图（跨层对照）'; return s.name.replace(/\.pdf$/i, '') }
function clearPdf() { if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value); pdfUrl.value = '' }
watch(sources, list => { sourceCode.value = list[0]?.code || '' }, { immediate:true })
watch([source, original, tab, archive], async () => {
  const run = ++pdfEpoch
  clearPdf(); sourceError.value = ''
  if (!source.value?.file || !archive.value || !original.value) return
  try {
    const modelArchive = objects.value?.sources?.includes(source.value) ? objectArchive.value : disciplineArchive.value
    const url = await openSource(activeModel.value ? modelArchive : waterView.value ? plumbingArchive.value : source.value.code === 'A401' ? coreArchive.value : source.value.code === 'B2交通' ? garageArchive.value : archive.value, source.value)
    if (run === pdfEpoch) pdfUrl.value = url
    else URL.revokeObjectURL(url)
  } catch (e) { if (run === pdfEpoch) sourceError.value = e.message }
})
async function load() {
  const run = ++epoch
  objects.value=null; objectArchive.value=null
  disciplines.value=null; disciplineArchive.value=null
  let preview
  loadingDetails.value = false; survey.value = null
  waterSystem.value = 'drain'; plumbing.value = null; plumbingArchive.value = null
  busy.value = true; message.value = ''; model.value = null; archive.value = null; core.value = null; coreArchive.value = null; coreError.value = ''; garage.value = null; garageArchive.value = null; scope('building')
  try {
    const data = await readFloorArchive(props.floorId, base => {
      if (run !== epoch) return
      preview = setTimeout(() => {
        if (run !== epoch) return
        archive.value = base.archive
        model.value = base.model
        busy.value = false
        loadingDetails.value = true
      }, 200)
    })
    if (data && run === epoch) {
      plumbing.value = data.plumbing
      plumbingArchive.value = data.plumbingArchive
      archive.value = data.archive
      model.value = data.model
      core.value = data.core
      coreArchive.value = data.coreArchive
      coreError.value = data.coreError
      survey.value = data.survey
      garage.value = data.garage
      garageArchive.value = data.garageArchive
      if (garage.value) scope('traffic')
      objects.value = data.objects
      objectArchive.value = data.objectsArchive
      disciplines.value = data.disciplines
      disciplineArchive.value = data.disciplinesArchive
      changeTab(tab.value)
    }
  } catch (e) { if (run === epoch) message.value = e.message || '读取失败' }
  finally { clearTimeout(preview); if (run === epoch) { busy.value = false; loadingDetails.value = false } }
}
async function attach(event) {
  const file = event.target.files[0]
  if (!file) return
  busy.value = true
  try { await attachFloorArchive(props.floorId, file); await load() }
  catch (e) { message.value = e.message || '关联失败'; busy.value = false }
}
async function attachDetail(event,kind) {
  const file = event.target.files[0]
  if (!file) return
  busy.value = true
  try { await attachSupplement(props.floorId,file,{...model.value,sources:buildingSources.value},kind); await load(); if (kind === 'core' && core.value) scope('core') }
  catch (e) { message.value = e.message || '关联失败'; busy.value = false }
}
watch(() => props.floorId, load, { immediate:true })
onBeforeUnmount(() => { epoch++; pdfEpoch++; clearPdf() })
const liftStations=computed(()=>buildStations(plumbing.value).filter(s=>s.kind==='处理间'))
const specs = computed(() => {
  const m = model.value
  if (!m) return []
  const {group,index} = selection.value
  const base = [['本层地坪标高', m.elevation.toFixed(3) + ' m'],['B2 至 B1 层高', (m.upperElevation - m.elevation).toFixed(3) + ' m']]
  if (group === 'outline') return [['竣工图面积',m.area + ' ㎡'],...(props.ledgerArea != null ? [['台账登记面积',props.ledgerArea + ' ㎡']] : []),['外包尺寸',m.span.map(n => n.toFixed(2)).join(' × ') + ' m'],['A101 建筑图注',m.geometry.bays.length + ' 辆'],...(garage.value ? [['交通竣工图',garage.value.parking.length+' 个一般小型车位'],['分区数量',['C','D'].map(z=>z+'区 '+garage.value.parking.filter(b=>b.zone===z).length+' 个').join(' · ')]] : []),...base,['标注依据','A101 平面图、A301 1–1 剖面图']]
  const rows=objectSpecs(group,index,{
    model:m,core:core.value,garage:garage.value,room:visibleRooms.value[index],
    points:paths.value?.[index],label:labels.value[group]
  })
  const station=group==='rooms' && liftStations.value.find(s=>s.name===visibleRooms.value[index]?.name)
  return station ? [...rows,['设备 · P301',[station.machine,station.model].filter(Boolean).join(' ')],['图注流量 · 扬程 · 功率',station.performance]] : rows
})
const selectedSheet = computed(() => {
  const {group,index} = selection.value
  const code = group === 'rooms' ? visibleRooms.value[index]?.source || 'A101'
    : group.startsWith('core') ? 'A401' : ['parking','lanes','crossings'].includes(group) ? 'B2交通' : 'A101'
  return sources.value.some(s => s.code === code && s.file) ? code : ''
})
</script>

<style scoped>
.drawing { min-width:0; }
nav,header,.pickers,.sources,.blank { display:flex; gap:8px; align-items:center; flex-wrap:wrap; }
nav { border-bottom:1px solid var(--dhic-border-color); padding-bottom:7px; }
button,label,select,a {font:inherit;font-size:13px;}
button,select,.blank label { cursor:pointer; border:1px solid var(--dhic-border-color); border-radius:5px; padding:5px 9px; background:white; color:inherit; }
nav button {border-color:transparent;}
nav button[aria-pressed=true] { background:var(--dhic-row-selected); font-weight:650; }
header { gap:8px 12px; } header h3 { margin-right:auto; } header { justify-content:flex-end; padding:12px 0; } h3 { margin:0; font-size:15px; }
.properties,.blank,.sources {padding:12px 0;} .pickers label,.sources label {display:flex; align-items:center; gap:6px;}
.blank input {max-width:170px;} dl { display:grid; grid-template-columns: repeat(auto-fit,minmax(160px,1fr)); gap:10px 18px; margin:12px 0; }
dt,.note,small {font-size:12px;color:var(--dhic-text-secondary);} dd {margin:4px 0 0;font-size:13px;} .note {line-height:1.6;margin:0;}
.sources object {width:100%;height:clamp(400px,64vh,850px);border:1px solid var(--dhic-border-color);} .sources small {flex-basis:100%;overflow-wrap:anywhere;}
.drawing-index {width:100%; border-collapse:collapse; font-size:13px;}
.drawing-index th,.drawing-index td {text-align:start;padding:11px 6px;border-bottom:1px solid var(--dhic-border-color-light);}
.drawing-index th {color:var(--dhic-text-secondary);font-weight:550;}
.drawing {container-type:inline-size;}
.unbound {align-self:center;padding:0 8px;color:var(--dhic-text-secondary);font-size:12px;}
.views,.scope {display:flex;align-items:center;gap:8px;flex-wrap:wrap;}
.scope {gap:0;padding:2px;background:#f0f1f2;border-radius:6px;}
.scope button {background:transparent;border:0;padding:5px 12px;}
.scope button[aria-pressed="true"] {background:white;box-shadow:0 1px 3px #24292e20;font-weight:600;}
.upload {position:relative;cursor:pointer;padding:5px 8px;color:var(--dhic-text-regular);}
.upload input {position:absolute;inset:0;width:100%;opacity:0;cursor:pointer;}
.upload:focus-within {outline:2px solid var(--dhic-focus-ring);}
.model-workbench {display:grid;grid-template-columns:minmax(0,1fr) clamp(200px,18%,270px);gap:18px;}
.properties {border-left:1px solid var(--dhic-border-color-light);padding:6px 0 0 16px;min-width:0;}
.properties > button {margin-bottom:10px;} .properties select {max-width:100%;min-width:0;} .properties label {max-width:100%;} .properties .pickers {align-items:flex-start;margin-top:12px;}
.properties dl {grid-template-columns:1fr;}
.properties dl div {border-bottom:1px solid var(--dhic-border-color-light);padding-bottom:7px;}
@container (max-width:800px) {
  .model-workbench {grid-template-columns:minmax(0,1fr);gap:10px;}
  .properties {border:0;padding:0;}
  .properties dl {grid-template-columns:repeat(auto-fit,minmax(min(145px,100%),1fr));}
}
.properties.picked h3::before { content:''; width:9px;height:9px;background:var(--selection-color);display:inline-block;margin-right:7px;border-radius:50%; }
.selected-value dd {color:var(--selection-color);font-weight:650;}
.drawing:fullscreen {background:#fff;box-sizing:border-box;padding:8px 16px;overflow:auto;}
.drawing:fullscreen :deep(.model-stage) {height:calc(100dvh - 115px);}
@container (max-width:800px) { .drawing:fullscreen :deep(.model-stage) {height:min(65svh,98cqw);} }
</style>
