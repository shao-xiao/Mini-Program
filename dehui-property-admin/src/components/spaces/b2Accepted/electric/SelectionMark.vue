<template>
  <svg class="selection-mark" aria-hidden="true" :viewBox="viewBox" preserveAspectRatio="none">
    <template v-for="(s,i) in shapes" :key="i">
      <path v-if="s.type!=='point'" :d="path(s)" :fill="s.type==='area'?'#823bb977':'none'" stroke="white" stroke-width="5" vector-effect="non-scaling-stroke"/>
      <path v-if="s.type!=='point'" :d="path(s)" fill="none" stroke="#7929ad" stroke-width="3" vector-effect="non-scaling-stroke"/>
      <g v-else :transform="'translate('+s.points[0].join(' ')+')'">
        <circle :r="radius" fill="#8138b95c" stroke="white" stroke-width="5" vector-effect="non-scaling-stroke"/>
        <circle :r="radius" fill="none" stroke="#7929ad" stroke-width="3" vector-effect="non-scaling-stroke"/>
      </g>
    </template>
  </svg>
</template>
<script setup>
defineProps({shapes:Array,viewBox:String,radius:{type:Number,default:8}})
function path(s){return 'M'+s.points.map(p=>p.join(' ')).join('L')+(s.type==='area'?'Z':'')}
</script>
<style scoped>.selection-mark{position:absolute;inset:0;width:100%;height:100%;pointer-events:none;overflow:hidden}</style>
