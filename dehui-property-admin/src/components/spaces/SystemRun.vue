<template>
  <section class="run" aria-label="系统工况">
    <h4>系统工况</h4>
    <p>按原图图层分步演示。选中步骤可在楼层平面查看对应范围。</p>
    <div class="steps">
      <button v-for="(step,i) in steps" :key="step.layer" :aria-pressed="index===i" @click="inspect(i)">{{ step.title }}</button>
    </div>
    <p v-if="current">{{ current.layer }}</p>
    <button @click="toggle">{{ playing ? '暂停工况' : '播放工况' }}</button>
    <button v-if="current?.object" @click="emit('object',current.object)">查看{{ current.objectName }}</button>
    <button @click="emit('close')">退出工况</button>
  </section>
</template>
<script setup>
import {computed,onMounted,onBeforeUnmount,ref,watch} from 'vue'
const props=defineProps({steps:{type:Array,required:true}})
const emit=defineEmits(['phase','object','close'])
const index=ref(0),playing=ref(!matchMedia('(prefers-reduced-motion: reduce)').matches)
const current=computed(()=>props.steps[index.value])
let timer
function schedule(){
  clearInterval(timer)
  if (playing.value && !document.hidden) timer=setInterval(()=>{index.value=(index.value+1)%props.steps.length},2400)
}
function inspect(i){playing.value=false;index.value=i;schedule()}
function toggle(){playing.value=!playing.value;schedule()}
watch(current,step=>{if(step)emit('phase',step.index)},{immediate:true})
onMounted(()=>{document.addEventListener('visibilitychange',schedule);schedule()})
onBeforeUnmount(()=>{clearInterval(timer);document.removeEventListener('visibilitychange',schedule)})
</script>
<style scoped>
.run{padding:10px;background:#f1f5f5;border-radius:5px;margin:8px 0}.run h4,.run p{margin:3px 0 8px}.steps{display:flex;flex-wrap:wrap;gap:3px}.run button{font:inherit;cursor:pointer;border:1px solid #ccd6d8;background:white;border-radius:4px;padding:4px 7px;margin:2px}.run button[aria-pressed=true]{background:#185a62;color:white}
</style>
