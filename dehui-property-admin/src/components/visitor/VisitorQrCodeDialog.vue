<template>
  <el-dialog
    :model-value="modelValue"
    :title="visitor?.qrCodeFileId ? '更换门禁二维码' : '上传门禁二维码'"
    width="460px"
    destroy-on-close
    @close="close"
  >
    <div class="visitor-summary">
      <strong>{{ visitor?.visitorName || '访客' }}</strong>
      <span>{{ visitor?.code || '' }}</span>
    </div>

    <div class="qr-panel">
      <div v-if="previewLoading" class="qr-placeholder">正在加载二维码…</div>
      <img v-else-if="previewUrl" class="qr-preview" :src="previewUrl" alt="访客门禁二维码" />
      <div v-else class="qr-placeholder">审核通过后上传一张清晰的门禁二维码</div>
    </div>

    <input
      ref="fileInput"
      class="hidden-input"
      type="file"
      accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
      @change="handleSelection"
    />
    <div class="upload-actions">
      <el-button type="primary" :loading="uploading" @click="fileInput?.click()">
        {{ visitor?.qrCodeFileId ? '选择新二维码' : '选择二维码图片' }}
      </el-button>
      <span>PNG/JPG/WebP，单张不超过 5MB</span>
    </div>
    <el-alert
      class="security-tip"
      type="info"
      :closable="false"
      show-icon
      title="二维码按访客预约受保护，仅预约申请人、所属租户负责人和有权限员工可读取。"
    />

    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadCustomFieldAttachment } from '../../api/customFields'
import request from '../../utils/request'

const props = defineProps({
  modelValue: Boolean,
  visitor: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])
const fileInput = ref()
const uploading = ref(false)
const previewLoading = ref(false)
const previewUrl = ref('')
let objectUrl = ''

function releasePreview() {
  if (objectUrl) URL.revokeObjectURL(objectUrl)
  objectUrl = ''
  previewUrl.value = ''
}

function setPreview(blob) {
  releasePreview()
  objectUrl = URL.createObjectURL(blob)
  previewUrl.value = objectUrl
}

async function loadCurrentPreview() {
  releasePreview()
  const url = props.visitor?.qrCodeUrl
  if (!props.modelValue || !url) return
  previewLoading.value = true
  try {
    const endpoint = String(url).replace(/^\/api/, '')
    const blob = await request.get(endpoint, { responseType: 'blob', silent: true })
    setPreview(blob)
  } catch {
    ElMessage.warning('当前二维码预览加载失败，可重新上传')
  } finally {
    previewLoading.value = false
  }
}

function validateFile(file) {
  if (!file) return '请选择二维码图片'
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return '仅支持 PNG、JPG 或 WebP 图片'
  if (file.size > 5 * 1024 * 1024) return '二维码图片不能超过 5MB'
  return ''
}

async function handleSelection(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  const error = validateFile(file)
  if (error) {
    ElMessage.error(error)
    return
  }
  if (!props.visitor?.id || props.visitor.status !== 'REGISTERED') {
    ElMessage.error('仅审核通过且尚未入场的预约可以上传二维码')
    return
  }

  uploading.value = true
  try {
    const uploaded = await uploadCustomFieldAttachment(file, {
      fieldType: 'IMAGE',
      workspaceKey: 'visitors'
    })
    const fileId = Number(uploaded?.fileId ?? uploaded?.id)
    if (!Number.isInteger(fileId) || fileId <= 0) throw new Error('上传响应缺少文件ID')
    await request.put(`/visitors/${props.visitor.id}/qr-code`, { fileId })
    setPreview(file)
    ElMessage.success(props.visitor.qrCodeFileId ? '门禁二维码已更换' : '门禁二维码已上传')
    emit('saved')
  } catch (error) {
    if (!error?.requestMessageShown) {
      ElMessage.error(error?.response?.data?.message || error?.message || '二维码上传失败')
    }
  } finally {
    uploading.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

watch(
  () => [props.modelValue, props.visitor?.id, props.visitor?.qrCodeFileId],
  loadCurrentPreview,
  { immediate: true }
)

onBeforeUnmount(releasePreview)
</script>

<style scoped>
.visitor-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--el-text-color-secondary);
}

.visitor-summary strong {
  color: var(--el-text-color-primary);
  font-size: 16px;
}

.qr-panel {
  display: grid;
  min-height: 230px;
  margin: 18px 0;
  place-items: center;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-extra-light);
}

.qr-preview {
  width: 210px;
  height: 210px;
  object-fit: contain;
  image-rendering: auto;
}

.qr-placeholder {
  max-width: 250px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

.hidden-input {
  display: none;
}

.upload-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-actions span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.security-tip {
  margin-top: 16px;
}
</style>
