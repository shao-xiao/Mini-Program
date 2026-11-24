<template>
  <div class="water-layout">
    <FloorScene :key="system" :geometry="geometry" :water-items="items" :selection="selection" @select="index=$event.index"/>
    <aside aria-label="给排水构件规格">
      <h3><i :style="{background:highlight}"></i>{{ selected.name || pipeLayers[selected.layer][0] }}</h3>
      <label>构件类型<select v-model="kind" @change="chooseKind"><option v-for="k in kinds" :key="k">{{ k }}</option></select></label>
      <label>图元<select v-model.number="index"><option v-for="entry in choices" :key="entry.i" :value="entry.i">{{ entry.text }}</option></select></label>
      <dl aria-live="polite"><div v-for="[key,value] in fields" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></div></dl>
      <p class="reference">{{ waterSystems[system].reference }}</p>
      <h4>图层图例</h4>
      <div class="legend"><span v-for="layer in layers" :key="layer"><i :style="{background:pipeLayers[layer][1]}"></i>{{ pipeLayers[layer][0] }}</span></div>
      <p>{{ selected.kind==='集水坑' ? '水面循环演示蓄水与抽排，节奏为示意。水位按图中相对基准 H 标注。' : '水平位置按图配准，管线粗细与显示高度用于辨识。' }}</p>
      <button @click="$emit('source','P301')">核对平面 · P301</button>
      <button @click="$emit('source',systemSheet)">核对原理 · {{ systemSheet }}</button>
    </aside>
  </div>
</template>
<script setup>
import {computed,ref,watch} from 'vue'
import FloorScene from './FloorScene.vue'
import {pipeLayers,waterSystems} from './plumbingCatalog'
import {selectionColor as highlight} from './garageMarkings'
const props = defineProps({geometry:Object, model:Object, system:String})
defineEmits(['source'])
const items = computed(()=>props.model.items.filter(item=>item.system===props.system))
const index=ref(0), kind=ref('')
const selected=computed(()=>items.value[index.value])
const systemSheet=computed(()=>waterSystems[props.system].sheet)
const kinds=computed(()=>[...new Set(items.value.map(item=>item.kind))])
const layers=computed(()=>[...new Set(items.value.map(item=>item.layer))])
const selection=computed(()=>({group:'water',index:index.value}))
const choices=computed(()=>items.value.map((item,i)=>({i,kind:item.kind,text:item.name || pipeLayers[item.layer][0]+' · '+item.kind+' '+(i+1)})).filter(e=>e.kind===kind.value))
function chooseKind(){ index.value=choices.value[0].i }
watch(()=>props.system,()=>{
  index.value=Math.max(0,items.value.findIndex(item=>item.name==='JSK1-1'))
  kind.value=selected.value.kind
},{immediate:true,flush:'sync'})
watch(selected,item=>{kind.value=item.kind})
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
  } else result.push(['构件','立管平面符号'])
  result.push(['定位依据','P301 · 1∶150'])
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
p{line-height:1.6} @container(max-width:800px){.water-layout{grid-template-columns:minmax(0,1fr)}aside{border:0;padding:0}dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 10px}}
</style>
