<template>
  <div v-if="isImage" class="image-value" :class="{ compact: mode === 'list' }">
    <el-image
      v-for="(file, index) in visibleImages"
      :key="file.fileId || file.url || index"
      class="thumbnail"
      :src="file.url"
      :alt="imageAlt || file.name || field.label || '图片'"
      :preview-src-list="previewUrls"
      :initial-index="index"
      fit="cover"
      preview-teleported
      @error="handleImageError(file, index)"
    />
    <span v-if="hiddenCount" class="more-count">+{{ hiddenCount }}</span>
    <span v-if="!visibleImages.length && !imageLoading">-</span>
  </div>
  <div v-else-if="isAttachment" class="attachment-value" :class="{ compact: mode === 'list' }">
    <template v-if="mode === 'list'">
      <a v-if="files[0]?.url" :href="files[0].url" target="_blank" rel="noopener noreferrer" @click.stop>
        {{ files[0].name || '附件' }}
      </a>
      <span v-else-if="files[0]">{{ files[0].name || '附件' }}</span>
      <span v-else>-</span>
      <span v-if="files.length > 1" class="count">等{{ files.length }}个附件</span>
    </template>
    <div v-else v-for="(file, index) in files" :key="file.fileId || file.url || index" class="attachment-row">
      <span class="file-name">{{ file.name || '未命名文件' }}</span>
      <span class="file-size">{{ formatSize(file.size) }}</span>
      <a v-if="file.url" :href="file.url" target="_blank" rel="noopener noreferrer">预览</a>
      <a v-if="file.url" :href="file.url" :download="file.name">下载</a>
    </div>
  </div>
  <span v-else>{{ fallback }}</span>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { normalizeCustomFieldType } from '../../utils/customFieldSchema'
import { formatCustomFieldValue, normalizeCustomFieldFiles } from '../../utils/customFieldValues'
import request from '../../utils/request'

const props = defineProps({
  field: { type: Object, required: true },
  value: { type: [String, Number, Boolean, Array, Object], default: null },
  mode: { type: String, default: 'list' },
  imageAlt: { type: String, default: '' }
})
const emit = defineEmits(['image-state'])

const type = computed(() => normalizeCustomFieldType(props.field.type || props.field.fieldType))
const isImage = computed(() => type.value === 'image')
const isAttachment = computed(() => type.value === 'attachment')
const sourceFiles = computed(() => normalizeCustomFieldFiles(props.value))
const protectedUrls = ref({})
const failedImageKeys = ref(new Set())
const imageLoading = ref(false)
const objectUrls = new Set()
const files = computed(() => sourceFiles.value.map((file) => {
  const key = file.fileId || file.url
  if (!isProtectedUrl(file.url)) return file
  return { ...file, url: protectedUrls.value[key] || '' }
}))
const renderableImages = computed(() => files.value.filter((file, index) => (
  Boolean(file.url) && !failedImageKeys.value.has(imageKey(file, index))
)))
const previewUrls = computed(() => renderableImages.value.map(file => file.url))
const visibleImages = computed(() => renderableImages.value.slice(0, props.mode === 'list' ? 2 : 30))
const hiddenCount = computed(() => Math.max(0, renderableImages.value.length - visibleImages.value.length))
const fallback = computed(() => formatCustomFieldValue(props.field, props.value))
let imageLoadRevision = 0

const isProtectedUrl = (url) => typeof url === 'string'
  && (url.startsWith('/api/files/') || url.startsWith('/api/mobile/files/'))

function imageKey(file, index) {
  return String(file.fileId || file.url || index)
}

function emitImageState() {
  if (!isImage.value) return
  emit('image-state', {
    loading: imageLoading.value,
    hasImage: renderableImages.value.length > 0
  })
}

function handleImageError(file, index) {
  const next = new Set(failedImageKeys.value)
  next.add(imageKey(file, index))
  failedImageKeys.value = next
  emitImageState()
}

const releaseObjectUrls = () => {
  objectUrls.forEach(url => URL.revokeObjectURL(url))
  objectUrls.clear()
}

watch(
  () => sourceFiles.value.map(file => `${file.fileId || ''}:${file.url || ''}`).join('|'),
  async () => {
    const revision = ++imageLoadRevision
    releaseObjectUrls()
    protectedUrls.value = {}
    failedImageKeys.value = new Set()
    const protectedFiles = sourceFiles.value.filter(file => isProtectedUrl(file.url))
    imageLoading.value = protectedFiles.length > 0
    emitImageState()
    const next = {}
    const createdUrls = []
    await Promise.all(protectedFiles.map(async (file) => {
      if (!isProtectedUrl(file.url)) return
      const key = file.fileId || file.url
      try {
        const endpoint = file.url.replace(/^\/api/, '')
        const blob = await request.get(endpoint, { responseType: 'blob', silent: true })
        const url = URL.createObjectURL(blob)
        createdUrls.push(url)
        next[key] = url
      } catch {
        next[key] = ''
      }
    }))
    if (revision !== imageLoadRevision) {
      createdUrls.forEach(url => URL.revokeObjectURL(url))
      return
    }
    createdUrls.forEach(url => objectUrls.add(url))
    protectedUrls.value = next
    imageLoading.value = false
    emitImageState()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  imageLoadRevision += 1
  releaseObjectUrls()
})

const formatSize = (size) => {
  const bytes = Number(size) || 0
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.image-value { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.thumbnail { width: 72px; height: 72px; border: 1px solid var(--el-border-color-lighter); border-radius: 5px; }
.compact .thumbnail { width: 34px; height: 34px; }
.more-count, .count, .file-size { color: var(--el-text-color-secondary); font-size: 12px; }
.attachment-value { display: grid; gap: 6px; }
.attachment-value.compact { display: flex; align-items: center; gap: 5px; min-width: 0; }
.attachment-row { display: flex; align-items: center; gap: 10px; padding: 7px 9px; border-bottom: 1px solid var(--el-border-color-lighter); }
.file-name { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
