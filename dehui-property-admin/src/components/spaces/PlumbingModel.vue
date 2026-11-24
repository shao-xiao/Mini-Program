<template>
  <div class="water-layout">
    <FloorScene :core="core" :floor-elevation="floorElevation" :plumbing="model" :rooms="rooms" :key="system+layer" :geometry="geometry" :water-items="items" :selection="selection" @select="index=$event.index"/>
    <aside aria-label="给排水构件规格">
      <h3><i :style="{background:highlight}"></i>{{ selected.name || pipeLayers[selected.layer][0] }}</h3>
      <label>显示图层<select :value="layer" @change="setLayer($event.target.value)">
        <option value="">全部图层</option><option v-for="name in layers" :key="name" :value="name">{{ pipeLayers[name][0] }}</option>
      </select></label>
      <label>构件类型<select v-model="kind" @change="chooseKind"><option v-for="k in kinds" :key="k">{{ k }}</option></select></label>
      <label>图元<select v-model.number="index"><option v-for="entry in choices" :key="entry.i" :value="entry.i">{{ entry.text }}</option></select></label>
      <section v-if="selected.connection" class="route">
        <h4>排水路径 · P202</h4><ol>
          <li>{{ selected.connection.inlet || '来水支路待核' }}</li>
          <li>{{ selected.name }} · 潜水泵</li>
          <li>{{ selected.connection.riser }} · DN{{ selected.connection.dn }}</li>
          <li>图示出口 {{ selected.connection.outlet }}</li>
        </ol>
      </section>
      <section v-if="selected.stationName" class="route">
        <h4>关联设备</h4><span>{{ selected.stationName }}</span>
        <button @click="$emit('station',selected.stationName)">查看设备立面与流向</button>
      </section>
      <dl aria-live="polite"><div v-for="[key,value] in fields" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></div></dl>
      <p class="reference">{{ waterSystems[system].reference }}</p>
      <h4>图层图例</h4>
      <div class="legend"><span v-for="layer in layers" :key="layer"><i :style="{background:pipeLayers[layer][1]}"></i>{{ pipeLayers[layer][0] }}</span></div>
      <p>{{ selected.kind==='集水坑' ? '水面循环演示蓄水与抽排，节奏为示意。水位按图中相对基准 H 标注。' : '水平位置按图配准，管线粗细与显示高度用于辨识。' }}</p>
      <p>管内亮带演示水流，通气管内浅色气泡演示空气。已核管段按排水或冲洗方向流动，其余管段以往复亮带示意。速度和安装高度为显示参数。消防系统按试水场景展示。</p>
      <button @click="$emit('source',plan)">核对平面 · {{ plan }}</button>
      <button @click="$emit('source',systemSheet)">核对原理 · {{ systemSheet }}</button>
      <button v-if="detailSheet" @click="$emit('source',detailSheet)">泵房大样 · {{ detailSheet }}</button>
    </aside>
  </div>
</template>
<script setup>
import {computed,ref,watch} from 'vue'
import FloorScene from './FloorScene.vue'
import {buildStations} from './plumbingStations'
import {pipeLayers,waterSystems} from './plumbingCatalog'
import {selectionColor as highlight} from './garageMarkings'
const props = defineProps({core:Object, floorElevation:Number, geometry:Object, model:Object, system:String, rooms:{type:Array,default:()=>[]}})
defineEmits(['source','station'])
const systemItems = computed(() => {
  const links = new Map()
  for (const station of buildStations(props.model, props.rooms)) {
    for (const run of station.runs) {
      const item = props.model.items[run.id]
      const direction = run.medium === '通气' ? 0 : (run.points[0] === item.points[0] ? 1 : -1)
      links.set(item, { direction, stationName: station.name })
    }
    for (const outlet of station.outlets) {
      const item = props.model.items[outlet.id]
      links.set(item, { direction: outlet.medium === '排水' ? 1 : 0, stationName: station.name })
    }
    if (station.kind === '集水坑') links.set(station.name, { stationName: station.name })
  }
  return props.model.items.filter(item => item.system === props.system).map(item => {
    const link = links.get(item) || links.get(item.name)
    return link ? { ...item, ...link } : item
  })
})
const layer=ref('')
const items=computed(()=>systemItems.value.filter(item=>!layer.value || item.layer===layer.value))
const plan=computed(()=>waterSystems[props.system].plan || 'P301')
const detailSheet=computed(()=>{
  const code=waterSystems[props.system].detail
  return props.model.sources.some(s=>s.code===code) ? code : null
})
const index=ref(0), kind=ref('')
const selected=computed(()=>items.value[index.value])
const systemSheet=computed(()=>waterSystems[props.system].sheet)
const kinds=computed(()=>[...new Set(items.value.map(item=>item.kind))])
const layers=computed(()=>[...new Set(systemItems.value.map(item=>item.layer))])
const selection=computed(()=>({group:'water',index:index.value}))
const choices=computed(()=>items.value.map((item,i)=>({i,kind:item.kind,text:item.name || pipeLayers[item.layer][0]+' · '+item.kind+' '+(i+1)})).filter(e=>e.kind===kind.value))
function setLayer(value) {
  index.value=0
  layer.value=value
  kind.value=selected.value.kind
}
function chooseKind(){ index.value=choices.value[0].i }
watch(()=>props.system,()=>{
  layer.value=''
  index.value=Math.max(0,items.value.findIndex(item=>item.name==='JSK1-1'))
  kind.value=selected.value.kind
},{immediate:true,flush:'sync'})
watch(selected,item=>{kind.value=item.kind})
function contains(points,x,z) {
  let inside=false
  for (let i=0,j=points.length-1;i<points.length;j=i++) {
    const [ax,az]=points[i], [bx,bz]=points[j]
    if ((az>z)!==(bz>z) && x<(bx-ax)*(z-az)/(bz-az)+ax) inside=!inside
  }
  return inside
}
const fields=computed(()=>{
  const s=selected.value
  const result=[['所属系统',waterSystems[props.system].name],['图纸图层',pipeLayers[s.layer][0]]]
  if(s.kind==='集水坑'){
    result.push(['水泵配置',s.pump+' · '+s.duty],['图注单泵参数',s.flow+' T/h · '+s.head+' m · '+s.power+' kW'])
    const levelLabels=['起泵水位','停泵水位','报警水位']
    levelLabels.forEach((label,i)=>result.push([label,'H − '+Math.abs(s.levels[i]).toFixed(3)+' m']))
  } else if(s.kind==='管段'){
    const [a,b]=s.points
    result.push(['图示水平投影',Math.hypot(a[0]-b[0],a[1]-b[1]).toFixed(2)+' m'])
  } else result.push(['构件',s.kind==='喷头' ? '喷头平面符号' : '立管平面符号'])
  const [a,b=a]=s.points
  const x=(a[0]+b[0])/2, z=(a[1]+b[1])/2
  const room=props.rooms.find(room=>contains(room.points,x,z))
  if (room) result.push(['图元中心所在空间',room.name])
  result.push(['管内介质',s.layer==='通气' ? '空气' : pipeLayers[s.layer][0].includes('污水') ? '污水' : '水'])

  result.push(['定位依据',plan.value+' · 地下二层平面'])
  return result
})
</script>
<style scoped>
.water-layout{display:grid;grid-template-columns:minmax(0,1fr) clamp(205px,19%,280px);gap:18px}
aside{min-width:0;border-left:1px solid var(--dhic-border-color-light);padding:4px 0 0 16px;font-size:13px}
h3,h4{margin:4px 0 12px} i{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:6px}
label{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:6px 0}
select,button{font:inherit;color:inherit;background:white;border:1px solid var(--dhic-border-color);border-radius:5px;padding:5px 8px;max-width:74%;cursor:pointer}
button{margin:3px 5px 0 0} dl>div{padding:7px 0;border-bottom:1px solid var(--dhic-border-color-light)}
dt,p{font-size:12px;color:var(--dhic-text-secondary)} dd{margin:4px 0 0;color:var(--selection-color);font-weight:600}
.legend{display:flex;gap:7px 12px;flex-wrap:wrap;font-size:12px}.legend i{border-radius:3px}
.route{background:#f4f7f8;border-radius:6px;padding:9px 11px;margin:10px 0}.route h4{margin:0 0 6px}.route ol{margin:0;padding-left:20px}.route li{padding:3px 0}
p{line-height:1.6} @container(max-width:800px){.water-layout{grid-template-columns:minmax(0,1fr)}aside{border:0;padding:0}dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 10px}}
</style>
