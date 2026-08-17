<template>
  <div class="custom-field-cell-editor">
    <CustomFieldValueInput
      v-if="editing"
      ref="controlRef"
      v-model="draftValue"
      :field="field"
      :disabled="busy"
      @change="handleChange"
      @visible-change="handleVisibleChange"
      @enter="commitEditing"
      @escape="cancelEditing"
      @blur="handleBlur"
    />
    <el-icon v-if="busy" class="custom-field-saving" aria-label="正在保存"><Loading /></el-icon>
    <span v-if="saveError" class="custom-field-save-error" role="alert">{{ saveError }}</span>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { isMultiValueFieldType, isOptionFieldType, normalizeFieldType } from '../../config/fieldTypes.js'
import { resolveCellValue } from '../../utils/cellRenderer.js'
import { validateCustomFieldValue } from '../../utils/customFieldValues.js'
import CustomFieldValueInput from './CustomFieldValueInput.vue'

defineOptions({ name: 'CustomFieldCellEditor' })

const props = defineProps({
  field: { type: Object, required: true },
  row: { type: Object, required: true },
  editable: { type: Boolean, default: false },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['save', 'cancel', 'error'])
const controlRef = ref(null)
const editing = ref(false)
const draftValue = ref('')
const committing = ref(false)
const saveError = ref('')
const type = computed(() => normalizeFieldType(props.field.type))
const isOption = computed(() => isOptionFieldType(type.value))
const isMultiple = computed(() => isMultiValueFieldType(type.value))
const busy = computed(() => props.saving || committing.value)

watch(() => props.saving, saving => {
  if (!saving) committing.value = false
})

onMounted(() => startEditing())

async function startEditing() {
  if (!props.editable || editing.value) return
  try {
    draftValue.value = cloneValue(resolveCellValue(props.row, props.field))
    if (isMultiple.value && !Array.isArray(draftValue.value)) draftValue.value = []
    if (type.value === 'checkbox') draftValue.value = Boolean(draftValue.value)
    saveError.value = ''
    committing.value = false
    editing.value = true
    await nextTick()
    controlRef.value?.focus?.()
  } catch (error) {
    editing.value = false
    emit('error', error)
  }
}

function handleChange() {
  if (!isMultiple.value || ['image', 'attachment'].includes(type.value)) commitEditing()
}

function handleVisibleChange(visible) {
  if (!visible && isMultiple.value) commitEditing()
}

function handleBlur() {
  if (!['long-text'].includes(type.value)) commitEditing()
}

function commitEditing() {
  if (!editing.value || committing.value) return
  const previousValue = resolveCellValue(props.row, props.field)
  if (valuesEqual(previousValue, draftValue.value)) {
    cancelEditing()
    return
  }
  const validationError = validateCustomFieldValue(props.field, draftValue.value)
  if (validationError) {
    saveError.value = validationError
    nextTick(() => controlRef.value?.focus?.())
    return
  }
  committing.value = true
  saveError.value = ''
  emit('save', cloneValue(draftValue.value), completeSaving)
}

function completeSaving(result = { success: true }) {
  const normalized = typeof result === 'object' && result !== null ? result : { success: result !== false }
  committing.value = false
  if (normalized.success !== false) return
  saveError.value = normalized.message || '保存失败，请稍后重试'
  nextTick(() => controlRef.value?.focus?.())
}

function cancelEditing() {
  editing.value = false
  committing.value = false
  emit('cancel')
}

function cloneValue(value) {
  return Array.isArray(value) ? [...value] : value ?? ''
}

function valuesEqual(left, right) {
  if (Array.isArray(left) || Array.isArray(right)) return JSON.stringify(left || []) === JSON.stringify(right || [])
  return String(left ?? '') === String(right ?? '')
}
</script>

<style scoped>
.custom-field-cell-editor {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-height: 28px;
}

.custom-field-saving {
  flex: none;
  color: var(--el-color-primary);
  animation: custom-field-rotating 1.2s linear infinite;
}

.custom-field-save-error {
  color: var(--el-color-danger);
  font-size: 12px;
  white-space: nowrap;
}

@keyframes custom-field-rotating {
  to { transform: rotate(360deg); }
}
</style>
