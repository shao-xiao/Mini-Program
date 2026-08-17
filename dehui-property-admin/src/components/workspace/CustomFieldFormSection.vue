<template>
  <section class="custom-field-form-section" aria-label="自定义字段">
    <el-form-item
      v-for="field in fields"
      :key="field.key"
      :label="field.label"
      :required="field.required"
      class="custom-field-form-item"
      :error="fieldErrors[field.key]"
    >
      <CustomFieldValueInput
        :field="field"
        :model-value="modelValue[field.key]"
        :disabled="!editable || saving"
        size="default"
        @update:model-value="value => updateValue(field, value)"
        @uploading-change="uploading => updateUploading(field, uploading)"
      />
      <p v-if="field.description" class="custom-field-description">{{ field.description }}</p>
    </el-form-item>
  </section>
</template>

<script setup>
import { reactive } from 'vue'
import { validateCustomFieldValue } from '../../utils/customFieldValues.js'
import CustomFieldValueInput from './CustomFieldValueInput.vue'

defineOptions({ name: 'CustomFieldFormSection' })

const props = defineProps({
  fields: { type: Array, default: () => [] },
  modelValue: { type: Object, default: () => ({}) },
  editable: { type: Boolean, default: true },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change'])
const fieldErrors = reactive({})
const uploadingFields = reactive(new Set())

function updateValue(field, value) {
  fieldErrors[field.key] = ''
  const next = { ...props.modelValue, [field.key]: value }
  emit('update:modelValue', next)
  emit('change', { field, value, values: next })
}

function updateUploading(field, uploading) {
  if (uploading) uploadingFields.add(field.key)
  else uploadingFields.delete(field.key)
}

function validate() {
  let valid = true
  if (uploadingFields.size) {
    valid = false
    uploadingFields.forEach(key => {
      fieldErrors[key] = '文件正在上传，请稍后保存'
    })
  }
  props.fields.forEach(field => {
    if (uploadingFields.has(field.key)) return
    const message = validateCustomFieldValue(field, props.modelValue[field.key])
    fieldErrors[field.key] = message
    if (message) valid = false
  })
  return { valid, errors: { ...fieldErrors } }
}

function clearValidation() {
  Object.keys(fieldErrors).forEach(key => { fieldErrors[key] = '' })
}

defineExpose({ validate, clearValidation, uploadingFields })
</script>

<style scoped>
.custom-field-form-section {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.custom-field-description {
  width: 100%;
  margin: 4px 0 0;
  color: var(--dhic-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .custom-field-form-section {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
