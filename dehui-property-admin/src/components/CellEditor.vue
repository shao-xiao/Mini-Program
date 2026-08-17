<template>
  <div class="cell-editor">
    <template v-if="editing">
      <el-select
        v-if="isOption"
        ref="selectRef"
        v-model="draftValue"
        :multiple="isMultiple"
        filterable
        clearable
        :disabled="busy"
        size="small"
        class="cell-control"
        @change="handleOptionChange"
        @visible-change="handleSelectVisible"
        @keyup.esc="cancelEditing"
      >
        <el-option
          v-for="option in options"
          :key="String(option.value)"
          :label="option.label"
          :value="option.value"
        />
      </el-select>

      <el-date-picker
        v-else-if="type === 'date'"
        ref="dateRef"
        v-model="draftValue"
        type="date"
        value-format="YYYY-MM-DD"
        size="small"
        class="cell-control"
        :disabled="busy"
        @change="commitEditing"
      />

      <el-input-number
        v-else-if="isNumeric"
        ref="numberRef"
        v-model="draftValue"
        :precision="field.decimals"
        controls-position="right"
        size="small"
        :disabled="busy"
        class="cell-control"
        @change="commitEditing"
        @keyup.esc="cancelEditing"
      />

      <el-switch
        v-else-if="type === 'checkbox'"
        v-model="draftValue"
        size="small"
        :disabled="busy"
        @change="commitEditing"
      />

      <el-input
        v-else
        ref="inputRef"
        v-model="draftValue"
        size="small"
        class="cell-control"
        :disabled="busy"
        @keyup.enter="commitEditing"
        @keyup.esc="cancelEditing"
        @blur="commitEditing"
      />
    </template>

    <el-icon v-if="busy" class="cell-saving" aria-label="正在保存"><Loading /></el-icon>
    <span v-if="saveError" class="cell-save-error" role="alert">{{ saveError }}</span>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import {
  isMultiValueFieldType,
  isNumericFieldType,
  isOptionFieldType,
  normalizeFieldType
} from '../config/fieldTypes'
import { normalizeCellOption, resolveCellValue } from '../utils/cellRenderer'

defineOptions({ name: 'CellEditor' })

const props = defineProps({
  field: { type: Object, required: true },
  row: { type: Object, required: true },
  editable: { type: Boolean, default: false },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['save', 'cancel', 'error'])
const inputRef = ref(null)
const numberRef = ref(null)
const selectRef = ref(null)
const dateRef = ref(null)
const editing = ref(false)
const draftValue = ref('')
const committing = ref(false)
const saveError = ref('')

const type = computed(() => normalizeFieldType(props.field.type))
const isOption = computed(() => isOptionFieldType(type.value))
const isMultiple = computed(() => isMultiValueFieldType(type.value))
const isNumeric = computed(() => isNumericFieldType(type.value))
const canEdit = computed(() => props.editable && !props.saving && !['formula', 'user', 'attachment', 'relation'].includes(type.value))
const busy = computed(() => props.saving || committing.value)
const options = computed(() => {
  const source = typeof props.field.options === 'function' ? props.field.options() : props.field.options
  return Array.isArray(source) ? source.map(normalizeCellOption) : []
})

watch(() => props.saving, saving => {
  if (!saving) committing.value = false
})

onMounted(() => startEditing())

async function startEditing() {
  if (!canEdit.value || editing.value) return
  try {
    committing.value = false
    saveError.value = ''
    draftValue.value = cloneValue(resolveCellValue(props.row, props.field))
    if (isMultiple.value && !Array.isArray(draftValue.value)) draftValue.value = []
    if (type.value === 'checkbox') draftValue.value = Boolean(draftValue.value)
    editing.value = true
    await nextTick()
    inputRef.value?.focus?.()
    numberRef.value?.focus?.()
    selectRef.value?.focus?.()
    dateRef.value?.focus?.()
  } catch (error) {
    editing.value = false
    emit('error', error)
  }
}

function handleOptionChange() {
  if (!isMultiple.value) commitEditing()
}

function handleSelectVisible(visible) {
  if (!visible && isMultiple.value) commitEditing()
}

function commitEditing() {
  if (!editing.value || committing.value) return
  const previousValue = resolveCellValue(props.row, props.field)
  if (valuesEqual(previousValue, draftValue.value)) {
    cancelEditing()
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
  nextTick(() => {
    inputRef.value?.focus?.()
    numberRef.value?.focus?.()
    selectRef.value?.focus?.()
    dateRef.value?.focus?.()
  })
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
  if (Array.isArray(left) || Array.isArray(right)) {
    return JSON.stringify(left || []) === JSON.stringify(right || [])
  }
  return String(left ?? '') === String(right ?? '')
}
</script>

<style scoped>
.cell-editor {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 28px;
}

.cell-saving {
  flex: none;
  color: #d93025;
  animation: rotating 1.4s linear infinite;
}

.cell-save-error {
  color: var(--el-color-danger);
  font-size: 12px;
  white-space: nowrap;
}

.cell-control {
  width: 100%;
}

@keyframes rotating {
  to {
    transform: rotate(360deg);
  }
}
</style>
