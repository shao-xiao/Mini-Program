<template><div class="electric" :class="{inspecting:detail}">
<section ref="stage" v-show="!sheetView" class="e-stage">
<SelectionMark :shapes="marks"/><div ref="labels" class="e-labels"></div>
<button ref="pin" class="e-pin" :class="{chosen:selected?.kind==='panel'}" v-show="ready" @click="piece('panel')">{{group.panel}}</button>
<div class="e-tools"><button @click="viewer?.view('iso')">轴测</button><button @click="viewer?.view('top')">俯视</button><button aria-label="放大" @click="viewer?.zoom(1.35)">＋</button><button aria-label="缩小" @click="viewer?.zoom(1/1.35)">−</button><button @click="playing=!playing">{{playing?'暂停演示':'继续演示'}}</button><button @click="replay">重播</button></div>
<div v-if="!ready" class="e-wait">{{err||'正在装配楼层…'}}</div>
<div v-if="ready" class="e-inset"><header>{{group.logical?'系统图逻辑关系':'局部路径'}}<button @click="viewer.focus()">定位</button></header>
<svg viewBox="0 0 216 66" aria-label="同步路径"><polyline :points="route" fill="none" stroke="#9daeb5" stroke-width="4"/><polyline :points="route" fill="none" :stroke="color" stroke-width="4" pathLength="100" :stroke-dasharray="progress+' 110'"/>
<polyline :points="route" fill="none" :stroke="selected?.kind==='wire'?'#823ab9':'transparent'" stroke-width="5" @click="piece('wire')"/>
<circle v-for="(v,i) in [line[0],line.at(-1)]" :key="i" :cx="v[0]" :cy="v[1]" r="6" :fill="selected?.kind===(i?'load':'panel')?'#843db7':i&&phase<2?'#88979b':color" @click="piece(i?'load':'panel')"/></svg>
<button @click="piece('panel')">{{group.panel}}</button> → <button @click="piece('load')">{{group.load}}</button></div>
<footer v-if="ready" class="e-status"><div><b>{{mode}}</b> {{group.steps[phase]}}</div><small><i :style="{background:color}"></i>{{legend}} · 模拟状态</small></footer></section>
<section v-if="sheetView" class="e-sheet"><div class="e-sheet-tools">{{sheet}} · 点击图中构件查看参数 <button @click="scale=Math.min(5,scale+.5)">＋</button><button @click="scale=Math.max(1,scale-.5)">−</button></div><div class="e-sheet-scroll"><div class="e-sheet-page" :style="{width:scale*100+'%'}"><img :src="src.image" :alt="sheet" @click="pickImage"/><SelectionMark v-if="selectionSheet===sheet" :shapes="sheetMarks" :view-box="'0 0 '+src.size.join(' ')" :radius="12"/></div></div></section>
<aside class="e-side" v-if="detail"><header><strong>{{detail.title}}</strong><button aria-label="关闭参数" @click="clear">×</button></header>
<label>改选构件 <select aria-label="改选构件" :value="choice" @change="selectChoice($event.target.value)"><option disabled value="">当前对象</option><option v-for="(o,i) in choices" :value="i" :key="i">{{o.name}}</option></select></label>
<button v-if="selected?.hasEquipment" @click="viewer.focusRoom(selected)">近看房内设备</button>
<dl><template v-for="([k,v],i) in detail.rows" :key="i"><dt>{{k}}</dt><dd>{{v}}</dd></template></dl><section><b>核对原图</b><a v-for="c in detail.sources" :key="c" :href="sources[c].file" target="_blank" rel="noopener">{{c}} · {{sources[c].name||c}} ↗</a></section></aside>
</div></template>
<script setup>
import {modelAssets} from '../assets.js'

import {computed,ref,shallowRef,onMounted,onBeforeUnmount} from 'vue'
import {electricalScene} from '../electric/scene.js'
import SelectionMark from '../electric/SelectionMark.vue'
const props=defineProps({mode:String,sheetView:Boolean,sheetCode:String}),data=modelAssets.serviceData,group=data.groups[props.mode],sources={...modelAssets.electricData.sources,...data.sources}
const stage=ref(),labels=ref(),pin=ref(),ready=ref(false),err=ref(''),playing=ref(!matchMedia('(prefers-reduced-motion: reduce)').matches),t=ref(0),scale=ref(1)
const selected=shallowRef(null),marks=shallowRef([]),world=shallowRef([]),rooms=shallowRef([]),detail=shallowRef(null),choice=ref(''),selectionSheet=ref('')
const sheet=computed(()=>props.sheetCode||group.sheets[0]),src=computed(()=>sources[sheet.value])
const sheetMarks=computed(()=>world.value.map(s=>({type:s.type,points:s.points.map(([x,y,z])=>[(x-src.value.offset[0])/src.value.scale,(z-src.value.offset[1])/src.value.scale])})))
const water=['消火栓','自动喷淋'].includes(props.mode),color=water?'#0079cf':props.mode==='消防报警与联动'?'#db6816':'#008675',legend=water?'供水':props.mode==='消防报警与联动'?'报警信号':'监测 / 数据信号'
const line=computed(()=>{if(!group.path)return [[12,32],[204,32]];const xs=group.path.map(v=>v[0]),zs=group.path.map(v=>v[1]),x=Math.min(...xs),z=Math.min(...zs),w=Math.max(...xs)-x,h=Math.max(...zs)-z,k=Math.min(186/(w||.001),48/(h||.001));return group.path.map(v=>[108+(v[0]-x-w/2)*k,33+(v[1]-z-h/2)*k])})
const route=computed(()=>line.value.map(p=>p.join(',')).join(' ')),progress=computed(()=>Math.min(100,Math.max(0,(t.value-1.4)/4.3*100))),phase=computed(()=>t.value<1.4?0:t.value<5.7?1:2)
const choices=computed(()=>[{name:group.panel,info:{kind:'panel',index:0}},{name:group.logical?'系统图连接':'图示路径',info:{kind:'wire',index:0}},{name:group.load,info:{kind:'load',index:0}},...data.survey.filter(o=>o.drawings===group.sheets[0]).map(o=>({name:o.name,info:{kind:'survey',survey:o}})),...rooms.value.map(r=>({name:r.room.name,info:r}))])
let viewer,frame,dead=false,last=0
function selectChoice(i){inspect(choices.value[Number(i)].info)}
function piece(kind){inspect({kind,index:0})}
function inspect(info){
 selected.value=info;selectionSheet.value=info.sheet||group.sheets[0];world.value=viewer.select(info);choice.value=choices.value.findIndex(o=>o.info.kind===info.kind&&(info.room?info.roomIndex===o.info.roomIndex:info.survey?info.survey.id===o.info.survey?.id:true));if(choice.value<0)choice.value=''
 if(info.room){detail.value={title:info.room.name+(info.kind==='roomEquipment'?' · 房内设备':''),rows:[['楼层','B2'],...info.room.fields],sources:[info.room.source]};return}
 if(info.survey){const o=info.survey;detail.value={title:o.name,rows:[...o.fields,['依据',o.basis],['线缆归属','逐条穿缆关系待核']],sources:[o.drawings]};return}
 if(info.kind==='layer'||info.kind==='building'){detail.value={title:info.layer||info.asset.label,rows:[['楼层','B2'],['安装依据','沿用已配准模型。单体编号、连接与实际安装标高待核。']],sources:[info.sheet||'A101']};return}
 detail.value={title:info.kind==='panel'?group.panel:info.kind==='load'?group.load:group.panel+' → '+group.load,rows:[['所在区域',group.location],...group.rows,...(group.logical?[['上级定位','上级节点参见系统图；B2 图中定位终端。']]:[]),['标高及状态','显示高度和运行时序为示意，实际安装标高待核。']],sources:group.sheets}
}
function clear(){selected.value=detail.value=null;world.value=viewer.select(null);marks.value=[]}
function pickImage(e){const i=e.currentTarget,r=i.getBoundingClientRect(),s=src.value;viewer.pickPlan((e.clientX-r.left)*i.naturalWidth/r.width*s.scale+s.offset[0],(e.clientY-r.top)*i.naturalHeight/r.height*s.scale+s.offset[1],i.naturalWidth/r.width*s.scale,sheet.value)}
function replay(){t.value=0;last=performance.now();playing.value=true}
function tick(now){if(dead)return;let dt=playing.value&&!document.hidden&&last?Math.min(.3,(now-last)/1000):0;last=now;t.value=(t.value+dt)%8;viewer.render(0,t.value,dt);frame=requestAnimationFrame(tick)}
onMounted(async()=>{try{viewer=electricalScene(stage.value,labels.value,pin.value,{mode:props.mode,service:true,groups:[group],onPick:inspect,onRooms:r=>rooms.value=r,onProjection:r=>marks.value=r});await viewer.load();if(dead)return;ready.value=true;frame=requestAnimationFrame(tick)}catch(e){err.value=e.message;console.error(e)}})
onBeforeUnmount(()=>{dead=true;cancelAnimationFrame(frame);viewer?.dispose()})
</script>
<style scoped src="./workbench.css"></style>
<style scoped>.e-inset button{font-size:11px}.e-inset svg{height:54px}.e-side select{max-width:100%}.e-sheet-page img{width:100%}</style>
