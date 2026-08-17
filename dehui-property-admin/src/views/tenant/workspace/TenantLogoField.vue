<template>
  <div class="tenant-logo-field">
    <div class="logo-preview" aria-live="polite">
      <span v-if="previewLoading" class="logo-placeholder">加载中</span>
      <img
        v-else-if="displayUrl"
        :src="displayUrl"
        :alt="`${tenantName || '当前租户'}企业 Logo`"
        @error="handlePreviewError"
      />
      <span v-else class="logo-placeholder">{{ configured ? '图片暂不可用' : '未配置' }}</span>
    </div>

    <div class="logo-control">
      <input
        ref="fileInput"
        class="hidden-file-input"
        type="file"
        accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
        aria-label="选择企业 Logo 图片"
        @change="handleFileSelection"
      />
      <div v-if="tenantId" class="logo-actions">
        <el-button :loading="uploading" :disabled="disabled" @click="fileInput?.click()">
          {{ configured ? '替换 Logo' : '上传 Logo' }}
        </el-button>
        <el-button v-if="configured" class="clear-button" :disabled="disabled || uploading" @click="clearLogo">
          清除
        </el-button>
      </div>
      <span v-if="tenantId && fileName" class="logo-file-name" :title="fileName">当前文件：{{ fileName }}</span>
      <span v-if="tenantId" class="logo-hint">PNG、JPG 或 WebP，单张不超过 5MB，建议使用 1:1 透明背景图片</span>
      <span v-else class="logo-hint">请先保存租户后配置企业 Logo</span>
      <span v-if="uploading" class="upload-status" role="status">正在上传 {{ progress }}%</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadCustomFieldAttachment } from '../../../api/customFields.js'
import request from '../../../utils/request.js'

defineOptions({ name: 'TenantLogoField' })

const props = defineProps({
  tenantId: { type: [Number, String], default: null },
  modelValue: { type: [Number, String], default: null },
  previewUrl: { type: String, default: '' },
  fileName: { type: String, default: '' },
  tenantName: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'update:previewUrl', 'update:fileName', 'uploading-change'])
const fileInput = ref(null)
const uploading = ref(false)
const progress = ref(0)
const previewLoading = ref(false)
const displayUrl = ref('')
let objectUrl = ''
let previewRevision = 0
let localUploadFileId = null

const normalizedFileId = computed(() => {
  const value = Number(props.modelValue)
  return Number.isInteger(value) && value > 0 ? value : null
})
const configured = computed(() => Boolean(normalizedFileId.value || props.previewUrl))

watch(
  () => [props.modelValue, props.previewUrl],
  () => {
    if (
      normalizedFileId.value === localUploadFileId
      && objectUrl
      && displayUrl.value === objectUrl
    ) return
    void loadPreview()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  previewRevision += 1
  releaseObjectUrl()
})

function previewSource() {
  if (props.previewUrl) return props.previewUrl
  return normalizedFileId.value ? `/api/files/${normalizedFileId.value}` : ''
}

function protectedEndpoint(source) {
  if (source.startsWith('/api/files/')) return source.replace(/^\/api/, '')
  if (source.startsWith('/files/')) return source
  return ''
}

async function loadPreview() {
  const revision = ++previewRevision
  const source = previewSource()
  localUploadFileId = null
  previewLoading.value = false
  releaseObjectUrl()
  displayUrl.value = ''
  if (!source) return

  const endpoint = protectedEndpoint(source)
  if (!endpoint) {
    displayUrl.value = source
    return
  }

  previewLoading.value = true
  try {
    const blob = await request.get(endpoint, { responseType: 'blob', silent: true })
    if (revision !== previewRevision) return
    setObjectPreview(blob)
  } catch {
    if (revision === previewRevision) displayUrl.value = ''
  } finally {
    if (revision === previewRevision) previewLoading.value = false
  }
}

function validateFile(file) {
  if (!file) return '请选择企业 Logo 图片'
  const extension = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : ''
  if (!['jpg', 'jpeg', 'png', 'webp'].includes(extension)) return '仅支持 PNG、JPG 或 WebP 图片'
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return '图片类型与文件扩展名不匹配'
  if (file.size > 5 * 1024 * 1024) return '企业 Logo 不能超过 5MB'
  return ''
}

async function handleFileSelection(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!props.tenantId) {
    ElMessage.warning('请先保存租户后配置企业 Logo')
    return
  }
  const validationError = validateFile(file)
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  uploading.value = true
  progress.value = 0
  emit('uploading-change', true)
  try {
    const response = await uploadCustomFieldAttachment(file, {
      fieldType: 'IMAGE',
      workspaceKey: 'tenants',
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          progress.value = Math.round((progressEvent.loaded / progressEvent.total) * 100)
        }
      }
    })
    const uploaded = response?.data || response
    const fileId = Number(uploaded?.fileId ?? uploaded?.id)
    if (!Number.isInteger(fileId) || fileId <= 0) throw new Error('上传响应缺少文件ID')

    previewRevision += 1
    setObjectPreview(file)
    localUploadFileId = fileId
    progress.value = 100
    emit('update:modelValue', fileId)
    emit('update:previewUrl', uploaded?.url || '')
    emit('update:fileName', uploaded?.fileName || uploaded?.name || file.name)
    ElMessage.success(configured.value ? '企业 Logo 已替换，保存租户后生效' : '企业 Logo 已上传，保存租户后生效')
  } catch (error) {
    if (!error?.requestMessageShown) {
      ElMessage.error(error?.response?.data?.message || error?.message || '企业 Logo 上传失败')
    }
  } finally {
    uploading.value = false
    emit('uploading-change', false)
  }
}

function clearLogo() {
  previewRevision += 1
  localUploadFileId = null
  releaseObjectUrl()
  displayUrl.value = ''
  previewLoading.value = false
  emit('update:modelValue', null)
  emit('update:previewUrl', '')
  emit('update:fileName', '')
}

function handlePreviewError() {
  releaseObjectUrl()
  displayUrl.value = ''
}

function setObjectPreview(blob) {
  releaseObjectUrl()
  objectUrl = URL.createObjectURL(blob)
  displayUrl.value = objectUrl
}

function releaseObjectUrl() {
  if (objectUrl) URL.revokeObjectURL(objectUrl)
  objectUrl = ''
}
</script>

<style scoped>
.tenant-logo-field {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 64px;
}

.logo-preview {
  display: grid;
  flex: 0 0 64px;
  width: 64px;
  height: 64px;
  overflow: hidden;
  place-items: center;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.logo-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.logo-placeholder {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.logo-control {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.logo-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.logo-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.logo-hint,
.logo-file-name,
.upload-status {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.logo-file-name {
  overflow: hidden;
  color: var(--el-text-color-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hidden-file-input {
  display: none;
}

@media (max-width: 560px) {
  .tenant-logo-field {
    align-items: flex-start;
  }
}
</style>
