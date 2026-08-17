<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="520px"
    destroy-on-close
    @update:model-value="value => $emit('update:modelValue', value)"
    @open="resetDraft"
  >
    <div v-if="errorText" class="custom-field-drawer-error" role="alert">{{ errorText }}</div>
    <CustomFieldFormSection
      ref="formRef"
      v-model="draftValues"
      :fields="fields"
      :editable="editable"
      :saving="saving"
    />
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button v-if="editable" type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { buildCustomFieldForm } from '../../utils/customFieldValues.js'
import CustomFieldFormSection from './CustomFieldFormSection.vue'

defineOptions({ name: 'CustomFieldValueDrawer' })

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '编辑自定义字段' },
  row: { type: Object, default: null },
  fields: { type: Array, default: () => [] },
  editable: { type: Boolean, default: false },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'save'])
const formRef = ref(null)
const draftValues = ref({})
const errorText = ref('')

watch(() => props.row, resetDraft, { deep: false })

function resetDraft() {
  draftValues.value = buildCustomFieldForm(props.fields, props.row?.customFields || {})
  errorText.value = ''
  formRef.value?.clearValidation?.()
}

function submit() {
  if (!props.editable || props.saving) return
  const validation = formRef.value?.validate?.() || { valid: true }
  if (!validation.valid) return
  emit('save', {
    row: props.row,
    values: { ...draftValues.value },
    complete: completeSaving
  })
}

function completeSaving(result = { success: true }) {
  if (result?.success === false) {
    errorText.value = result.message || '保存失败，请稍后重试'
    return
  }
  emit('update:modelValue', false)
}
</script>

<style scoped>
.custom-field-drawer-error {
  margin-bottom: 12px;
  padding: 8px 10px;
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 4px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger-dark-2);
  font-size: 13px;
}
</style>
