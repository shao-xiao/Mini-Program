<template>
  <div ref="barRef" class="floating-table-scrollbar" style="display: none" aria-label="表格横向滚动工具">
    <span class="scrollbar-label">左右拖动查看更多列</span>
    <button type="button" aria-label="向左滚动表格" @click="move(-1)">‹</button>
    <div ref="trackRef" class="floating-scrollbar-track" tabindex="0" role="region" aria-label="横向滚动表格，可使用左右方向键" @scroll="onTrackScroll" @keydown.left.prevent="move(-1)" @keydown.right.prevent="move(1)" @keydown.home.prevent="moveTo(0)" @keydown.end.prevent="moveTo(Infinity)">
      <div ref="spacerRef" class="floating-scrollbar-spacer"></div>
    </div>
    <button type="button" aria-label="向右滚动表格" @click="move(1)">›</button>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({ table: Object, rows: Array, columns: Array })
const barRef = ref(null)
const trackRef = ref(null)
const spacerRef = ref(null)
let tableElement
let bodyWrap
let observer
let frame = 0
let disposed = false

function schedule() {
  if (!frame && !disposed) frame = requestAnimationFrame(measure)
}
function measure() {
  frame = 0
  if (!tableElement || !bodyWrap || !barRef.value || !trackRef.value) return
  const rect = tableElement.getBoundingClientRect()
  const left = Math.max(8, rect.left)
  const right = Math.min(window.innerWidth - 8, rect.right)
  const max = Math.max(0, bodyWrap.scrollWidth - bodyWrap.clientWidth)
  const visible = max > 1 && rect.top < window.innerHeight - 90 && rect.bottom > window.innerHeight - 12 && right - left > 180 && rect.width > 0
  const bar = barRef.value
  bar.style.display = visible ? 'flex' : 'none'
  if (!visible) return
  // Position writes are grouped in one animation frame, outside Vue's table rendering.
  bar.style.left = `${left}px`
  bar.style.width = `${right - left}px`
  spacerRef.value.style.width = `${trackRef.value.clientWidth + max}px`
  if (Math.abs(trackRef.value.scrollLeft - bodyWrap.scrollLeft) > 1) trackRef.value.scrollLeft = bodyWrap.scrollLeft
}
function moveTo(value) {
  if (!bodyWrap) return
  bodyWrap.scrollLeft = Math.max(0, Math.min(value, bodyWrap.scrollWidth - bodyWrap.clientWidth))
  schedule()
}
function move(direction) {
  if (bodyWrap) moveTo(bodyWrap.scrollLeft + direction * Math.max(120, bodyWrap.clientWidth * 0.65))
}
function onTrackScroll() {
  if (bodyWrap && Math.abs(bodyWrap.scrollLeft - trackRef.value.scrollLeft) > 1) bodyWrap.scrollLeft = trackRef.value.scrollLeft
}
async function bindTable() {
  await nextTick()
  if (disposed) return
  observer?.disconnect()
  tableElement = props.table?.$el
  bodyWrap = tableElement?.querySelector?.('.el-table__body-wrapper .el-scrollbar__wrap')
  if (tableElement && bodyWrap && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(schedule)
    observer.observe(tableElement)
    observer.observe(bodyWrap)
    const body = bodyWrap.querySelector('.el-table__body')
    if (body) observer.observe(body)
  }
  if (!bodyWrap && barRef.value) barRef.value.style.display = 'none'
  schedule()
}
watch(() => [props.table, props.rows, props.columns], bindTable)
onMounted(() => {
  bindTable()
  window.addEventListener('resize', schedule, { passive: true })
  document.addEventListener('scroll', schedule, { capture: true, passive: true })
})
onBeforeUnmount(() => {
  disposed = true
  observer?.disconnect()
  cancelAnimationFrame(frame)
  window.removeEventListener('resize', schedule)
  document.removeEventListener('scroll', schedule, true)
})
</script>

<style scoped>
.floating-table-scrollbar { position: fixed; bottom: 12px; z-index: 80; box-sizing: border-box; align-items: center; gap: 8px; height: 36px; padding: 5px 10px; border: 1px solid #d9dfe7; border-radius: 6px; background: rgba(255,255,255,.98); box-shadow: 0 2px 12px rgba(32,33,36,.14); }
.scrollbar-label { flex-shrink: 0; color: #667085; font-size: 11px; }
.floating-table-scrollbar button { flex-shrink: 0; width: 24px; height: 24px; padding: 0; border: 1px solid #e4e7ec; border-radius: 4px; background: #f7f8fa; color: #475467; font: 20px/20px sans-serif; cursor: pointer; }
.floating-table-scrollbar button:hover { color: #d93025; border-color: #e8b2ac; }
.floating-scrollbar-track { flex: 1; min-width: 0; height: 18px; overflow-x: scroll; overflow-y: hidden; scrollbar-width: auto; scrollbar-color: #8c96a6 #eef0f3; }
.floating-scrollbar-track::-webkit-scrollbar { height: 12px; }
.floating-scrollbar-track::-webkit-scrollbar-track { background: #eef0f3; border-radius: 6px; }
.floating-scrollbar-track::-webkit-scrollbar-thumb { background: #8c96a6; border: 2px solid #eef0f3; border-radius: 6px; }
.floating-scrollbar-track::-webkit-scrollbar-thumb:hover { background: #667085; }
.floating-scrollbar-track:focus-visible, .floating-table-scrollbar button:focus-visible { outline: 2px solid #d93025; outline-offset: 2px; }
.floating-scrollbar-spacer { height: 1px; }
@media(max-width:640px) { .scrollbar-label { display: none; } }
</style>
