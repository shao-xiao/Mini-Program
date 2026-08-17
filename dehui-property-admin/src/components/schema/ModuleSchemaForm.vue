<template>
  <el-form ref="formRef" :model="model" :label-width="labelWidth">
    <el-form-item
      v-for="field in fields"
      :key="field.fieldKey"
      :label="field.label"
      :required="field.required"
      :error="errors[field.fieldKey]"
    >
      <SchemaFieldInput
        :field="field"
        :module-key="moduleKey"
        :model-value="model[field.fieldKey]"
        :disabled="readonly || !field.editable"
        @update:model-value="update(field, $event)"
      />
      <div v-if="field.description" class="schema-help">{{ field.description }}</div>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { reactive, watch } from 'vue'
import SchemaFieldInput from './SchemaFieldInput.vue'
import { validateModuleValues, splitModuleValues } from '../../utils/moduleFieldSchema.js'

const props = defineProps({
  moduleKey: { type: String, required: true },
  fields: { type: Array, default: () => [] },
  values: { type: Object, default: () => ({}) },
  readonly: Boolean,
  labelWidth: { type: String, default: '120px' }
})
const emit = defineEmits(['update:values', 'validation'])
const model = reactive({})
const errors = reactive({})

watch(() => [props.fields, props.values], () => {
  for (const key of Object.keys(model)) delete model[key]
  for (const field of props.fields) {
    model[field.fieldKey] = props.values[field.apiField] ?? props.values[field.fieldKey]
      ?? props.values.customFields?.[field.fieldKey] ?? null
  }
}, { immediate: true, deep: true })

function update(field, value) {
  model[field.fieldKey] = value
  delete errors[field.fieldKey]
  emit('update:values', splitModuleValues(props.fields, model))
}

function validate() {
  const result = validateModuleValues(props.fields, model)
  for (const key of Object.keys(errors)) delete errors[key]
  Object.assign(errors, result.errors)
  emit('validation', result)
  return result.valid
}

defineExpose({ validate, getPayload: () => splitModuleValues(props.fields, model) })
</script>

<style scoped>
.schema-help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
</style>
