<template>
  <div class="electric" :class="{inspecting:detail}">
    <section v-show="!sheetView" ref="stage" class="e-stage" :aria-label="mode+'三维模型'">
      <SelectionMark :shapes="screenMarks"/><div ref="labels" class="e-labels"></div>
      <button ref="pin" :class="{chosen:selected?.kind==='panel' && groups[selected.index].panel===g.panel}" v-show="ready" class="e-pin" @click="inspect({kind:'panel',index})" :title="'查看配电箱 '+g.panel">{{g.panel}}</button>
      <div class="e-tools">
        <button @click="viewer?.view('iso')">轴测</button><button @click="viewer?.view('top')">俯视</button>
        <button aria-label="放大" @click="viewer?.zoom(1.35)">＋</button><button aria-label="缩小" @click="viewer?.zoom(1/1.35)">−</button>
        <select v-model="lock" aria-label="演示回路" @change="switchGroup"><option value="all">整层轮播</option>
          <option v-for="(s,i) in groups" :key="i" :value="String(i)">{{s.panel}} · {{s.circuit}}</option>
        </select>
        <button @click="playing=!playing">{{playing?'暂停演示':'继续演示'}}</button><button @click="restart">重播</button>
      </div>
      <div v-if="!ready" class="e-wait" role="status">{{error||'正在装配 B2 房间和强电模型…'}}</div>
      <div v-if="ready&&power" class="e-inset" aria-label="已核支路同步放大图">
        <header>核心筒东侧 · 支路放大<button @click="viewer.focus()">定位机房</button></header>
        <svg viewBox="0 0 228 83" role="group" aria-label="动力配电支路详情">
          <path d="M31 18H169V66 M31 27H109V46" fill="none" stroke="#bcc7ca" stroke-width="4"/>
          <path :d="g.circuit==='P1'?'M31 18H169V66':'M31 27H109V46'" pathLength="100" :stroke-dasharray="(fraction*100)+' 110'" fill="none" stroke="#087bc4" stroke-width="4"/>
          <g v-for="(branch,i) in groups" :key="branch.circuit" :class="{chosen:selected?.kind==='wire' && selected.index===i}" role="button" tabindex="0" :aria-label="branch.circuit+' 线路参数'" @click="inspect({kind:'wire',index:i})" @keydown.enter.prevent="inspect({kind:'wire',index:i})" @keydown.space.prevent="inspect({kind:'wire',index:i})">
            <path :d="branch.circuit==='P1'?'M31 18H169V66':'M31 27H109V46'" fill="none" stroke="transparent" stroke-width="10"/>
            <text :x="i?75:138" :y="i?41:14">{{branch.circuit}}</text>
          </g>
          <g :class="{chosen:selected?.kind==='panel'}" role="button" tabindex="0" aria-label="配电箱参数" @click="inspect({kind:'panel',index})" @keydown.enter.prevent="inspect({kind:'panel',index})" @keydown.space.prevent="inspect({kind:'panel',index})">
            <rect x="7" y="10" width="24" height="27" rx="2" fill="#197795"/><text x="4" y="52">配电箱</text>
          </g>
          <g v-for="(branch,i) in groups" :key="branch.load" :class="{chosen:selected?.kind==='load' && selected.index===i}" role="button" tabindex="0" :aria-label="branch.load+'参数'" @click="inspect({kind:'load',index:i})" @keydown.enter.prevent="inspect({kind:'load',index:i})" @keydown.space.prevent="inspect({kind:'load',index:i})">
            <circle v-if="i===0" cx="169" cy="66" r="6" :fill="g.circuit==='P1'&&lit?'#ffb02b':'#79909b'"/>
            <rect v-else x="102" y="45" width="14" height="11" :fill="g.circuit==='P3'&&lit?'#ffb02b':'#79909b'"/>
            <text :x="i?75:180" :y="i?73:70">{{i?'提升设备箱':'排风机'}}</text>
          </g>
        </svg>
      </div>
      <footer v-if="ready" class="e-status">
        <div><b>{{g.panel}} · {{g.circuit}}</b><span>{{phase}}</span></div>
        <small><i class="fixed"></i>既有构件 <i :style="{background:ink}"></i>{{power?'供电路径':em?'应急灯位':'普通灯组'}} <template v-if="power"><i style="background:#ffaf20"></i>受电末端</template> · 模拟状态</small>
      </footer>
    </section>
    <section v-if="sheetView" class="e-sheet" aria-label="可点选的专业原图">
      <div class="e-sheet-tools"><span>{{sheet}} · 点击图中构件查看参数</span><button aria-label="放大原图" @click="sheetZoom=Math.min(5,sheetZoom+.5)">＋</button><button aria-label="缩小原图" @click="sheetZoom=Math.max(1,sheetZoom-.5)">−</button></div>
      <div class="e-sheet-scroll"><div class="e-sheet-page" :style="{width:(sheetZoom*100)+'%'}"><img :src="sources[sheet].pickImage" :alt="sheet+' '+sheetNames[sheet]" draggable="false" @click="pickDrawing"><SelectionMark :shapes="sheetMarks" viewBox="0 0 1908 1348" :radius="13"/></div></div>
    </section>
    <aside v-if="detail" class="e-side" aria-label="强电构件参数"><header><strong>{{detail.title}}</strong><button aria-label="关闭参数" @click="clearSelection">×</button></header>
      <label v-if="selected?.kind==='room'" class="room-picker">所选房间<select aria-label="所选房间" :value="selected.roomIndex" @change="inspect(rooms.find(r=>r.roomIndex===Number($event.target.value)))"><option v-for="r in rooms" :key="r.roomIndex" :value="r.roomIndex">{{r.room.name}}</option></select></label>
      <dl><template v-for="[k,val] in detail.rows" :key="k"><dt>{{k}}</dt><dd>{{val}}</dd></template></dl>
      <section v-if="detail.branches?.length"><b>从该箱播放</b><button v-for="i in detail.branches" :key="i" @click="choose(i)">{{groups[i].circuit}} · {{groups[i].load}}</button></section>
      <section><b>核对竣工原图</b><a v-for="s in detail.sheets" :key="s" :href="sources[s].file" target="_blank" rel="noopener">{{s}} · {{sheetNames[s]}} ↗</a></section>
    </aside>
  </div>
</template>
<script setup>
import {modelAssets} from '../assets.js'

import {ref,computed,shallowRef,onMounted,onBeforeUnmount} from 'vue'
import SelectionMark from './SelectionMark.vue'
import {electricalScene} from './scene.js'
const props=defineProps({mode:String,sheetView:Boolean}),data=modelAssets.electricData
const power=props.mode==='动力配电',em=props.mode==='应急照明',groups=data.groups.filter(o=>o.mode===props.mode)
const sheet=groups[0].sheets[0],sheetZoom=ref(1)
const sources=data.sources
const stage=ref(null),labels=ref(null),pin=ref(null),ready=ref(false),error=ref(''),lock=ref('all'),index=ref(0),elapsed=ref(0),detail=shallowRef(null)
const playing=ref(!matchMedia('(prefers-reduced-motion: reduce)').matches),g=computed(()=>groups[index.value]),ink=power?'#087bc4':em?'#802bd4':'#e59912'
const fraction=computed(()=>Math.min(1,Math.max(0,(elapsed.value-1.4)/4.3))),lit=computed(()=>elapsed.value>=5.7)
const phase=computed(()=>{
  const t=elapsed.value,x=g.value
  if(power)return t<1.4?'配电箱出线':t<5.7?'沿 '+x.circuit+' 供电':'已到达 · '+x.load
  if(em)return t<1.4?'常用电源供电':t<3?'箱内双电源自动切换':t<4.5?'EX1 → '+x.load:x.zone+' · 模拟点亮 '+x.points.length+' 处'
  return t<2.2?'照明箱选通 '+x.circuit:x.zone+' · 点亮 '+x.points.length+' 处已核灯位'
})
const sheetNames={A101:'地下二层平面',A401:'核心筒放大图',E401:'电力平面',E207:'动力配电系统',E302:'照明平面',E204:'照明配电系统',E301:'应急照明平面',E202:'应急照明配电系统'}
const selected=shallowRef(null),rooms=shallowRef([]),screenMarks=shallowRef([]),worldMarks=shallowRef([])
const sheetMarks=computed(()=>worldMarks.value.map(s=>({type:s.type,points:s.points.map(([x,y,z])=>[(x+17.2799375)*.8/.05291666666666667,(z+4.96781666667)*.8/.05291666666666667])})))
function clearSelection(){selected.value=null;detail.value=null;worldMarks.value=viewer?.select(null)||[];screenMarks.value=[]}
let viewer,frame,dead=false,previous=0
function restart(){index.value=lock.value==='all'?0:Number(lock.value);elapsed.value=0;previous=performance.now();playing.value=true}
function switchGroup(){restart();if(lock.value==='all')clearSelection();else inspect({kind:'wire',index:index.value})}
function choose(i){lock.value=String(i);restart();inspect({kind:'wire',index:i})}
function pickDrawing(e){
  const img=e.currentTarget,r=img.getBoundingClientRect(),scale=0.05291666666666667/.8
  const x=(e.clientX-r.left)*img.naturalWidth/r.width*scale-17.2799375
  const z=(e.clientY-r.top)*img.naturalHeight/r.height*scale-4.96781666667
  viewer?.pickPlan(x,z,img.naturalWidth/r.width*scale)
}
function inspect(info){
  selected.value=info;worldMarks.value=viewer.select(info)
  if(info.kind==='room'||info.kind==='roomEquipment'){
    const room=info.room,equipment=info.kind==='roomEquipment'
    const rows=[['楼层','B2 地下二层'],['所在房间',room.name],...room.fields]
    if(equipment)rows.push(['设备依据','沿用房间模块内的设备示意外形。单台设备编号及供电回路待核。'])
    detail.value={title:equipment?room.name+' · 房内设备':room.name,rows,sheets:[room.source]};return
  }
  if(info.kind==='building'){
    const asset=info.asset
    detail.value={title:asset.label,rows:[['楼层','B2 地下二层'],['类型',asset.kind],['模型剖切高度',asset['展示剖切高度']]],sheets:['A101']};return
  }
  if(info.kind==='survey'){
    const o=info.survey;detail.value={title:o.name,rows:[['图元编号',o.id],['回路归属','共用敷设空间，逐根电缆归属待核。'],...(o.fields||[]),['安装依据',o.basis]],sheets:[o.drawings]};return
  }
  if(info.kind==='layer'){
    const l=info.layer,title=l.includes('桥架')||l.includes('TRAY')?'桥架':l.startsWith('EQUIP')?l.replace('EQUIP-',''):'线路'
    detail.value={title,rows:[['图纸图层',l],['编号、回路及服务区域','该图元的逐项对应关系待核。'],['安装依据','沿用已配准专业 GLB，构件安装标高待核。']],sheets:[info.sheet]};return
  }
  const i=info.index,x=groups[i],isPanel=info.kind==='panel',isLoad=info.kind==='load'
  const branches=groups.map((s,i)=>s.panel===x.panel?i:-1).filter(i=>i>=0)

  let title=isPanel?x.panel:isLoad?(em?'应急灯位':x.load):x.panel+' / '+x.circuit
  let rows=[['配电箱',x.panel],['回路',isPanel?branches.map(j=>groups[j].circuit).join('、'):em&&isLoad?'灯具末级分支待核':x.circuit],['服务区域',x.zone],['安装依据',x.mount]]
  if(power)rows.push(['供电关系',isPanel?'P1 → 污水处理间排风机；P3 → 一体化污水提升设备自带控制箱':x.panel+' → '+x.circuit+' → '+x.load],...(isPanel?[]:[['设备额定值',x.rating],['线路标注',x.wire]]))
  else if(em)rows.push(['箱内切换','E202 双电源自动转换，WTS 40 A / 4P。B2ALE2 同图。'],['集中电源出线',x.panel+' / EX1 → '+x.load],['灯组依据','E301 图示灯位，按地理区域演示。集中电源下游逐灯分支待核。'])
  else rows.push(['灯组依据','E204 箱内出线与 E302 灯旁回路标注对应。演示覆盖已核灯位。'])
  detail.value={title,rows,branches:isPanel?branches:[],sheets:x.sheets}
}
function tick(now){
  if(dead)return
  const dt=playing.value&&previous&&!document.hidden?Math.min(.3,(now-previous)/1000):0
  previous=now
  let t=elapsed.value+dt
  if(t>=8){t%=8;if(lock.value==='all')index.value=(index.value+1)%groups.length}
  elapsed.value=t;viewer.render(index.value,t,dt);frame=requestAnimationFrame(tick)
}
onMounted(async()=>{try{viewer=electricalScene(stage.value,labels.value,pin.value,{mode:props.mode,groups,onPick:inspect,onProjection:marks=>screenMarks.value=marks,onRooms:list=>rooms.value=list});await viewer.load();if(dead)return;ready.value=true;frame=requestAnimationFrame(tick)}catch(e){error.value='强电模型载入失败 · '+e.message;console.error(e)}})
onBeforeUnmount(()=>{dead=true;cancelAnimationFrame(frame);viewer?.dispose()})
</script>
<style scoped>
.electric{position:relative;display:grid;grid-template-columns:minmax(0,1fr);gap:14px}.electric.inspecting{grid-template-columns:minmax(0,1fr) minmax(210px,250px)}
.e-stage{height:clamp(575px,78vh,1000px);position:relative;overflow:hidden;background:#edf1f0;border-radius:5px;isolation:isolate}.e-stage :deep(canvas){width:100%;height:100%;display:block;touch-action:none}
.e-tools,.e-inset,.e-status,.e-wait{position:absolute;background:#ffffffef;border:1px solid #cdd7d8;border-radius:5px;color:#263c43}.e-tools{top:10px;left:10px;display:flex;gap:2px;max-width:calc(100% - 20px);flex-wrap:wrap;padding:3px}.electric button,.electric select{font:inherit;color:inherit;cursor:pointer}.e-tools button,.e-tools select,.e-inset button,.e-side button{padding:4px 7px;background:white;border:1px solid #d2ddde;border-radius:3px}.e-tools button:hover,.e-inset button:hover,.e-side button:hover{background:#e8f3f6}
.e-labels{position:absolute;inset:0;overflow:hidden;pointer-events:none}.e-labels :deep(.plan-label){position:absolute;left:0;top:0;background:#f9fcfbd9;color:#263e45;font:500 11px/15px system-ui;white-space:nowrap;border-radius:2px;padding:0 2px}
.e-pin{position:absolute;transform:translate(6px,-100%);background:#15546b;color:#fff!important;border:1px solid white;border-radius:3px;font:600 11px/1.3 system-ui!important;padding:3px 5px}.e-pin:before{content:'';position:absolute;bottom:-4px;left:-5px;border-right:7px solid #15546b;border-bottom:5px solid transparent}
.e-status{right:10px;bottom:10px;padding:7px 10px;pointer-events:none}.e-status div,.e-status small{display:flex;align-items:center;gap:8px}.e-status small{color:#536171;font-size:11px}.e-status i{width:11px;height:5px;display:inline-block}.fixed{background:#677980}
.e-inset{left:10px;bottom:10px;width:232px;padding:6px 8px}.e-inset header,.e-side header{display:flex;align-items:center;justify-content:space-between;gap:6px;font-size:12px}.e-inset svg{width:100%;height:78px;display:block}.e-inset text{font:10px system-ui;fill:#29424c}.e-wait{left:40%;top:45%;padding:8px}.e-side{border-left:1px solid #dce3e5;padding-left:14px;overflow-wrap:anywhere;font-size:13px}.e-side header{font-size:15px}.e-side dl{padding:0;margin:8px 0}.e-side dt{color:#667587;padding-top:7px;font-size:12px}.e-side dd{margin:2px 0;padding-bottom:7px;border-bottom:1px solid #e6ecec}.e-side section{display:grid;gap:5px;margin:12px 0}.e-side a{color:#14657a}.e-side button{text-align:left}
.e-inset g[role=button],.e-labels :deep([role=button]){cursor:pointer;pointer-events:auto}.e-labels :deep([role=button]:hover){background:#fff;outline:1px solid #197795}
.e-side{align-self:start;max-height:clamp(500px,78vh,1000px);overflow:auto;background:white}.e-side header{position:sticky;top:0;background:white;padding:5px 0;z-index:1}
.e-sheet{min-width:0}.e-sheet-tools{display:flex;align-items:center;gap:6px;font-size:12px}.e-sheet-tools button{border:1px solid #d2ddde;background:white;border-radius:3px}.e-sheet-scroll{overflow:auto;max-height:78vh;border:1px solid #dce3e5}.e-sheet-scroll img{display:block;max-width:none;height:auto;cursor:crosshair}
@media(max-width:850px){.electric.inspecting{grid-template-columns:minmax(0,1fr)}.e-side{position:absolute;z-index:4;right:4px;top:50px;width:min(267px,72%);max-height:min(64vh,490px);padding:4px 12px 12px;border:1px solid #cbd6d8;border-radius:5px;box-shadow:0 3px 16px #23394329}.e-status{top:54px;bottom:auto}.e-inset{width:195px}}
.e-stage :deep(.plan-label.active),.e-pin.chosen{color:white!important;background:#7929ad!important;outline:1px solid white}.e-inset .chosen text{fill:#7929ad;font-weight:700}.e-inset .chosen rect,.e-inset .chosen circle{fill:#7929ad;stroke:#fff;stroke-width:1.5}.e-inset .chosen path{stroke:#7929ad;stroke-width:4}.e-side header strong{color:#7929ad}.e-sheet-page{position:relative}.e-sheet-page img{width:100%}.room-picker{display:flex;gap:8px;align-items:center;margin-top:8px}.room-picker select{min-width:0;max-width:75%}
</style>
