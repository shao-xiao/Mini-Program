<template>
  <div class="custom-field-input">
    <template v-if="isFileField">
      <div v-if="isImageField && files.length" class="image-grid">
        <div v-for="(file, index) in files" :key="file.fileId || file.url || index" class="image-item">
          <div v-if="isPreviewLoading(file, index)" class="image-preview image-loading">图片加载中</div>
          <el-image
            v-else-if="previewFileUrl(file, index)"
            class="image-preview"
            :src="previewFileUrl(file, index)"
            :alt="file.name || '图片'"
            :preview-src-list="imagePreviewUrls"
            :initial-index="imagePreviewIndex(file, index)"
            fit="cover"
            preview-teleported
            @error="handlePreviewError(file, index)"
          >
            <template #error><div class="image-error">图片不可用</div></template>
          </el-image>
          <div v-else class="image-preview"><div class="image-error">图片不可用</div></div>
          <div v-if="!disabled" class="file-actions">
            <el-button link size="small" :disabled="index === 0" @click="moveFile(index, -1)">前移</el-button>
            <el-button link size="small" :disabled="index === files.length - 1" @click="moveFile(index, 1)">后移</el-button>
            <el-button link size="small" type="danger" @click="removeFile(index)">删除</el-button>
          </div>
        </div>
      </div>
      <div v-else-if="files.length" class="attachment-list">
        <div v-for="(file, index) in files" :key="file.fileId || file.url || index" class="attachment-item">
          <div class="attachment-main">
            <span class="attachment-name">{{ file.name || '未命名文件' }}</span>
            <span class="attachment-size">{{ formatSize(file.size) }}</span>
          </div>
          <div class="attachment-actions">
            <a v-if="file.url" :href="file.url" target="_blank" rel="noopener noreferrer">预览</a>
            <a v-if="file.url" :href="file.url" :download="file.name">下载</a>
            <template v-if="!disabled">
              <el-button link size="small" :disabled="index === 0" @click="moveFile(index, -1)">上移</el-button>
              <el-button link size="small" :disabled="index === files.length - 1" @click="moveFile(index, 1)">下移</el-button>
              <el-button link size="small" type="danger" @click="removeFile(index)">删除</el-button>
            </template>
          </div>
        </div>
      </div>

      <div v-if="!disabled" class="upload-panel">
        <input
          ref="fileInput"
          class="hidden-input"
          type="file"
          multiple
          :accept="accept"
          @change="handleFileSelection"
        />
        <el-button :disabled="uploading || files.length >= maxFiles" @click="fileInput?.click()">
          {{ isImageField ? '+ 添加图片' : '+ 添加附件' }}
        </el-button>
        <span class="upload-tip">最多上传{{ maxFiles }}个，可连续追加</span>
      </div>
      <div v-if="uploading" class="upload-progress">
        <el-progress :percentage="progress" :stroke-width="8" />
        <span>正在上传 {{ uploadingName }}</span>
      </div>
      <div v-if="uploadError" class="upload-error">{{ uploadError }}</div>
    </template>

    <input
      v-else-if="type === 'text'"
      type="text"
      class="native-control"
      :value="modelValue"
      :disabled="disabled"
      :placeholder="placeholder"
      @input="updateValue($event.target.value)"
    />
    <textarea
      v-else-if="type === 'textarea'"
      class="native-control native-textarea"
      :value="modelValue"
      rows="3"
      :disabled="disabled"
      :placeholder="placeholder"
      @input="updateValue($event.target.value)"
    />
    <input
      v-else-if="type === 'number'"
      type="number"
      class="native-control"
      :value="modelValue"
      :disabled="disabled"
      @input="updateValue($event.target.value === '' ? null : Number($event.target.value))"
    />
    <el-select
      v-else-if="type === 'select'"
      :model-value="modelValue"
      :disabled="disabled"
      clearable
      style="width: 100%"
      @update:model-value="updateValue"
    >
      <el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value" />
    </el-select>
    <el-select
      v-else-if="type === 'multiSelect'"
      :model-value="modelValue"
      :disabled="disabled"
      multiple
      clearable
      style="width: 100%"
      @update:model-value="updateValue"
    >
      <el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value" />
    </el-select>
    <el-date-picker
      v-else-if="type === 'date'"
      :model-value="modelValue"
      type="date"
      value-format="YYYY-MM-DD"
      :disabled="disabled"
      style="width: 100%"
      @update:model-value="updateValue"
    />
    <el-date-picker
      v-else-if="type === 'datetime'"
      :model-value="modelValue"
      type="datetime"
      value-format="YYYY-MM-DD HH:mm:ss"
      :disabled="disabled"
      style="width: 100%"
      @update:model-value="updateValue"
    />
    <el-switch
      v-else-if="type === 'boolean'"
      :model-value="Boolean(modelValue)"
      :disabled="disabled"
      @update:model-value="updateValue"
    />
    <el-input v-else :model-value="modelValue" :disabled="disabled" @update:model-value="updateValue" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadCustomFieldAttachment } from '../../api/customFields'
import { normalizeCustomFieldFiles } from '../../utils/customFieldValues'
import { normalizeCustomFieldType } from '../../utils/customFieldSchema'
import request from '../../utils/request'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Boolean, Array, Object], default: '' },
  disabled: Boolean,
  placeholder: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'change', 'uploading-change'])

const IMAGE_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'webp'])
const ATTACHMENT_EXTENSIONS = new Set([
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'csv',
  'jpg', 'jpeg', 'png', 'webp', 'zip'
])
const BLOCKED_EXTENSIONS = new Set(['exe', 'dll', 'bat', 'cmd', 'com', 'scr', 'msi', 'ps1', 'sh', 'php', 'jsp', 'js', 'html', 'svg'])

const type = computed(() => normalizeCustomFieldType(props.field.type || props.field.fieldType))
const isFileField = computed(() => ['image', 'attachment'].includes(type.value))
const isImageField = computed(() => type.value === 'image')
const files = computed(() => normalizeCustomFieldFiles(props.modelValue))
const options = computed(() => {
  const source = props.field.options || props.field.config?.options || []
  return source.map((option) => typeof option === 'object'
    ? { label: option.label ?? option.value, value: option.value ?? option.label }
    : { label: option, value: option })
})
const maxFiles = computed(() => {
  const fallback = isImageField.value ? 9 : 10
  return Math.min(30, Math.max(1, Number(props.field.config?.maxFiles ?? props.field.maxFiles ?? fallback) || fallback))
})
const accept = computed(() => isImageField.value
  ? '.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp'
  : '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.jpg,.jpeg,.png,.webp,.zip')
const fileInput = ref()
const uploading = ref(false)
const progress = ref(0)
const uploadingName = ref('')
const uploadError = ref('')
const protectedPreviewUrls = ref({})
const previewLoadingKeys = ref(new Set())
const failedPreviewKeys = ref(new Set())
const previewObjectUrls = new Set()
let previewLoadRevision = 0

const isProtectedUrl = (url) => typeof url === 'string'
  && (url.startsWith('/api/files/') || url.startsWith('/api/mobile/files/'))

const previewKey = (file, index) => String(file.fileId || file.url || index)

const previewFileUrl = (file, index) => {
  const key = previewKey(file, index)
  if (failedPreviewKeys.value.has(key)) return ''
  if (!isProtectedUrl(file.url)) return file.url || ''
  return protectedPreviewUrls.value[key] || ''
}

const isPreviewLoading = (file, index) => previewLoadingKeys.value.has(previewKey(file, index))

const handlePreviewError = (file, index) => {
  const next = new Set(failedPreviewKeys.value)
  next.add(previewKey(file, index))
  failedPreviewKeys.value = next
}

const releasePreviewObjectUrls = () => {
  previewObjectUrls.forEach(url => URL.revokeObjectURL(url))
  previewObjectUrls.clear()
}

watch(
  () => isImageField.value
    ? files.value.map(file => `${file.fileId || ''}:${file.url || ''}`).join('|')
    : '',
  async () => {
    const revision = ++previewLoadRevision
    releasePreviewObjectUrls()
    protectedPreviewUrls.value = {}
    failedPreviewKeys.value = new Set()
    const protectedFiles = isImageField.value
      ? files.value.filter(file => isProtectedUrl(file.url))
      : []
    previewLoadingKeys.value = new Set(protectedFiles.map(previewKey))
    const next = {}
    const createdUrls = []
    await Promise.all(protectedFiles.map(async (file, index) => {
      const key = previewKey(file, index)
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
    if (revision !== previewLoadRevision) {
      createdUrls.forEach(url => URL.revokeObjectURL(url))
      return
    }
    createdUrls.forEach(url => previewObjectUrls.add(url))
    protectedPreviewUrls.value = next
    previewLoadingKeys.value = new Set()
  },
  { immediate: true }
)

const updateValue = (value) => {
  emit('update:modelValue', value)
  emit('change', value)
}

const fileExtension = (name = '') => name.includes('.') ? name.split('.').pop().toLowerCase() : ''

const validateSelectedFile = (file) => {
  const extension = fileExtension(file.name)
  if (BLOCKED_EXTENSIONS.has(extension)) return `不允许上传 .${extension} 文件`
  const allowed = isImageField.value ? IMAGE_EXTENSIONS : ATTACHMENT_EXTENSIONS
  if (!allowed.has(extension)) return `不支持 .${extension || '未知'} 文件`
  if (isImageField.value && !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return '图片类型与文件扩展名不匹配'
  if (type.value === 'attachment' && extension !== 'zip' && !file.type) return '无法识别文件类型'
  return ''
}

const handleFileSelection = async (event) => {
  const selected = Array.from(event.target.files || [])
  event.target.value = ''
  uploadError.value = ''
  if (!selected.length) return
  if (files.value.length + selected.length > maxFiles.value) {
    uploadError.value = `最多上传${maxFiles.value}个文件`
    return
  }
  const next = [...files.value]
  uploading.value = true
  emit('uploading-change', true)
  try {
    for (let index = 0; index < selected.length; index += 1) {
      const file = selected[index]
      const error = validateSelectedFile(file)
      if (error) {
        uploadError.value = `${file.name}：${error}`
        continue
      }
      uploadingName.value = file.name
      progress.value = Math.round((index / selected.length) * 100)
      try {
        const response = await uploadCustomFieldAttachment(file, {
          fieldType: isImageField.value ? 'IMAGE' : 'ATTACHMENT',
          workspaceKey: props.field.workspaceKey,
          onUploadProgress: (progressEvent) => {
            if (progressEvent.total) {
              const current = progressEvent.loaded / progressEvent.total
              progress.value = Math.round(((index + current) / selected.length) * 100)
            }
          }
        })
        const uploaded = response?.data || response
        const fileId = Number(uploaded?.fileId ?? uploaded?.id)
        if (!Number.isInteger(fileId) || fileId <= 0) throw new Error('上传响应缺少文件ID')
        next.push({
          fileId,
          id: fileId,
          name: uploaded.name || file.name,
          url: uploaded.url || '',
          mimeType: uploaded.mimeType || file.type,
          size: Number(uploaded.size) || file.size,
          sortOrder: next.length
        })
        updateValue(next.map((item, order) => ({ ...item, sortOrder: order })))
      } catch (error) {
        uploadError.value = `${file.name}：${error?.message || '上传失败'}`
      }
    }
    progress.value = 100
  } finally {
    uploading.value = false
    uploadingName.value = ''
    emit('uploading-change', false)
    if (uploadError.value) ElMessage.warning('部分文件上传失败，失败文件未加入待保存列表')
  }
}

const removeFile = (index) => updateValue(files.value.filter((_, itemIndex) => itemIndex !== index))

const moveFile = (index, offset) => {
  const target = index + offset
  if (target < 0 || target >= files.value.length) return
  const next = [...files.value]
  ;[next[index], next[target]] = [next[target], next[index]]
  updateValue(next.map((file, order) => ({ ...file, sortOrder: order })))
}

const imagePreviewUrls = computed(() => files.value
  .map((file, index) => previewFileUrl(file, index))
  .filter(Boolean))

const imagePreviewIndex = (file, index) => Math.max(0, imagePreviewUrls.value.indexOf(previewFileUrl(file, index)))

const formatSize = (size) => {
  const bytes = Number(size) || 0
  if (!bytes) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

onBeforeUnmount(() => {
  previewLoadRevision += 1
  releasePreviewObjectUrls()
})

defineExpose({ uploading })
</script>

<style scoped>
.custom-field-input { width: 100%; }
.native-control { box-sizing: border-box; width: 100%; min-height: 32px; padding: 0 11px; border: 1px solid var(--el-border-color); border-radius: 4px; color: var(--el-text-color-primary); background: var(--el-bg-color); font: inherit; outline: none; }
.native-control:focus { border-color: var(--el-color-primary); }
.native-textarea { padding-block: 7px; resize: vertical; }
.hidden-input { display: none; }
.image-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(112px, 1fr)); gap: 10px; margin-bottom: 10px; }
.image-item { overflow: hidden; border: 1px solid var(--el-border-color); border-radius: 6px; background: #fff; }
.image-preview { display: block; width: 100%; height: 96px; }
.image-loading { display: grid; place-items: center; color: var(--el-text-color-secondary); font-size: 12px; background: var(--el-fill-color-light); }
.image-error { display: grid; width: 100%; height: 100%; place-items: center; color: var(--el-text-color-secondary); font-size: 12px; }
.file-actions { display: flex; justify-content: center; padding: 2px 4px; border-top: 1px solid var(--el-border-color-lighter); }
.attachment-list { display: grid; gap: 7px; margin-bottom: 10px; }
.attachment-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 10px; border: 1px solid var(--el-border-color-lighter); border-radius: 5px; background: var(--el-fill-color-light); }
.attachment-main { min-width: 0; }
.attachment-name { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attachment-size { color: var(--el-text-color-secondary); font-size: 12px; }
.attachment-actions { display: flex; flex: none; align-items: center; gap: 8px; font-size: 13px; }
.upload-panel { display: flex; align-items: center; gap: 10px; }
.upload-tip { color: var(--el-text-color-secondary); font-size: 12px; }
.upload-progress { margin-top: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.upload-error { margin-top: 6px; color: var(--el-color-danger); font-size: 12px; }
</style>
