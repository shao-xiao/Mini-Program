<template>
  <header><b>地下二层 · {{tab}}模型</b><nav><button v-for="name in modes" :key="name" :aria-pressed="mode===name" @click="mode=name;paper=false">{{name}}</button></nav>
    <button @click="documentFull">全屏</button><button v-if="meta" @click="paper=!paper">{{paper?'返回模型':'核对原图'}}</button></header>
  <p v-if="error" role="alert">{{error}} <button @click="load">重新读取</button></p><p v-else-if="!meta" role="status">正在读取 B2 {{tab}}模型…</p>
  <template v-else><nav v-if="paper"><a v-for="(s,code) in meta.sources" :key="code" :href="s.file" target="_blank" rel="noopener">{{code}} 原图</a></nav>
  <HvacInstallation v-if="keyName==='hvac'" :key="mode" :name="mode"/>
  <Electric v-else-if="keyName==='electric'" :mode="mode" :key="mode" :sheet-view="paper"/>
  <Service v-else :key="mode" :mode="mode" :sheet-view="paper"/></template>
</template>
<script setup>
import {ref,shallowRef,onMounted} from 'vue'
import HvacInstallation from './HvacInstallation.vue'
import Electric from './electric/Electric.vue'
import Service from './services/Service.vue'
import {loadAssets} from './assets'
const systems={暖通:['hvac','普通通风','防烟与排烟'],强电:['electric','动力配电','普通照明','应急照明'],消防:['fire','消防报警与联动','消火栓','自动喷淋','消防物联网'],弱电:['weak','综合弱电','综合安防']}
const tab=new URLSearchParams(location.search).get('tab')
const [keyName,...modes]=systems[tab]||[]
const mode=ref(modes[0]),meta=shallowRef(null),paper=ref(false),error=ref('')
async function load(){
  error.value=''
  try{if(!keyName)throw Error('专业入口无效');meta.value=await loadAssets(keyName)}
  catch(e){error.value=e.message||'读取模型失败'}
}
function documentFull(){if(document.fullscreenElement)document.exitFullscreen();else document.documentElement.requestFullscreen?.()}
onMounted(load)
</script>
<style>
*{box-sizing:border-box}body{color:#24343d;font:14px/1.5 -apple-system,BlinkMacSystemFont,sans-serif}#app>header,#app>nav,#app>header nav{display:flex;flex-wrap:wrap;align-items:center;gap:6px}#app>header,#app>nav{padding:8px 0}#app>header>b{margin-right:auto}#app>header button,#app>nav a{border:1px solid #d1dadd;border-radius:4px;background:white;color:inherit;padding:5px 9px;cursor:pointer}#app>header button[aria-pressed=true]{background:#e2eeee;font-weight:600}
@media(max-width:760px){#app>header nav{order:2;width:100%}#app .e-stage,#app .installation .stage{height:65svh;min-height:360px}#app .e-inset{max-width:43%}#app .stage canvas,#app .e-stage canvas{touch-action:none}}
</style>
