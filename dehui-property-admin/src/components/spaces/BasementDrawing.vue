<template>
  <section class="drawing" aria-label="B2 楼层图模">
    <nav aria-label="图纸专业">
      <button v-for="name in tabs" :key="name" type="button" :aria-pressed="tab === name" @click="changeTab(name)">{{ name }}</button>
    </nav>
    <p v-if="busy" role="status">正在读取楼层图模…</p>
    <div v-else-if="!model" class="blank">
      <strong>{{ message || '尚未关联竣工图模' }}</strong>
      <button type="button" @click="load">重新读取</button>
      <label v-if="canAttach">关联竣工图包<input type="file" accept=".zip" @change="attach"></label>
    </div>
    <template v-else>
      <header><h3>{{ tab === '平面图' ? '地下二层 · 建筑模型' : tab + '竣工图' }}</h3>
        <button v-if="tab === '平面图'" type="button" @click="original = !original">{{ original ? '返回三维模型' : '核对建筑原图' }}</button>
      </header>
      <template v-if="tab === '平面图' && !original">
        <FloorScene :geometry="model.geometry" :selection="selection" @select="selection = $event" />
        <div class="properties">
          <div class="pickers">
            <label>构件类型<select aria-label="构件类型" :value="selection.group" @change="selection = { group: $event.target.value, index: 0 }">
              <option v-for="(label,key) in labels" :key="key" :value="key">{{ label }}</option>
            </select></label>
            <label v-if="selection.group !== 'outline'">图元<select aria-label="图元" :value="selection.index" @change="selection = { ...selection, index: Number($event.target.value) }">
              <option v-for="(_,i) in model.geometry[selection.group]" :key="i" :value="i">{{ labels[selection.group] }} {{ i + 1 }}</option>
            </select></label>
          </div>
          <dl aria-live="polite"><div v-for="[label,val] in specs" :key="label"><dt>{{ label }}</dt><dd>{{ val }}</dd></div></dl>
          <p class="note">图面按同一米制比例重建。墙柱在地坪以上 1.20 m 处剖切，楼梯与内隔墙未核实的高度仅显示平面线稿。图元序号仅用于本图定位。</p>
        </div>
      </template>
      <table v-else-if="tab !== '平面图'" class="drawing-index">
        <thead><tr><th>图号</th><th>图名</th><th>核对状态</th></tr></thead>
        <tbody><tr v-for="s in sources" :key="s.code"><td>{{ s.code }}</td><td>{{ sheetTitle(s) }}</td><td>已核对图签 · 未建模</td></tr></tbody>
      </table>
      <div v-else class="sources">
        <label>图纸<select v-model="sourceCode"><option v-for="s in sources" :key="s.code" :value="s.code">{{ s.code }} · {{ sheetTitle(s) }}</option></select></label>
        <p v-if="sourceError" role="status">{{ sourceError }}</p>
        <template v-else-if="pdfUrl">
          <a :href="pdfUrl" target="_blank" rel="noopener">在新窗口核对原图 ↗</a>
          <object :data="pdfUrl" type="application/pdf" :aria-label="source?.name">请在新窗口查看 PDF。</object>
        </template>
        <p v-else role="status">{{ source?.file ? '正在校验原图…' : '已核对图号和文件名称，原图尚未关联。' }}</p>
        <small v-if="source">{{ source.name }}</small>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, ref, shallowRef, watch, onBeforeUnmount, defineAsyncComponent } from 'vue'
import { hasPermission } from '../../utils/permission'
import { readFloorArchive, attachFloorArchive, openSource } from './floorDrawingArchive'
const FloorScene = defineAsyncComponent(() => import('./FloorScene.vue'))
const props = defineProps({ floorId: { type: Number, required: true }, ledgerArea: [String, Number] })
const tabs = ['平面图','给排水','强电','消防','弱电']
const labels = { outline:'楼层轮廓', bays:'车位', columns:'柱', walls:'实填墙段' }
const canAttach = hasPermission('floor:update')
const tab = ref(tabs[0]), original = ref(false), selection = ref({ group:'outline', index:0 })
const model = shallowRef(null), archive = shallowRef(null), busy = ref(false), message = ref('')
const sourceCode = ref(''), pdfUrl = ref(''), sourceError = ref('')
let epoch = 0, pdfEpoch = 0
const sources = computed(() => model.value?.sources.filter(s => tab.value === '平面图' ? ['建筑','剖面'].includes(s.group) : s.group === tab.value) || [])
const source = computed(() => sources.value.find(s => s.code === sourceCode.value))
function changeTab(name) { tab.value = name; original.value = false }
function sheetTitle(s) { return s.name.replace(/^.*?地下二层/, '地下二层').replace(/\.pdf$/i, '') }
function clearPdf() { if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value); pdfUrl.value = '' }
watch(sources, list => { sourceCode.value = list[0]?.code || '' }, { immediate:true })
watch([source, original, tab, archive], async () => {
  const run = ++pdfEpoch
  clearPdf(); sourceError.value = ''
  if (!source.value?.file || !archive.value || (tab.value === '平面图' && !original.value)) return
  try {
    const url = await openSource(archive.value, source.value)
    if (run === pdfEpoch) pdfUrl.value = url
    else URL.revokeObjectURL(url)
  } catch (e) { if (run === pdfEpoch) sourceError.value = e.message }
})
async function load() {
  const run = ++epoch
  busy.value = true; message.value = ''; model.value = null; archive.value = null
  try {
    const data = await readFloorArchive(props.floorId)
    if (data && run === epoch) { archive.value = data.archive; model.value = data.model }
  } catch (e) { if (run === epoch) message.value = e.message || '读取失败' }
  finally { if (run === epoch) busy.value = false }
}
async function attach(event) {
  const file = event.target.files[0]
  if (!file) return
  busy.value = true
  try { await attachFloorArchive(props.floorId, file); await load() }
  catch (e) { message.value = e.message || '关联失败'; busy.value = false }
}
watch(() => props.floorId, load, { immediate:true })
onBeforeUnmount(() => { epoch++; pdfEpoch++; clearPdf() })
const specs = computed(() => {
  const m = model.value
  if (!m) return []
  const {group,index} = selection.value
  const base = [['本层地坪标高', m.elevation.toFixed(3) + ' m'],['B2 至 B1 层高', (m.upperElevation - m.elevation).toFixed(3) + ' m']]
  if (group === 'outline') return [['竣工图面积',m.area + ' ㎡'],...(props.ledgerArea != null ? [['台账登记面积',props.ledgerArea + ' ㎡']] : []),['外包尺寸',m.span.map(n => n.toFixed(2)).join(' × ') + ' m'],['图注实际停车',m.geometry.bays.length + ' 辆'],...base,['标注依据','A101 平面图、A301 1–1 剖面图']]
  const pts = m.geometry[group][index]
  const lengths = pts.map(([x,z],i) => {const b=pts[(i+1)%pts.length]; return Math.hypot(x-b[0],z-b[1])})
  const dimensions = ['平面轮廓四边', lengths.map(n => n.toFixed(2)).join(' / ') + ' m']
  return [['所选图元',labels[group]+' '+(index+1)],dimensions,['尺寸依据','A101 矢量轮廓量取 · 取至 0.01 m'],...base]
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
header { justify-content:space-between; padding:12px 0; } h3 { margin:0; font-size:15px; }
.properties,.blank,.sources {padding:12px 0;} .pickers label,.sources label {display:flex; align-items:center; gap:6px;}
.blank input {max-width:170px;} dl { display:grid; grid-template-columns: repeat(auto-fit,minmax(160px,1fr)); gap:10px 18px; margin:12px 0; }
dt,.note,small {font-size:12px;color:var(--dhic-text-secondary);} dd {margin:4px 0 0;font-size:13px;} .note {line-height:1.6;margin:0;}
.sources object {width:100%;height:clamp(400px,64vh,850px);border:1px solid var(--dhic-border-color);} .sources small {flex-basis:100%;overflow-wrap:anywhere;}
.drawing-index {width:100%; border-collapse:collapse; font-size:13px;}
.drawing-index th,.drawing-index td {text-align:start;padding:11px 6px;border-bottom:1px solid var(--dhic-border-color-light);}
.drawing-index th {color:var(--dhic-text-secondary);font-weight:550;}
</style>
