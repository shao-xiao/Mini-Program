<template>
  <aside class="details" aria-label="系统示意图详情">
    <h3><i></i>{{ record?.name || layerTitle || title }}</h3>
    <label>构件<select :value="record?.id || ''" @change="$emit('object',$event.target.value)">
      <option value="">系统总览</option>
      <option v-for="entry in objects" :key="entry.id" :value="entry.id">{{ entry.name }}</option>
    </select></label>
    <label>专业图层<select :value="selection.index" @change="$emit('layer',Number($event.target.value))">
      <option :value="-1">全部图层</option>
      <option v-for="(layer,index) in layers" :key="layer" :value="index">{{ layer }}</option>
    </select></label>
    <p v-if="error" role="alert">{{ error }} <button @click="$emit('retry')">重试</button></p>
    <p v-else-if="loading" role="status">正在装配本层设备…</p>
    <section v-if="connection"><h4>排水路径 · P202</h4><ol>
      <li>{{ connection.inlet || '来水支路待核' }}</li><li>{{ record.name }} · 潜水泵</li>
      <li>{{ connection.riser }} · DN{{ connection.dn }}</li><li>图示出口 {{ connection.outlet }}</li>
    </ol></section>
    <section v-else-if="steps.length">
      <h4>系统运行示意</h4>
      <ol><li v-for="step in steps" :key="step.index"><button :aria-pressed="selection.index===step.index" @click="$emit('layer',step.index)">{{ step.title }}</button></li></ol>
    </section>
    <dl aria-live="polite">
      <template v-if="room"><dt>所在空间</dt><dd>{{ room }}</dd></template>
      <template v-for="[label,value] in record?.fields || []" :key="label"><dt>{{ label }}</dt><dd>{{ value }}</dd></template>
      <template v-if="record?.placement"><dt>截面</dt><dd>{{ record.placement.nominalSizeMm.join(' × ') }} mm</dd><dt>安装要求</dt><dd>{{ record.placement.relativeMount }} · 绝对标高待核</dd></template>
      <template v-if="layerTitle"><dt>原图图层</dt><dd>{{ layers[selection.index] }}</dd></template>
      <template v-if="record?.name?.includes('消防补风口')"><dt>局部流向</dt><dd>沿图注出风面向车库补风，箭头速度为示意。</dd></template>
      <dt>图纸依据</dt><dd>{{ record?.drawings || drawings }}</dd>
      <dt v-if="record">构件编号</dt><dd v-if="record">{{ record.id }}</dd>
    </dl>
    <p>{{ record?.basis || purpose }}</p>
    <p v-if="!record || record.kind==='segment'">具体支路的上下游、服务范围及安装标高，以对应图纸标注为准；未核连接按系统原理示意。</p>
    <button v-for="code in sources" :key="code" @click="$emit('source',code)">核对原图 · {{ code }}</button>
  </aside>
</template>
<script setup>
import { computed } from 'vue'
const props=defineProps({connection:Object,title:String,record:Object,objects:Array,rooms:Array,layers:Array,steps:Array,selection:Object,sources:Array,drawings:String,purpose:String,error:String,loading:Boolean})
defineEmits(['object','layer','source','retry'])
const layerTitle=computed(()=>props.steps.find(s=>s.index===props.selection.index)?.title || props.layers[props.selection.index]?.split(' · ').at(-1))
const room=computed(()=>{
  const record=props.record
  if(record?.kind==='room') return record.name
  const point=record?.planXZ || (record?.origin && [record.origin[0],record.origin[2]]) || props.selection.location
  if(!point) return ''
  const [x,z]=point
  return props.rooms?.find(({points})=>{
    let inside=false
    for(let i=0,j=points.length-1;i<points.length;j=i++){
      const [a,b]=points[i],[c,d]=points[j]
      if((b>z)!==(d>z) && x<(c-a)*(z-b)/(d-b)+a) inside=!inside
    }
    return inside
  })?.name || ''
})
</script>
<style scoped>
.details{padding:5px 0 0 16px;border-left:1px solid var(--dhic-border-color-light);font-size:13px;min-width:0}.details h3{font-size:15px;margin:0 0 12px}.details h3 i{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:7px;background:var(--selection-color,#76339c)}
label{display:flex;align-items:center;justify-content:space-between;gap:8px;margin:7px 0}button,select{background:white;color:inherit;border:1px solid #d7dfe2;border-radius:4px;padding:5px 8px;font:inherit;cursor:pointer}select{max-width:72%}section{background:#f4f7f7;padding:10px 12px;border-radius:5px;margin:14px 0}h4{margin:0 0 5px}ol{margin:0;padding-left:22px}li button{border:0;background:transparent;padding:4px 0;text-align:left}li button[aria-pressed=true],dd{color:var(--selection-color,#76339c)}dt{color:#647387;font-size:12px}dd{margin:4px 0 8px;padding-bottom:7px;border-bottom:1px solid #e9edef;overflow-wrap:anywhere}p{font-size:12px;line-height:1.55;color:#58667a}.details>button{margin:2px 4px 2px 0}@container(max-width:800px){.details{border:0;padding:0}}
</style>
