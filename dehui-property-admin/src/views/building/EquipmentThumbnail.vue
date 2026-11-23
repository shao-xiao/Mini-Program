<template>
  <span ref="frame" class="equipment-thumbnail" aria-hidden="true">
    <slot v-if="!ready" />
    <img
      v-if="src"
      :key="src"
      :src="src"
      alt=""
      loading="lazy"
      decoding="async"
      :class="{ ready }"
      @load="ready = true"
      @error="ready = false; src = ''"
    />
  </span>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { normalizeCustomFieldFiles } from '../../utils/customFieldValues'
import request from '../../utils/request'

const props = defineProps({ value: { type: [Array, Object, String, Number], default: null } })
const frame = ref(null)
const nearViewport = ref(false)
const ready = ref(false)
const src = ref('')
let observer

const photoPath = computed(() => {
  for (const file of normalizeCustomFieldFiles(props.value)) {
    if (file.url) {
      try {
        const url = new URL(file.url, window.location.origin)
        if (url.origin === window.location.origin && (
          url.pathname.startsWith('/files/public/') || /^\/api\/files\/\d+$/.test(url.pathname)
        )) return url.pathname
      } catch { /* Some historical records only contain a file ID. */ }
    }
    if (file.fileId) return `/api/files/${file.fileId}`
  }
  return ''
})

onMounted(() => {
  if (!('IntersectionObserver' in window)) {
    nearViewport.value = true
    return
  }
  observer = new IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting)) {
      nearViewport.value = true
      observer.disconnect()
    }
  }, { rootMargin: '120px' })
  observer.observe(frame.value)
})
onBeforeUnmount(() => observer?.disconnect())

watch([photoPath, nearViewport], async ([path, visible], _, onCleanup) => {
  src.value = ''
  ready.value = false
  if (!visible || !path) return
  if (path.startsWith('/files/public/')) {
    src.value = path
    return
  }
  const controller = new AbortController()
  let objectUrl
  onCleanup(() => {
    controller.abort()
    if (objectUrl) URL.revokeObjectURL(objectUrl)
  })
  try {
    const blob = await request.get(path.slice(4), { responseType: 'blob', silent: true, signal: controller.signal })
    if (!controller.signal.aborted) {
      objectUrl = URL.createObjectURL(blob)
      src.value = objectUrl
    }
  } catch { /* Keep the equipment illustration when a photo is unavailable. */ }
})
</script>

<style scoped>
.equipment-thumbnail { width: 80px; height: 64px; position: relative; display: inline-grid; place-items: center; flex: none; }
.equipment-thumbnail img { position: absolute; inset: 0; height: 100%; width: 100%; object-fit: contain; border-radius: 5px; opacity: 0; }
.equipment-thumbnail img.ready { opacity: 1; }
</style>
